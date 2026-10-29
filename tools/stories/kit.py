"""
Tiny authoring kit for Kissa game.json files (v4 stories). Each tools/stories/<game>.py builds a dict with these
helpers and writes app/src/main/assets/games/<id>/game.json. Run tools/voicegen afterwards (voice_file fields are
filled by voicegen --apply-only from the hash cache, so re-running a story script never loses audio).

Dialogue + captions: Hindi (Devanagari). Player choices: Roman Hinglish ({m:..|f:..} for gendered verbs).
NPC voiced lines must not contain {player} (the name is only known at runtime and voices are pre-generated).
"""
import json, os, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def say(c, text, emo="neutral", move=None, **kw):
    assert "{player}" not in text, text
    d = {"say": c, "text": text, "emotion": emo}
    if move:
        d["move"] = move
    d.update(kw)
    return d


def nar(text):
    return {"narrate": text}


def me(text, time=None):
    d = {"player": text}
    if time:
        d["time"] = time
    return d


def setfx(**effects):
    return {"set": effects}


def enter(c, at=0.62, frm="right"):
    return {"enter": c, "at": at, "from": frm}


def exit_(c, to="right"):
    return {"exit": c, "to": to}


def emote(c, e):
    return {"emote": c, "emotion": e}


def cam(zoom=None, focus=None, shake=0.0):
    d = {}
    if zoom is not None:
        d["zoom"] = zoom
    if focus:
        d["focus"] = focus
    if shake:
        d["shake"] = shake
    return {"camera": d}


def fx(**k):
    return {"fx": k}


def crowd(r):
    return {"audience": r}


def wait(ms):
    return {"wait": ms}


def if_(cond, then, otherwise=None):
    d = {"if": cond, "then": then}
    if otherwise:
        d["else"] = otherwise
    return d


def obj(i, action="start"):
    return {"objective": i, "action": action}


def ch(text, style, score, effects=None, goto=None, reaction=None, kind=None, cond=None):
    d = {"text": text, "style": style, "score": score, "effects": effects or {}}
    if goto:
        d["goto"] = goto
    if reaction:
        d["reaction"] = reaction
    if kind:
        d["kind"] = kind
    if cond:
        d["if"] = cond
    return d


def intent(i, style, score, match, effects=None, reaction=None, goto=None):
    d = {"id": i, "style": style, "score": score, "match": match, "effects": effects or {}}
    if reaction:
        d["reaction"] = reaction
    if goto:
        d["goto"] = goto
    return d


def reply(char, prompt, presets, intents, fallback_line, fallback_emo="neutral", fallback_effects=None, placeholder=None):
    return {"open_reply": {
        "char": char, "prompt": prompt, "placeholder": placeholder or "Apne shabdon mein likho ya bolo…", "choices": presets,
        "intents": intents,
        "fallback": {"reaction": [say(char, fallback_line, fallback_emo)], "effects": fallback_effects or {}},
    }}


def scene(title=None, bg="college_corridor", time="day", music=None, cast=(), steps=(), choices=None, prompt=None,
          type_=None, audience=0, with_=None, header=None, checkpoint=None, next_=None, ai=None, weather=None, ending=None):
    d = {}
    if type_:
        d["type"] = type_
    if title:
        d["title"] = title
    if checkpoint:
        d["checkpoint"] = checkpoint
    if bg:
        d["background"] = bg
    d["time"] = time
    if weather:
        d["weather"] = weather
    if music:
        d["music"] = music
    if cast:
        d["cast"] = [{"id": c, "at": a} for c, a in cast]
    if audience:
        d["audience"] = audience
    if with_:
        d["with"] = with_
    if header:
        d["header"] = header
    if ai:
        d["ai"] = ai
    d["steps"] = list(steps)
    if prompt:
        d["prompt"] = prompt
    if choices:
        d["choice"] = choices
    if next_:
        d["next"] = next_
    if ending:
        d["ending"] = ending
    return d


def write(game):
    gid = game["id"]
    path = os.path.join(ROOT, "app", "src", "main", "assets", "games", gid, "game.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(game, f, ensure_ascii=False, indent=2)
        f.write("\n")
    n = 0

    def walk(steps):
        nonlocal n
        for s in steps or []:
            if "say" in s or "narrate" in s:
                n += 1
            walk(s.get("then")); walk(s.get("else"))
            if "open_reply" in s:
                o = s["open_reply"]
                for it in o.get("intents", []):
                    walk(it.get("reaction"))
                walk(o.get("fallback", {}).get("reaction"))
    for sc in game["scenes"].values():
        walk(sc.get("steps"))
        for c in sc.get("choice", []):
            walk(c.get("reaction"))
    print(f"wrote {path}: {len(game['scenes'])} scenes, {n} voiced lines")
