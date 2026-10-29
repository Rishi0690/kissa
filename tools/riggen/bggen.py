#!/usr/bin/env python3
"""
bggen — turns each painted background into the 3-layer catalog format (spec §5): back / mid / front WebP.
  back  = full painting (far plane; the renderer blurs it slightly for depth of field)
  mid   = the floor/ground plane cut from the same painting with a feathered mask (moves faster than back -> parallax)
  front = an original foreground silhouette layer (plants, railing, desk edge, lamp bokeh) with alpha, drawn over actors
Day / dusk / night / rain are runtime filters, not new artwork. Target total per background: 100-200 KB.
"""
import os, random, math, json
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # app/
ART = os.path.join(os.path.dirname(ROOT), "art")
OUT = os.path.join(ROOT, "app/src/main/assets/bg")
PNG = os.path.join(ART, "v3/png/bg")
W, H = 1280, 714

SRC = {
    "college_corridor": "bg_corridor.png", "classroom": "bg_classroom.png", "library": "bg_library.png", "cafe": "bg_cafe.png",
    "street": "bg_street.png", "park": "bg_park.png", "forest": "bg_forest.png",
    "bedroom": "v3/raw/bg_bedroom_raw.png", "office": "v3/raw/bg_office_raw.png", "rooftop": "v3/raw/bg_rooftop_raw.png",
}
FRONT = {"college_corridor": "plants", "classroom": "desk", "library": "desk", "cafe": "lamps", "street": "rail", "park": "plants",
         "forest": "plants", "bedroom": "desk", "office": "plants", "rooftop": "rail"}
FLOOR = {"college_corridor": 0.62, "classroom": 0.66, "library": 0.66, "cafe": 0.66, "street": 0.68, "park": 0.64, "forest": 0.66,
         "bedroom": 0.68, "office": 0.64, "rooftop": 0.66}


def leaf(d, cx, cy, ln, ang, col):
    a = math.radians(ang)
    tip = (cx + math.cos(a) * ln, cy + math.sin(a) * ln)
    nx, ny = -math.sin(a) * ln * 0.18, math.cos(a) * ln * 0.18
    mid = ((cx + tip[0]) / 2, (cy + tip[1]) / 2)
    d.polygon([(cx, cy), (mid[0] + nx, mid[1] + ny), tip, (mid[0] - nx, mid[1] - ny)], fill=col)


def front_layer(kind, seed):
    rnd = random.Random(seed)
    S = 2
    img = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if kind == "plants":
        for side in (0, 1):
            bx = (rnd.randint(-40, 80) if side == 0 else W - rnd.randint(-40, 80)) * S
            by = (H + 40) * S
            for k in range(26):
                ang = rnd.uniform(-160, -20) if side == 0 else rnd.uniform(-160, -20)
                leaf(d, bx + rnd.randint(-60, 60) * S, by, rnd.randint(160, 330) * S, ang, (18, 34, 24, 255))
    elif kind == "desk":
        d.polygon([(0, (H - 70) * S), (W * S, (H - 40) * S), (W * S, H * S), (0, H * S)], fill=(40, 26, 20, 255))
        d.rectangle([0, (H - 74) * S, W * S, (H - 66) * S], fill=(70, 46, 34, 255))
        d.rectangle([(W - 260) * S, (H - 150) * S, (W - 120) * S, (H - 70) * S], fill=(30, 30, 50, 255))  # book stack
        d.rectangle([(W - 250) * S, (H - 190) * S, (W - 140) * S, (H - 150) * S], fill=(80, 30, 34, 255))
    elif kind == "rail":
        y = (H - 120) * S
        d.rectangle([0, y, W * S, y + 12 * S], fill=(26, 26, 32, 255))
        d.rectangle([0, y + 70 * S, W * S, y + 78 * S], fill=(26, 26, 32, 255))
        for x in range(0, W, 64):
            d.rectangle([x * S, y, (x + 8) * S, H * S], fill=(26, 26, 32, 255))
    elif kind == "lamps":
        for k in range(9):
            x = rnd.randint(0, W) * S; y = rnd.randint(0, int(H * 0.5)) * S; r = rnd.randint(30, 80) * S
            d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 196, 120, 70))
        d.polygon([(0, (H - 60) * S), (W * S, (H - 60) * S), (W * S, H * S), (0, H * S)], fill=(46, 30, 22, 255))
    img = img.filter(ImageFilter.GaussianBlur(6 * S)).resize((W, H), Image.LANCZOS)
    return img


def main():
    os.makedirs(OUT, exist_ok=True); os.makedirs(PNG, exist_ok=True)
    report = {}
    for i, (bid, src) in enumerate(SRC.items()):
        im = Image.open(os.path.join(ART, src)).convert("RGB").resize((W, H), Image.LANCZOS)
        im = ImageEnhance.Sharpness(im).enhance(1.1)
        # back
        back = im
        # mid: floor band with feathered top edge
        fy = int(H * FLOOR[bid])
        mask = Image.new("L", (W, H), 0)
        md = ImageDraw.Draw(mask)
        md.rectangle([0, fy, W, H], fill=255)
        mask = mask.filter(ImageFilter.GaussianBlur(28))
        mid = im.copy(); mid.putalpha(mask)
        mid = mid.crop((0, max(0, fy - 90), W, H))
        front = front_layer(FRONT[bid], i)
        sizes = {}
        qb = 58 if bid in ("park",) else 72
        for name, layer, q in (("back", back, qb), ("mid", mid, qb), ("front", front, 70)):
            p = os.path.join(OUT, f"{bid}_{name}.webp")
            layer.save(p, "WEBP", quality=q, method=6)
            layer.save(os.path.join(PNG, f"{bid}_{name}.png"))
            sizes[name] = os.path.getsize(p)
        report[bid] = {"bytes": sum(sizes.values()), "layers": sizes, "mid_top": max(0, fy - 90), "w": W, "h": H}
        print(bid, report[bid]["bytes"], sizes)
    json.dump(report, open(os.path.join(OUT, "backgrounds.json"), "w"), indent=1)


if __name__ == "__main__":
    main()
