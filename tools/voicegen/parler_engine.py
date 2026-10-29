"""
AI4Bharat Indic Parler-TTS engine for voicegen (free, open weights, runs on CPU).

Load quirks (all required, see AGENTS.md):
  * the safetensors file is read with plain file reads into an fp32 model built from ParlerTTSConfig
    (mmap on the /workspace FUSE mount SIGBUSes),
  * text_encoder.encoder.embed_tokens.weight is tied to the T5 shared embedding,
  * GenerationConfig must be loaded from the repo or generation stops after ~0.1 s.
~35-60 s per line on 2 CPU cores, ~4 GB RAM. Never run it next to Gradle.

Voice control is the natural-language description: a named speaker (Hindi: Rohit, Divya, Aman, Rani) plus
pitch/pace words from the character's voice profile and an emotion phrase from the line's emotion.
"""
import glob, gc, json, os, struct, sys, time

MODEL_ID = os.environ.get("PARLER_MODEL", "RXD03/indic-parler-tts")
HF_CACHE = os.environ.get("HF_HOME_CACHE", "/workspace/work/cache/huggingface/hub")

EMOTION_PHRASE = {
    "neutral": "in a natural, conversational and slightly expressive tone",
    "smile": "in a warm, friendly and pleasant tone",
    "happy": "in a happy, cheerful and animated tone",
    "laugh": "in a very happy, laughing and playful tone",
    "angry": "in an angry, sharp and intense tone",
    "furious": "in a furious, loud and very intense tone",
    "sad": "in a sad, soft and slow tone",
    "crying": "in a very sad, trembling and emotional tone",
    "shocked": "in a shocked, surprised and urgent tone",
    "surprised": "in a surprised and animated tone",
    "shy": "in a shy, soft and hesitant tone",
    "blushing": "in a soft, shy and affectionate tone",
    "smug": "in a mocking, confident and teasing tone",
    "worried": "in a worried, anxious and slightly fast tone",
    "scared": "in a scared, fearful and urgent tone",
    "disgusted": "in a disgusted and annoyed tone",
    "bored": "in a bored, flat and slightly monotone tone",
    "thinking": "in a thoughtful, calm and slow tone",
    "sleepy": "in a sleepy, slow and soft tone",
    "determined": "in a determined, firm and confident tone",
    "narration": "in a calm, warm storytelling narration tone",
}


def describe(speaker, emotion, pitch=1.0, speed=1.0, extra=""):
    p = "a slightly high-pitched voice" if pitch >= 1.06 else "a slightly low-pitched voice" if pitch <= 0.94 else "a balanced pitch"
    if pitch <= 0.88:
        p = "a low-pitched, deep voice"
    s = "at a slightly fast pace" if speed >= 1.04 else "at a slightly slow pace" if speed <= 0.94 else "at a moderate pace"
    emo = EMOTION_PHRASE.get(emotion or "neutral", EMOTION_PHRASE["neutral"])
    ex = (" " + extra.strip()) if extra else ""
    return (f"{speaker} speaks {emo} with {p} {s}.{ex} The recording is of very high quality, very clear audio, "
            f"with the speaker's voice sounding close up and no background noise.")


class Parler:
    def __init__(self, threads=2):
        import torch
        from parler_tts import ParlerTTSForConditionalGeneration, ParlerTTSConfig
        from transformers import AutoTokenizer, GenerationConfig
        torch.set_num_threads(threads)
        self.torch = torch
        cfg = ParlerTTSConfig.from_pretrained(MODEL_ID)
        model = ParlerTTSForConditionalGeneration(cfg).eval()
        name = "models--" + MODEL_ID.replace("/", "--")
        f = glob.glob(os.path.join(HF_CACHE, name, "snapshots", "*", "model.safetensors"))[0]
        DT = {"F32": torch.float32, "F16": torch.float16, "BF16": torch.bfloat16, "I64": torch.int64}
        P = dict(model.state_dict(keep_vars=True))
        done = set()
        with open(f, "rb") as fh, torch.no_grad():
            n = struct.unpack("<Q", fh.read(8))[0]
            hdr = json.loads(fh.read(n)); base = 8 + n
            for k, v in hdr.items():
                if k == "__metadata__" or k not in P:
                    continue
                a, b = v["data_offsets"]; fh.seek(base + a); buf = bytearray(fh.read(b - a))
                P[k].copy_(torch.frombuffer(buf, dtype=DT[v["dtype"]]).reshape(v["shape"])); done.add(k); del buf
            sh = [k for k in hdr if k.startswith("text_encoder") and k.endswith("shared.weight")]
            key = "text_encoder.encoder.embed_tokens.weight"
            if key in P and key not in done and sh:
                P[key].copy_(P[sh[0]]); done.add(key)
        missing = [k for k in P if k not in done]
        if missing:
            print("parler: missing weights", missing[:8], file=sys.stderr)
        model.generation_config = GenerationConfig.from_pretrained(MODEL_ID)
        del P; gc.collect()
        self.model = model
        self.tok = AutoTokenizer.from_pretrained(MODEL_ID)
        self.dtok = AutoTokenizer.from_pretrained(model.config.text_encoder._name_or_path)
        self.sr = model.config.sampling_rate

    def synth(self, text, description, out_wav, seed=0):
        import soundfile as sf
        torch = self.torch
        torch.manual_seed(seed)
        d = self.dtok(description, return_tensors="pt"); p = self.tok(text, return_tensors="pt")
        with torch.no_grad():
            a = self.model.generate(input_ids=d.input_ids, attention_mask=d.attention_mask,
                                    prompt_input_ids=p.input_ids, prompt_attention_mask=p.attention_mask)
        audio = a.float().cpu().numpy().squeeze()
        sf.write(out_wav, audio, self.sr)
        return len(audio) / self.sr
