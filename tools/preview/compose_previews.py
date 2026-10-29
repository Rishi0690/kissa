#!/usr/bin/env python3
"""
compose_previews — builds the v3 preview frames: real stage/rig renders from PreviewFramesTest (raw/*.png) + real game
text from the bundled game JSON, with the app's UI chrome (caption bar, choice cards, typing bar, chat bubbles, status
sheet, ending screen, customiser) laid out to mirror the Compose code (same colours, order and copy). Compose itself is
not rendered headless, so these are faithful mock-ups of the layout, not device screenshots.
  python3 tools/preview/compose_previews.py <raw_dir> <out_dir> <fonts_dir>
"""
import json, os, re, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

RAW, OUT, FONTS = sys.argv[1], sys.argv[2], sys.argv[3]
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
GAMES = os.path.join(ROOT, "app/src/main/assets/games")
W, H = 720, 1280
ACC = (255, 122, 144)
HI = (247, 244, 255); LO = (201, 194, 218); INK = (14, 11, 22)
EMOJI = re.compile(r"[\U0001F000-\U0001FAFF\u2600-\u27BF\uFE0F]")


def font(size, bold=False, deva=False, serif=False):
    if serif:
        return ImageFont.truetype(os.path.join(FONTS, "NotoSerif-Bold.ttf"), size)
    name = ("NotoSansDevanagari-" if deva else "NotoSans-") + ("Bold" if bold else "Regular") + ".ttf"
    return ImageFont.truetype(os.path.join(FONTS, name), size, layout_engine=ImageFont.Layout.RAQM)


def is_deva(t):
    return any("\u0900" <= c <= "\u097F" for c in t)


def F(t, size, bold=False):
    return font(size, bold, deva=is_deva(t))


def wrap(d, text, fnt, width):
    words = text.split(); lines = []; cur = ""
    for w in words:
        t = (cur + " " + w).strip()
        if d.textlength(t, font=fnt) <= width:
            cur = t
        else:
            if cur: lines.append(cur)
            cur = w
    if cur: lines.append(cur)
    return lines


def rrect(d, box, r, fill, outline=None, width=1):
    d.rounded_rectangle(box, r, fill=fill, outline=outline, width=width)


def game(gid):
    return json.load(open(os.path.join(GAMES, gid, "game.json"), encoding="utf-8"))


def clean(t, player="Kavya", female=True):
    t = t.replace("{player}", player)
    t = re.sub(r"\{m:([^|}]*)\|f:([^}]*)\}", lambda m: m.group(2) if female else m.group(1), t)
    return EMOJI.sub("", t).strip()


def base(name):
    return Image.open(os.path.join(RAW, name + ".png")).convert("RGBA").resize((W, H))


def overlay(img):
    return Image.new("RGBA", img.size, (0, 0, 0, 0))


def hud(img, rank, objective):
    o = overlay(img); d = ImageDraw.Draw(o)
    d.ellipse((16, 56, 60, 100), fill=(0, 0, 0, 110)); d.line((30, 70, 46, 86), fill=HI, width=3); d.line((46, 70, 30, 86), fill=HI, width=3)
    f1 = font(14, True); f2 = font(15, True)
    wpill = 40 + d.textlength("PLAYER ", font=f1) + d.textlength(rank.upper(), font=f2) + 14
    rrect(d, (70, 60, 70 + wpill, 96), 18, (26, 20, 40, 210), outline=(255, 255, 255, 70))
    d.ellipse((80, 69, 98, 87), fill=ACC)
    d.text((104, 69), "PLAYER ", font=f1, fill=LO); d.text((104 + d.textlength("PLAYER ", font=f1), 68), rank.upper(), font=f2, fill=HI)
    if objective:
        fo = font(14)
        t = objective if d.textlength(objective, font=fo) < 210 else objective[:26] + "…"
        tw = d.textlength(t, font=fo) + 46
        rrect(d, (W - 70 - tw, 62, W - 70, 94), 16, (0, 0, 0, 140))
        d.polygon([(W - 60 - tw, 70), (W - 60 - tw, 86), (W - 50 - tw, 74)], fill=ACC)
        d.text((W - 44 - tw, 69), t, font=fo, fill=HI)
    d.ellipse((W - 60, 56, W - 16, 100), fill=(0, 0, 0, 110))
    for i, hgt in enumerate((10, 18, 14)):
        d.rectangle((W - 48 + i * 8, 88 - hgt, W - 43 + i * 8, 88), fill=HI)
    return Image.alpha_composite(img, o)


