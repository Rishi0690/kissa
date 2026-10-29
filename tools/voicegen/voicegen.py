#!/usr/bin/env python3
"""
voicegen — pre-generates every voiced line of a Kissa game once (spec §3) and writes it into the game pack.

  python3 tools/voicegen/voicegen.py app/src/main/assets/games/aukaat/game.json [more game.json ...]
         [--proxy http://127.0.0.1:3128] [--cache DIR] [--dry-run] [--bitrate 32k]

For each `say` / `narrate` step (including nested if/then/else, choice reactions, intent reactions and
fallbacks) it:
  1. resolves the character's logical voice slot -> provider voice through voice_map.json (games never
     store provider voice ids),
  2. applies the character's pitch/speed profile plus per-emotion prosody,
  3. looks the line up in a content-addressed cache keyed by hash(text + provider + voice + prosody),
     synthesising only on a miss (so a line is re-voiced only when its text or the voice profile changes),
  4. compresses to Ogg Opus (mono, 24 kHz, low bitrate) as audio/<speaker>_<hash>.ogg in the game folder,
  5. fills the step's `voice_file`, and records a 25 fps amplitude envelope in voice_env.json so the
     app can lip-sync without decoding audio.
Lines containing {player} are skipped on purpose: the player's name is only known at runtime, so those
lines use the app's emergency phone-TTS fallback and are flagged by the validator.

Providers: "edge" (Microsoft Edge read-aloud neural voices; free, no key) is active. "sarvam" (Bulbul
v4-flash / v3 via POST https://api.sarvam.ai/text-to-speech, header api-subscription-key, base64 WAV in
`audios[0]`) is implemented as a shim but unused while the product is free-only.
"""
import argparse, asyncio, time, base64, hashlib, json, os, re, shutil, subprocess, sys, tempfile, wave, struct

HERE = os.path.dirname(os.path.abspath(__file__))
FPS = 25


def load_map():
    with open(os.path.join(HERE, "voice_map.json"), encoding="utf-8") as f:
        return json.load(f)


# ---------------------------------------------------------------- providers
async def synth_edge(text, voice, pitch_hz, rate_pct, vol_pct, proxy, out_mp3):
    import edge_tts
    sign = lambda v, unit: ("+" if v >= 0 else "") + f"{int(round(v))}{unit}"
    for attempt in range(4):
        try:
            c = edge_tts.Communicate(text, voice, rate=sign(rate_pct, "%"), volume=sign(vol_pct, "%"), pitch=sign(pitch_hz, "Hz"), proxy=proxy)
            await c.save(out_mp3)
            if os.path.getsize(out_mp3) > 1000:
                return
        except Exception as e:  # transient websocket errors
            err = e
            await asyncio.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"edge synthesis failed for {voice}: {text[:40]}")


def synth_sarvam(text, cfg, pace, out_wav, api_key=None, lang="hi-IN"):
    """Bulbul shim (unused while free-only). v3/v4-flash: pace 0.5-2.0, no pitch/loudness; returns base64 WAV."""
    import urllib.request
    api_key = api_key or os.environ.get("SARVAM_API_KEY")
    if not api_key:
        raise RuntimeError("SARVAM_API_KEY not set")
    for model in [cfg.get("model"), cfg.get("fallback_model")]:
        if not model:
            continue
        speaker = cfg.get("speaker_v4") if model == "bulbul:v4-flash" else cfg.get("speaker_v3")
        body = {"text": text, "target_language_code": lang, "language_code": lang, "model": model,
                "pace": max(0.5, min(2.0, pace)), "speech_sample_rate": 24000}
        if speaker:
            body["speaker"] = speaker
        req = urllib.request.Request("https://api.sarvam.ai/text-to-speech", data=json.dumps(body).encode(),
                                     headers={"api-subscription-key": api_key, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                audio = base64.b64decode(json.load(r)["audios"][0])
            open(out_wav, "wb").write(audio)
            return
        except Exception as e:
            print(f"  sarvam {model} failed: {e}", file=sys.stderr)
    raise RuntimeError("sarvam synthesis failed")


# ---------------------------------------------------------------- audio utils
def to_opus(src, dst, bitrate):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src, "-ac", "1", "-ar", "24000", "-c:a", "libopus", "-b:a", bitrate,
                    "-application", "voip", dst], check=True)


def envelope(path):
    pcm = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "s16le", "-ac", "1", "-ar", "8000", "-"], capture_output=True, check=True).stdout
    n = len(pcm) // 2
    samples = struct.unpack(f"<{n}h", pcm[: n * 2])
    win = 8000 // FPS
    vals = []
    for i in range(0, n, win):
        chunk = samples[i:i + win]
        if not chunk:
            break
        rms = (sum(s * s for s in chunk) / len(chunk)) ** 0.5
        vals.append(rms)
    peak = max(vals) if vals else 1
    out = bytes(min(255, int(255 * min(1.0, (v / peak) ** 0.7))) if peak > 0 else 0 for v in vals)
    return base64.b64encode(out).decode(), n / 8000.0