def caption(img, speaker, text, narration=False, player=False):
    o = overlay(img); d = ImageDraw.Draw(o)
    ft = F(text, 26); lines = wrap(d, text, ft, W - 80)
    hgt = 40 + (34 if speaker else 0) + len(lines) * 40
    top = H - 30 - hgt
    rrect(d, (16, top, W - 16, H - 30), 28, (14, 11, 22, 222), outline=(255, 255, 255, 30))
    y = top + 20
    if speaker:
        d.text((40, y), speaker, font=font(19, True), fill=(143, 227, 192) if player else ACC); y += 34
    for ln in lines:
        d.text((40, y), ln, font=ft, fill=LO if narration else HI); y += 40
    return Image.alpha_composite(img, o)


STYLE = {"confident": "confident", "timid": "timid", "rude": "rude", "funny": "funny", "kind": "kind", "cold": "cold"}


def choices(img, prompt, opts):
    o = Image.new("RGBA", img.size, (0, 0, 0, 140)); d = ImageDraw.Draw(o)
    blocks = []
    for (t, st) in opts:
        ft = F(t, 24, True); ls = wrap(d, t, ft, W - 150); blocks.append((ls, ft, st))
    total = sum(30 + len(b[0]) * 34 + (24 if b[2] else 0) + 12 for b in blocks)
    y = (H - total) // 2
    if prompt:
        fp = F(prompt, 22)
        for ln in wrap(d, prompt, fp, W - 80):
            d.text(((W - d.textlength(ln, font=fp)) / 2, y - 50), ln, font=fp, fill=HI); y += 0
    for i, (ls, ft, st) in enumerate(blocks):
        hgt = 26 + len(ls) * 34 + (24 if st else 0)
        rrect(d, (30, y, W - 30, y + hgt), 28, (255, 255, 255, 52), outline=(255, 255, 255, 95), width=2)
        d.ellipse((48, y + hgt / 2 - 20, 88, y + hgt / 2 + 20), fill=ACC)
        d.text((61 if i < 9 else 56, y + hgt / 2 - 16), str(i + 1), font=font(22, True), fill=INK)
        yy = y + 13
        for ln in ls:
            d.text((106, yy), ln, font=ft, fill=HI); yy += 34
        if st:
            d.text((106, yy), STYLE.get(st, st), font=font(15), fill=LO)
        y += hgt + 14
    return Image.alpha_composite(img, o)


def typing_bar(img, prompt, presets, placeholder):
    o = overlay(img); d = ImageDraw.Draw(o)
    top = H - 200
    for k in range(200):
        d.line((0, top + k, W, top + k), fill=(14, 11, 22, int(230 * k / 200)))
    d.text((26, top + 26), prompt, font=F(prompt, 17), fill=LO)
    x = 22
    for p in presets:
        fp = F(p, 18); tw = d.textlength(p, font=fp) + 30
        rrect(d, (x, top + 58, x + tw, top + 98), 20, (255, 255, 255, 50), outline=ACC + (160,), width=2)
        d.text((x + 15, top + 66), p, font=fp, fill=HI); x += tw + 10
    rrect(d, (20, top + 116, W - 140, top + 176), 30, (34, 29, 48, 255), outline=(255, 255, 255, 70), width=2)
    d.text((44, top + 133), placeholder, font=F(placeholder, 19), fill=LO)
    d.ellipse((W - 128, top + 118, W - 72, top + 174), fill=(42, 36, 56, 255))
    d.rounded_rectangle((W - 106, top + 130, W - 94, top + 152), 6, fill=HI); d.line((W - 100, top + 156, W - 100, top + 164), fill=HI, width=3)
    d.ellipse((W - 64, top + 118, W - 8, top + 174), fill=ACC)
    d.polygon([(W - 46, top + 132), (W - 22, top + 146), (W - 46, top + 160)], fill=INK)
    return Image.alpha_composite(img, o)


def toasts(img, items):
    o = overlay(img); d = ImageDraw.Draw(o); y = 120
    for t, pos in items:
        ft = font(18, True); tw = d.textlength(t, font=ft) + 32
        col = (31, 107, 74, 235) if pos is True else (122, 35, 51, 235) if pos is False else (58, 52, 80, 235)
        if pos == "rank":
            col = (255, 196, 100, 255)
        rrect(d, ((W - tw) / 2, y, (W + tw) / 2, y + 36), 18, col)
        d.text(((W - tw) / 2 + 16, y + 6), t, font=ft, fill=INK if pos == "rank" else HI); y += 44
    return Image.alpha_composite(img, o)


def chat_screen():
    g = game("aukaat"); sc = g["scenes"]["s5_chat"]
    img = Image.new("RGBA", (W, H), (16, 13, 24, 255)); d = ImageDraw.Draw(img)
    for y in range(H):
        c = int(20 - 8 * y / H); d.line((0, y, W, y), fill=(c, c - 3, c + 9, 255))
    d.rectangle((0, 0, W, 130), fill=(27, 23, 38, 255))
    d.line((24, 88, 40, 72), fill=HI, width=3); d.line((24, 88, 40, 104), fill=HI, width=3); d.line((24, 88, 52, 88), fill=HI, width=3)
    av = Image.open(os.path.join(RAW, "chat_avatar.png")).convert("RGBA").resize((70, 70))
    m = Image.new("L", (70, 70), 0); ImageDraw.Draw(m).ellipse((0, 0, 70, 70), fill=255); img.paste(av, (66, 54), m)
    d.text((150, 56), "Tanvi Oberoi", font=font(24, True), fill=HI); d.text((150, 90), "typing…", font=font(17), fill=(143, 227, 192))
    rrect(d, (480, 70, 690, 104), 17, (26, 20, 40, 230), outline=(255, 255, 255, 70))
    d.text((496, 77), "PLAYER BOLD", font=font(15, True), fill=HI)
    y = 160
    nar = sc["steps"][0]["narrate"]
    fn = F(nar, 19)
    d.text(((W - d.textlength(nar, font=fn)) / 2, y), nar, font=fn, fill=LO); y += 50
    for s in sc["steps"][1:4]:
        t = s["text"]; ft = F(t, 25); ls = wrap(d, t, ft, 440)
        hgt = 26 + len(ls) * 38 + 22
        bw = max(d.textlength(l, font=ft) for l in ls) + 40
        d.rounded_rectangle((20, y, 20 + bw, y + hgt), 26, fill=(42, 37, 54, 255))
        yy = y + 12
        for l in ls:
            d.text((40, yy), l, font=ft, fill=HI); yy += 38
        d.text((20 + bw - 80, y + hgt - 28), s["time"], font=font(14), fill=LO)
        y += hgt + 12
    # player's reply + seen
    t = "Kya hua? Batao, main sun rahi hoon."
    ft = font(24); bw = d.textlength(t, font=ft) + 40
    d.rounded_rectangle((W - 20 - bw, y, W - 20, y + 74), 26, fill=(107, 53, 80, 255))
    d.text((W - bw, y + 12), t, font=ft, fill=HI); d.text((W - 110, y + 46), "1:05 AM  ✓✓", font=font(14), fill=(127, 211, 255))
    d.text((W - 66, y + 80), "Seen", font=font(14), fill=LO); y += 120
    d.rounded_rectangle((20, y, 110, y + 50), 24, fill=(42, 37, 54, 255))
    for i in range(3):
        d.ellipse((40 + i * 20, y + 20, 52 + i * 20, y + 32), fill=LO)
    # bottom: preset reply chips + input + mic (open reply)
    o = sc["steps"][4]["open_reply"]
    img = typing_bar(img, o["prompt"], o["choices"], o["placeholder"])
    return img


def status_screen():
    g = game("pehla-hafta")
    img = base("office")
    dim = Image.new("RGBA", img.size, (0, 0, 0, 170)); img = Image.alpha_composite(img, dim)
    o = overlay(img); d = ImageDraw.Draw(o)
    top = 470
    d.rounded_rectangle((0, top, W, H + 40), 40, fill=(23, 19, 31, 255))
    d.text((34, top + 30), "Status", font=font(34, serif=True), fill=HI)
    rrect(d, (34, top + 92, 250, top + 128), 18, (26, 20, 40, 255), outline=(255, 255, 255, 70))
    d.ellipse((44, top + 101, 62, top + 119), fill=ACC); d.text((70, top + 100), "PLAYER RISING", font=font(15, True), fill=HI)
    d.text((266, top + 100), "Score 24 / 42 → Star Performer", font=font(17), fill=LO)
    d.rounded_rectangle((34, top + 146, W - 34, top + 156), 5, fill=(255, 255, 255, 50)); d.rounded_rectangle((34, top + 146, 34 + (W - 68) * 0.18, top + 156), 5, fill=ACC)
    y = top + 186
    vals = {"impression": 3, "team": 5, "himmat": 4}
    for k, sd in g["stats"].items():
        v = vals[k]; frac = (v - sd["min"]) / (sd["max"] - sd["min"])
        d.text((34, y), sd["label"], font=font(20), fill=HI)
        d.rounded_rectangle((250, y + 8, W - 100, y + 22), 7, fill=(255, 255, 255, 34))
        d.rounded_rectangle((250, y + 8, 250 + (W - 350) * frac, y + 22), 7, fill=(107, 214, 154))
        d.text((W - 80, y), str(v), font=font(20, True), fill=HI); y += 46
    y += 14; d.text((34, y), "Objectives", font=font(21, True), fill=HI); y += 40
    for ob, st, col in ((g["objectives"][0], "active", ACC), (g["objectives"][1], "available", LO)):
        d.polygon([(36, y + 4), (36, y + 24), (50, y + 9)], fill=col)
        d.text((62, y - 2), ob["text"], font=font(20), fill=HI)
        d.text((62, y + 26), f'{ob.get("place", "")} · {ob.get("deadline", "")} · {st}', font=font(16), fill=LO); y += 66
    d.text(((W - 120) / 2, H - 80), "Band karo", font=font(20, True), fill=ACC)
    return Image.alpha_composite(img, o)