def duration(path):
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path], capture_output=True, text=True)
    try:
        return float(r.stdout.strip())
    except ValueError:
        return 0.0


# ---------------------------------------------------------------- game walking
def iter_lines(game):
    """Yields (step_dict, speaker_id_or_None, owner_char_for_fallback) for every voiced step in the game."""
    def walk(steps, default_char=None):
        for s in steps or []:
            if not isinstance(s, dict):
                continue
            if "say" in s:
                yield s, s["say"]
            elif "narrate" in s:
                yield s, None
            if "then" in s or "else" in s:
                yield from walk(s.get("then"), default_char)
                yield from walk(s.get("else"), default_char)
            if "open_reply" in s:
                o = s["open_reply"]
                for it in o.get("intents", []):
                    yield from walk(it.get("reaction"), o.get("char"))
                fb = o.get("fallback") or {}
                yield from walk(fb.get("reaction"), o.get("char"))
                if "say" in fb:  # shorthand fallback line
                    yield fb, fb.get("char") or o.get("char")
    for sc in game["scenes"].values():
        yield from walk(sc.get("steps"))
        ch = sc.get("choice") or sc.get("choices") or []
        if isinstance(ch, dict):
            ch = ch.get("options", [])
        for c in ch:
            yield from walk(c.get("reaction"))


def line_text(step):
    # shorthand fallback stores its line under "say"
    if "text" in step:
        return step["text"]
    if "narrate" in step:
        return step["narrate"]
    return step.get("say", "")


def speakable(text):
    """Strip stage cues that should be shown but not spoken (e.g. 'धीरे से:' prefix), emojis, and markup."""
    t = re.sub(r"^\s*धीरे से:\s*", "", text)
    t = re.sub(r"[\U0001F000-\U0001FAFF\u2600-\u27BF]", "", t)
    return t.strip()


def line_jobs(game, vm, provider, bitrate):
    """Yields (step, speaker, spoken, slot, emotion, key, extra) for every voiced line of a game."""
    chars = {c["id"]: c for c in game.get("characters", [])}
    narrator_slot = game.get("narrator_voice") or "narrator_hindi"
    for step, speaker in iter_lines(game):
        text = line_text(step)
        if "{player}" in text or "{name}" in text:
            yield step, speaker, None, None, None, None, None
            continue
        spoken = speakable(text)
        if not spoken:
            continue
        if speaker is None:
            slot, vp = narrator_slot, {"pitch": 1.0, "speed": 1.0}
        else:
            ch = chars.get(speaker)
            if ch is None:
                print(f"  ! unknown speaker {speaker}", file=sys.stderr); continue
            vp = ch.get("voice", {}); slot = vp.get("slot")
        scfg = vm["slots"].get(slot)
        if scfg is None or provider not in scfg:
            print(f"  ! slot {slot} has no {provider} voice in voice_map", file=sys.stderr); continue
        pcfg = scfg[provider]
        emo = (step.get("emotion") or "neutral") if speaker else "narration"
        if provider == "parler":
            from parler_engine import describe
            pitch = float(vp.get("pitch", 1.0)) * float(pcfg.get("pitch", 1.0))
            speed = float(vp.get("speed", 1.0)) * float(pcfg.get("speed", 1.0))
            desc = describe(pcfg["speaker"], emo, pitch, speed, pcfg.get("extra", ""))
            key_src = json.dumps(["parler", pcfg["speaker"], desc, spoken, bitrate], ensure_ascii=False, sort_keys=True)
            extra = {"desc": desc}
        else:
            ep, er, ev = vm["emotion_prosody"].get(emo, [0, 0, 0])
            base_hz = 200 if "female" in slot else 120
            pitch_hz = pcfg.get("pitch_hz", 0) + (float(vp.get("pitch", 1.0)) - 1.0) * base_hz + ep
            rate_pct = pcfg.get("rate_pct", 0) + (float(vp.get("speed", 1.0)) - 1.0) * 100 + er
            key_src = json.dumps([provider, pcfg, spoken, round(pitch_hz, 1), round(rate_pct, 1), ev, bitrate], ensure_ascii=False, sort_keys=True)
            extra = {"pitch_hz": pitch_hz, "rate_pct": rate_pct, "vol_pct": ev, "pcfg": pcfg}
        h = hashlib.sha1(key_src.encode()).hexdigest()[:12]
        yield step, speaker, spoken, slot, emo, h, extra


def plausible(secs, text):
    n = len(text)
    return 0.35 + n * 0.035 <= secs <= 2.5 + n * 0.16


def trim_to_opus(src_wav, dst, bitrate):
    """Trim leading/trailing silence, normalise loudness a little, encode Ogg/Opus mono 24 kHz."""
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src_wav, "-af",
                    "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,areverse,"
                    "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.12,areverse,"
                    "loudnorm=I=-18:TP=-2:LRA=11,apad=pad_dur=0.08",
                    "-ac", "1", "-ar", "24000", "-c:a", "libopus", "-b:a", bitrate, "-application", "audio", dst], check=True)