def ending_screen():
    g = game("aukaat")
    img = base("ending_bg")
    o = Image.new("RGBA", img.size, (14, 11, 22, 236)); d = ImageDraw.Draw(o)
    def center(t, y, fnt, col):
        d.text(((W - d.textlength(t, font=fnt)) / 2, y), t, font=fnt, fill=col)
    center("E N D I N G", 90, font(18, True), ACC)
    center("Saath Khade", 120, font(52, serif=True), HI)
    center("GOOD", 196, font(15), LO)
    rrect(d, (250, 236, 470, 272), 18, (26, 20, 40, 255), outline=ACC, width=2); d.text((290, 244), "PLAYER BOLD", font=font(16, True), fill=HI)
    y = 300
    vals = {"power": 11, "trust": 9, "dosti": 4}
    for k, sd in g["stats"].items():
        v = vals[k]; frac = (v - sd["min"]) / (sd["max"] - sd["min"])
        d.text((44, y), sd["label"], font=font(20), fill=HI)
        d.rounded_rectangle((240, y + 8, W - 100, y + 22), 7, fill=(255, 255, 255, 34)); d.rounded_rectangle((240, y + 8, 240 + (W - 340) * frac, y + 22), 7, fill=ACC)
        d.text((W - 80, y), str(v), font=font(20, True), fill=HI); y += 44
    y += 20; center(f"Baaki endings (1/{len(g['endings'])} mili)", y, font(21, True), HI); y += 46
    for e in g["endings"]:
        got = e["id"] == "good"
        rrect(d, (40, y, W - 40, y + 70), 18, (255, 255, 255, 20))
        d.ellipse((58, y + 24, 80, y + 46), fill=(107, 214, 154) if got else (120, 112, 140))
        d.text((96, y + 8), e["title"] if got else "???", font=font(20, True), fill=HI)
        d.text((96, y + 38), e["teaser"], font=font(16), fill=LO); y += 82
    y += 16
    rrect(d, (40, y, W - 40, y + 60), 30, ACC); center("Checkpoint se replay karo", y + 15, font(21, True), INK); y += 76
    rrect(d, (40, y, W - 40, y + 60), 30, None, outline=(255, 255, 255, 120), width=2); center("Shuru se dobara khelo", y + 15, font(21), HI); y += 80
    center("Game page par wapas", y, font(18), LO)
    return Image.alpha_composite(img, o)