def synth_all(games, vm, provider, cache, bitrate, limit=None):
    jobs = []
    seen = set()
    for gpath in games:
        game = json.load(open(gpath, encoding="utf-8"))
        for step, speaker, spoken, slot, emo, h, extra in line_jobs(game, vm, provider, bitrate):
            if h is None or h in seen or os.path.exists(os.path.join(cache, h + ".ogg")):
                continue
            seen.add(h)
            jobs.append((game["id"], speaker, spoken, slot, emo, h, extra))
    print(f"synth: {len(jobs)} lines to generate with {provider}", flush=True)
    if limit:
        jobs = jobs[:limit]
    if not jobs:
        return 0
    eng = None
    if provider == "parler":
        from parler_engine import Parler
        t0 = time.time(); eng = Parler(); print(f"parler loaded in {time.time() - t0:.0f}s", flush=True)
    done = 0
    for gid, speaker, spoken, slot, emo, h, extra in jobs:
        t0 = time.time()
        with tempfile.TemporaryDirectory() as td:
            if provider == "parler":
                raw = os.path.join(td, "raw.wav")
                secs = 0
                for attempt in range(3):
                    secs = eng.synth(spoken, extra["desc"], raw, seed=int(h[:6], 16) + attempt * 7919)
                    if plausible(secs, spoken):
                        break
                    print(f"  ~ retry {h} ({secs:.1f}s for {len(spoken)} chars)", flush=True)
                trim_to_opus(raw, os.path.join(cache, h + ".ogg"), bitrate)
            elif provider == "edge":
                raw = os.path.join(td, "raw.mp3")
                asyncio.run(synth_edge(spoken, extra["pcfg"]["voice"], extra["pitch_hz"], extra["rate_pct"], extra["vol_pct"], None, raw))
                to_opus(raw, os.path.join(cache, h + ".ogg"), bitrate)
            else:
                raw = os.path.join(td, "raw.wav")
                synth_sarvam(spoken, extra["pcfg"], 1.0 + extra["rate_pct"] / 100.0, raw)
                to_opus(raw, os.path.join(cache, h + ".ogg"), bitrate)
        done += 1
        print(f"  + [{done}/{len(jobs)}] {gid}/{speaker or 'narrator'} {h} {emo} {time.time() - t0:.0f}s  {spoken[:40]}", flush=True)
    return done


def apply_all(games, vm, provider, cache, bitrate):
    total_lines = 0; total_bytes = 0; total_secs = 0.0; missing = 0; dynamic = 0; report = []
    for gpath in games:
        gdir = os.path.dirname(os.path.abspath(gpath))
        game = json.load(open(gpath, encoding="utf-8"))
        os.makedirs(os.path.join(gdir, "audio"), exist_ok=True)
        envs = {}; used = set()
        for step, speaker, spoken, slot, emo, h, extra in line_jobs(game, vm, provider, bitrate):
            if h is None:
                step.pop("voice_file", None); dynamic += 1; continue
            cached = os.path.join(cache, h + ".ogg")
            total_lines += 1
            if not os.path.exists(cached):
                step.pop("voice_file", None); missing += 1; continue
            fname = f"audio/{speaker or 'narrator'}_{h}.ogg"
            dst = os.path.join(gdir, fname)
            if not os.path.exists(dst):
                shutil.copyfile(cached, dst)
            step["voice_file"] = fname
            if fname not in used:
                used.add(fname)
                env, secs = envelope(dst)
                envs[fname] = {"fps": FPS, "secs": round(secs, 3), "env": env}
                total_bytes += os.path.getsize(dst); total_secs += secs
        for f in os.listdir(os.path.join(gdir, "audio")):
            if f"audio/{f}" not in used:
                os.remove(os.path.join(gdir, "audio", f))
        json.dump(envs, open(os.path.join(gdir, "voice_env.json"), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
        with open(gpath, "w", encoding="utf-8") as f:
            json.dump(game, f, ensure_ascii=False, indent=2); f.write("\n")
        report.append(f"{game['id']}: {len(used)} files")
    print("\n".join(report))
    print(f"TOTAL lines={total_lines} missing_audio={missing} player_name_lines={dynamic} audio_bytes={total_bytes} audio_secs={total_secs:.1f} provider={provider}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("games", nargs="+")
    ap.add_argument("--cache", default=os.path.join(HERE, ".cache"))
    ap.add_argument("--bitrate", default="40k")
    ap.add_argument("--provider", default=None)
    ap.add_argument("--synth-only", action="store_true", help="generate missing audio into the cache, do not touch games")
    ap.add_argument("--apply-only", action="store_true", help="no synthesis: link cached audio into the game packs")
    ap.add_argument("--limit", type=int, default=None)
    a = ap.parse_args()
    vm = load_map()
    provider = a.provider or vm["active_provider"]
    os.makedirs(a.cache, exist_ok=True)
    sys.path.insert(0, HERE)
    if not a.apply_only:
        synth_all(a.games, vm, provider, a.cache, a.bitrate, a.limit)
    if not a.synth_only:
        apply_all(a.games, vm, provider, a.cache, a.bitrate)


if __name__ == "__main__":
    main()