def customiser_screen():
    img = Image.new("RGBA", (W, H), (21, 18, 31, 255)); d = ImageDraw.Draw(img)
    rig = Image.open(os.path.join(RAW, "customiser_rig.png")).convert("RGBA").resize((W, 560))
    img.paste(rig, (0, 0))
    d.text(((W - d.textlength("Tumhara character", font=font(22, True))) / 2, 60), "Tumhara character", font=font(22, True), fill=HI)
    y = 580
    d.text((30, y), "Role: Naya student jise sab kam samajhte hain", font=font(17), fill=LO); y += 36
    rrect(d, (30, y, W - 30, y + 64), 20, None, outline=ACC, width=2); d.text((50, y - 10), " Tumhara naam ", font=font(14), fill=LO)
    d.text((50, y + 16), "Kavya", font=font(24), fill=HI); y += 84
    def row(label, chips, sel):
        nonlocal y
        d.text((30, y), label, font=font(18, True), fill=HI); y += 30; x = 30
        for c in chips:
            fc = font(16); tw = d.textlength(c, font=fc) + 28
            on = c == sel
            rrect(d, (x, y, x + tw, y + 38), 19, ACC[:3] + (60,) if on else (255, 255, 255, 16), outline=ACC if on else (255, 255, 255, 40), width=2)
            d.text((x + 14, y + 8), c, font=fc, fill=HI if on else LO); x += tw + 10
            if x > W - 60: break
        y += 54
    def swatches(label, cols, sel):
        nonlocal y
        d.text((30, y), label, font=font(18, True), fill=HI); y += 30
        for i, c in enumerate(cols):
            d.ellipse((30 + i * 52, y, 70 + i * 52, y + 40), fill=c, outline=ACC if i == sel else (255, 255, 255, 90), width=4 if i == sel else 1)
        y += 56
    row("Body", ["Ladka", "Ladki"], "Ladki")
    swatches("Skin", [(255, 227, 204), (243, 201, 164), (224, 174, 132), (188, 135, 96), (142, 94, 66)], 1)
    row("Hair", ["long straight", "long wavy", "ponytail", "bob", "bun", "braid"], "ponytail")
    swatches("Hair colour", [(74, 69, 82), (107, 74, 58), (154, 106, 72), (178, 89, 58), (138, 51, 70), (184, 184, 192)], 2)
    row("Outfit", ["college blazer", "hoodie", "kurta", "denim jacket"], "denim jacket")
    rrect(d, (30, H - 100, W - 30, H - 40), 30, ACC)
    d.text(((W - d.textlength("Game shuru karo", font=font(22, True))) / 2, H - 86), "Game shuru karo", font=font(22, True), fill=INK)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    a = game("aukaat")
    s1 = a["scenes"]["s1"]
    out = {}
    img = hud(base("cinematic"), "Fresher", "Pehla din survive karo")
    out["1_cinematic_caption"] = caption(img, "Tanvi", "अंधे हो क्या? मेरी पूरी ड्रेस खराब कर दी!")
    orp = next(st["open_reply"] for st in s1["steps"] if "open_reply" in st)
    out["2_cinematic_open_reply"] = typing_bar(hud(base("cinematic"), "Fresher", "Pehla din survive karo"), orp["prompt"], orp["choices"], orp["placeholder"])
    out["3_cinematic_choices"] = choices(hud(base("cinematic"), "Fresher", "Pehla din survive karo"), s1["prompt"], [(clean(c["text"]), c["style"]) for c in s1["choice"]])
    out["4_tone_reaction_toasts"] = toasts(caption(hud(base("cinematic"), "Bold", "Pehla din survive karo"), "Tanvi", "…ठीक है, काव्या। ये नाम याद रहेगा।"), [("RANK UP  >>  BOLD", "rank"), ("Rank +12", True), ("Power +3", True), ("Tanvi Trust +1", True)])
    s7 = a["scenes"]["s7_canteen"]
    out["5_pov"] = caption(hud(base("pov"), "Bold", "Sahil ki dhamki ka sach…"), "Sahil", s7["steps"][2]["text"])
    out["6_chat"] = chat_screen()
    out["7_status"] = status_screen()
    out["8_ending"] = ending_screen()
    out["9_customiser"] = customiser_screen()
    b = game("baarish-wali-raat")
    out["10_cinematic_rain"] = caption(hud(base("cinematic_rain"), "Ajnabi", "Naina ko surakshit ghar…"), None, b["scenes"]["r2_walk"]["steps"][2]["narrate"], narration=True)
    for k, im in out.items():
        im.convert("RGB").save(os.path.join(OUT, k + ".png"))
    # contact sheet
    names = sorted(out, key=lambda s: int(s.split("_")[0]))
    th = [Image.open(os.path.join(OUT, n + ".png")).resize((288, 512)) for n in names]
    cols = 5; rows = (len(th) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * 296 + 8, rows * 548 + 8), (10, 8, 16))
    dd = ImageDraw.Draw(sheet)
    for i, (n, t) in enumerate(zip(names, th)):
        x = 8 + (i % cols) * 296; y = 8 + (i // cols) * 548
        sheet.paste(t, (x, y)); dd.text((x + 4, y + 516), n.split("_", 1)[1].replace("_", " "), font=font(18, True), fill=HI)
    sheet.save(os.path.join(OUT, "sheet.jpg"), quality=86)
    print("wrote", len(out), "frames + sheet.jpg")


if __name__ == "__main__":
    main()
