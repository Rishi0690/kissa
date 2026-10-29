#!/usr/bin/env python3
"""
riggen — builds the shared 2D rig kit (spec §5): two base rigs (female, male) on ONE skeleton, plus mix-and-match
layers. Every layer is a separate WebP with a pivot, drawn so it snaps onto a joint of the skeleton:

  skin/<rig>/<part>.webp          tintable (multiply by skin tone)
  face/<shape>.webp               head base per face shape (tintable skin)
  hair/<style>_back|front.webp    tintable (multiply by hair colour) + hair/<style>_shine.webp (white, drawn on top)
  outfit/<rig>/<outfit>/<part>_t.webp   tintable cloth (multiply by outfit colour)
  outfit/<rig>/<outfit>/<part>_c.webp   fixed-colour cloth/details (shirts, jeans, ties, shoes)
  acc/<id>.webp                   accessories (glasses, earrings, scarf, backpack, ...)
  prop/<id>.webp                  20 props with a grip pivot (snap to hand / head / back anchors)

Parts: torso (pivot pelvis), head (pivot neck top), upper_arm/forearm/hand, thigh/shin/foot (pivot = proximal joint).
Tintable art is light grey with baked cel shading + dark ink outline, so a multiply tint keeps lines and shadows.
Expressions are NOT baked: the face (eyes, brows, mouth, blush, tears) is drawn as vector layers at runtime from the
face anchors in rig.json, so all 20 expressions, blink and lip-sync work on every head.

Run: python3 tools/riggen/riggen.py  -> app/src/main/assets/rig/ (+ PNG mirror in ../art/v3/png/rig for JVM previews)
"""
import json, math, os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # app/
OUT = os.path.join(ROOT, "app/src/main/assets/rig")
PNG = os.path.join(os.path.dirname(ROOT), "art/v3/png/rig")
SS = 2          # supersampling
SCALE = 1.0     # output px per rig unit (body height ~1600 units)

INK = 46
BASE = 236
SHADE = 196
DEEP = 160

# ---------------------------------------------------------------- skeleton (rig units, pelvis = origin, y down)
SKELETON = {
    "female": {
        "pelvis": (0, 0), "chest": (0, -300), "neck": (0, -520), "head": (0, -560),
        "shoulder_l": (-104, -500), "elbow_l": (-122, -322), "wrist_l": (-130, -162),
        "shoulder_r": (104, -500), "elbow_r": (122, -322), "wrist_r": (130, -162),
        "hip_l": (-54, 14), "knee_l": (-58, 388), "ankle_l": (-60, 742),
        "hip_r": (54, 14), "knee_r": (58, 388), "ankle_r": (60, 742),
    },
    "male": {
        "pelvis": (0, 0), "chest": (0, -310), "neck": (0, -540), "head": (0, -580),
        "shoulder_l": (-124, -515), "elbow_l": (-140, -330), "wrist_l": (-146, -162),
        "shoulder_r": (124, -515), "elbow_r": (140, -330), "wrist_r": (146, -162),
        "hip_l": (-58, 14), "knee_l": (-60, 398), "ankle_l": (-62, 762),
        "hip_r": (58, 14), "knee_r": (60, 398), "ankle_r": (62, 762),
    },
}
# limb widths (top, bottom)
WIDTH = {
    "female": {"upper_arm": (46, 36), "forearm": (36, 28), "thigh": (96, 62), "shin": (60, 38)},
    "male": {"upper_arm": (56, 44), "forearm": (44, 34), "thigh": (100, 68), "shin": (66, 44)},
}

# face anchors in head-local units (origin = head joint / neck top, y down). Used by the runtime face renderer.
FACE = {
    "soft": {"w": 196, "chin": 6, "crown": -262, "eye_y": -100, "eye_dx": 44, "eye_w": 58, "eye_h": 46, "brow_y": -146, "nose_y": -70, "mouth_y": -40, "mouth_w": 30},
    "sharp": {"w": 186, "chin": 14, "crown": -262, "eye_y": -100, "eye_dx": 42, "eye_w": 56, "eye_h": 40, "brow_y": -144, "nose_y": -68, "mouth_y": -36, "mouth_w": 28},
    "round": {"w": 208, "chin": 0, "crown": -258, "eye_y": -96, "eye_dx": 46, "eye_w": 60, "eye_h": 48, "brow_y": -142, "nose_y": -66, "mouth_y": -38, "mouth_w": 32},
}


# ---------------------------------------------------------------- drawing helpers
def catmull(points, closed=True, n=10):
    pts = list(points)
    if len(pts) < 3:
        return pts
    out = []
    m = len(pts)
    rng = range(m) if closed else range(m - 1)
    for i in rng:
        p0 = pts[(i - 1) % m] if closed or i > 0 else pts[0]
        p1 = pts[i]
        p2 = pts[(i + 1) % m]
        p3 = pts[(i + 2) % m] if closed or i + 2 < m else pts[-1]
        for k in range(n):
            t = k / n
            t2, t3 = t * t, t * t * t
            x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3)
            y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
            out.append((x, y))
    if not closed:
        out.append(pts[-1])
    return out


class Canvas:
    """A part canvas in rig units with a pivot. Coordinates passed to draw calls are part-local (pivot = 0,0)."""

    def __init__(self, x0, y0, x1, y1):
        self.x0, self.y0 = x0, y0
        self.w = int(math.ceil((x1 - x0) * SCALE)); self.h = int(math.ceil((y1 - y0) * SCALE))
        self.W, self.H = self.w * SS, self.h * SS

    def P(self, p):
        return ((p[0] - self.x0) * SCALE * SS, (p[1] - self.y0) * SCALE * SS)

    def mask(self, polys, smooth=True, ellipses=(), n=10):
        m = Image.new("L", (self.W, self.H), 0)
        d = ImageDraw.Draw(m)
        for poly in polys:
            pts = catmull(poly, True, n) if smooth else poly
            d.polygon([self.P(p) for p in pts], fill=255)
        for (cx, cy, rx, ry) in ellipses:
            a = self.P((cx - rx, cy - ry)); b = self.P((cx + rx, cy + ry))
            d.ellipse([a, b], fill=255)
        return m

    def pivot(self):
        return (-self.x0 * SCALE, -self.y0 * SCALE)


def shift(m, dx, dy):
    return ImageChops.offset(m, int(dx * SS), int(dy * SS))


def erode(m, px):
    k = max(3, int(px * SS) | 1)
    return m.filter(ImageFilter.MinFilter(k))


def toon(cv, m, base=BASE, shade=SHADE, light=(-1, -1), ink=INK, ink_px=3.2, shade_off=10, rim=True, gloss=False):
    """Fill mask with base tone, cel shadow on the side away from the light, a soft occlusion gradient and an inner ink line."""
    W, H = m.size
    img = Image.new("L", (W, H), base)
    # cel shadow: region of the mask not covered by the mask shifted toward the light
    lit = shift(m, light[0] * shade_off, light[1] * shade_off)
    shadow = ImageChops.subtract(m, lit).filter(ImageFilter.GaussianBlur(1.2 * SS))
    img.paste(shade, (0, 0), shadow)
    # soft ambient occlusion near edges
    inner = m.filter(ImageFilter.GaussianBlur(12 * SS)).point(lambda v: max(0, min(255, (v - 128) * 2)))
    ao = ImageChops.subtract(m, inner)
    img = Image.composite(Image.new("L", (W, H), max(0, shade - 18)), img, ao.point(lambda v: int(v * 0.35)))
    if rim:
        rimm = ImageChops.subtract(m, shift(m, -light[0] * 4, -light[1] * 4)).filter(ImageFilter.GaussianBlur(1.5 * SS))
        img.paste(min(255, base + 14), (0, 0), rimm.point(lambda v: int(v * 0.6)))
    # inner ink line
    edge = ImageChops.subtract(m, erode(m, ink_px))
    img.paste(ink, (0, 0), edge)
    rgba = Image.merge("RGBA", (img, img, img, m))
    return rgba


def colored(cv, m, rgb, ink=(40, 32, 36), ink_px=3.0, shade=0.8, light=(-1, -1), shade_off=9):
    W, H = m.size
    base = Image.new("RGB", (W, H), rgb)
    sh = tuple(int(c * shade) for c in rgb)
    lit = shift(m, light[0] * shade_off, light[1] * shade_off)
    shadow = ImageChops.subtract(m, lit).filter(ImageFilter.GaussianBlur(1.2 * SS))
    base.paste(sh, (0, 0), shadow)
    edge = ImageChops.subtract(m, erode(m, ink_px))
    base.paste(ink, (0, 0), edge)
    r, g, b = base.split()
    return Image.merge("RGBA", (r, g, b, m))


def lines(cv, img, polylines, width=3, fill=INK, smooth=True, alpha=255):
    d = ImageDraw.Draw(img)
    for pl in polylines:
        pts = catmull(pl, False, 8) if smooth and len(pl) > 2 else pl
        col = (fill, fill, fill, alpha) if isinstance(fill, int) else fill + (alpha,)
        d.line([cv.P(p) for p in pts], fill=col, width=int(width * SS), joint="curve")
    return img


def save(cv, img, rel, manifest, extra=None):
    img = img.resize((cv.w, cv.h), Image.LANCZOS)
    os.makedirs(os.path.dirname(os.path.join(OUT, rel)), exist_ok=True)
    os.makedirs(os.path.dirname(os.path.join(PNG, rel)), exist_ok=True)
    img.save(os.path.join(OUT, rel + ".webp"), "WEBP", quality=86, method=6)
    img.save(os.path.join(PNG, rel + ".png"))
    e = {"w": cv.w, "h": cv.h, "px": round(cv.pivot()[0], 1), "py": round(cv.pivot()[1], 1)}
    if extra:
        e.update(extra)
    manifest[rel] = e


def blank(cv):
    return Image.new("RGBA", (cv.W, cv.H), (0, 0, 0, 0))


def over(a, b):
    return Image.alpha_composite(a, b)


def clip(img, m):
    """Keep img only inside mask m."""
    r, g, b, a = img.split()
    return Image.merge("RGBA", (r, g, b, ImageChops.multiply(a, m)))


# ---------------------------------------------------------------- limbs
def limb_poly(length, w0, w1, bulge=1.06):
    """Tapered capsule from (0,0) down to (0,length), local to the proximal joint."""
    h0, h1 = w0 / 2, w1 / 2
    return [(-h0, -h0 * 0.2), (-h0 * bulge, length * 0.3), (-h1 * 1.02, length * 0.82), (-h1 * 0.8, length + h1 * 0.5), (0, length + h1 * 0.75),
            (h1 * 0.8, length + h1 * 0.5), (h1 * 1.02, length * 0.82), (h0 * bulge, length * 0.3), (h0, -h0 * 0.2), (0, -h0 * 0.62)]


def dist(a, b):
    return math.hypot(b[0] - a[0], b[1] - a[1])


def build_skin(rig, man):
    S = SKELETON[rig]; Wd = WIDTH[rig]
    # limbs (left side; right side is the mirror at runtime? no: draw both explicitly to keep light direction)
    for side, sx in (("l", -1), ("r", 1)):
        for part, a, b in (("upper_arm", "shoulder", "elbow"), ("forearm", "elbow", "wrist"), ("thigh", "hip", "knee"), ("shin", "knee", "ankle")):
            L = dist(S[f"{a}_{side}"], S[f"{b}_{side}"])
            w0, w1 = Wd[part]
            cv = Canvas(-w0 * 0.7, -w0 * 0.7, w0 * 0.7, L + w1)
            m = cv.mask([limb_poly(L, w0, w1)])
            img = toon(cv, m, light=(-sx * 0.0 - 1, -1))
            if part == "shin":  # knee hint
                img = lines(cv, img, [[(-w0 * 0.18, 4), (0, 10), (w0 * 0.18, 4)]], width=2, fill=120)
            save(cv, img, f"skin/{rig}/{part}_{side}", man, {"len": round(L, 1)})
        # hand: relaxed mitten with thumb, local to wrist, pointing down
        hw = 34 if rig == "female" else 40
        cv = Canvas(-hw, -12, hw, 96)
        pts = [(-hw * 0.5, -6), (-hw * 0.62, 30), (-hw * 0.55, 62), (-hw * 0.3, 84), (0, 90), (hw * 0.3, 84), (hw * 0.55, 62), (hw * 0.6, 30), (hw * 0.5, -6), (0, -10)]
        thumb = [(sx * -hw * 0.45, 10), (sx * -hw * 0.95, 34), (sx * -hw * 0.85, 52), (sx * -hw * 0.5, 44)]
        m = cv.mask([pts, thumb])
        img = toon(cv, m, ink_px=2.6)
        img = lines(cv, img, [[(-hw * 0.18, 60), (-hw * 0.2, 84)], [(hw * 0.12, 62), (hw * 0.12, 86)]], width=1.6, fill=130)
        save(cv, img, f"skin/{rig}/hand_{side}", man, {"grip": [0, 52]})
        # bare foot (rarely visible; shoes cover it)
        cv = Canvas(-40, -10, 40, 70)
        m = cv.mask([[(-22, -6), (-30, 30), (-34, 52), (-14, 64), (sx * 30, 60), (sx * 34 * 0.6, 40), (24, 26), (22, -6)]])
        save(cv, toon(cv, m), f"skin/{rig}/foot_{side}", man)
    # torso (+neck) local to pelvis
    f = rig == "female"
    sh = abs(S["shoulder_l"][0]); ny = S["neck"][1]; hy = S["head"][1]
    cv = Canvas(-sh - 40, hy - 10, sh + 40, 110)
    if f:
        body = [(0, ny + 6), (-30, ny + 10), (-sh + 10, ny + 22), (-sh - 6, ny + 60), (-sh + 8, -330), (-80, -260), (-74, -150), (-96, -40), (-102, 30), (-60, 92), (0, 104),
                (60, 92), (102, 30), (96, -40), (74, -150), (80, -260), (sh - 8, -330), (sh + 6, ny + 60), (sh - 10, ny + 22), (30, ny + 10)]
    else:
        body = [(0, ny + 6), (-36, ny + 12), (-sh + 10, ny + 22), (-sh - 8, ny + 66), (-sh + 4, -340), (-98, -250), (-90, -140), (-92, -40), (-96, 30), (-60, 96), (0, 108),
                (60, 96), (96, 30), (92, -40), (90, -140), (98, -250), (sh - 4, -340), (sh + 8, ny + 66), (sh - 10, ny + 22), (36, ny + 12)]
    neck_w = 24 if f else 30
    neck = [(-neck_w, hy + 6), (-neck_w - 2, ny + 30), (neck_w + 2, ny + 30), (neck_w, hy + 6)]
    m = cv.mask([body, neck])
    img = toon(cv, m)
    # neck shadow under chin + collarbones
    img = lines(cv, img, [[(-sh * 0.55, ny + 40), (-20, ny + 46)], [(20, ny + 46), (sh * 0.55, ny + 40)]], width=2, fill=150)
    sd = ImageDraw.Draw(img)
    a = cv.P((-neck_w, hy + 10)); b = cv.P((neck_w, hy + 34))
    sd.ellipse([a, b], fill=(SHADE - 20, SHADE - 20, SHADE - 20, 255))
    save(cv, img, f"skin/{rig}/torso", man)
    return body


def build_faces(man):
    for shape, F in FACE.items():
        w = F["w"] / 2; chin = F["chin"]; crown = F["crown"]
        cv = Canvas(-w - 30, crown - 10, w + 30, chin + 20)
        if shape == "sharp":
            face = [(0, chin), (-w * 0.42, chin - 22), (-w * 0.86, -60), (-w, -120), (-w * 0.98, -190), (-w * 0.7, crown + 16), (0, crown), (w * 0.7, crown + 16), (w * 0.98, -190), (w, -120), (w * 0.86, -60), (w * 0.42, chin - 22)]
        elif shape == "round":
            face = [(0, chin), (-w * 0.55, chin - 14), (-w * 0.92, -50), (-w, -120), (-w * 0.97, -195), (-w * 0.7, crown + 14), (0, crown), (w * 0.7, crown + 14), (w * 0.97, -195), (w, -120), (w * 0.92, -50), (w * 0.55, chin - 14)]
        else:
            face = [(0, chin), (-w * 0.5, chin - 18), (-w * 0.9, -56), (-w, -120), (-w * 0.98, -192), (-w * 0.7, crown + 15), (0, crown), (w * 0.7, crown + 15), (w * 0.98, -192), (w, -120), (w * 0.9, -56), (w * 0.5, chin - 18)]
        ears = [(-w - 14, -112, 16, 26), (w + 14, -112, 16, 26)]
        m = cv.mask([face], ellipses=ears)
        img = toon(cv, m, shade_off=8, light=(-1, -0.6))
        # ear inner lines
        img = lines(cv, img, [[(-w - 12, -126), (-w - 18, -110), (-w - 12, -96)], [(w + 12, -126), (w + 18, -110), (w + 12, -96)]], width=1.8, fill=140)
        # nose hint
        img = lines(cv, img, [[(2, F["nose_y"] - 10), (5, F["nose_y"]), (0, F["nose_y"] + 3)]], width=1.8, fill=150)
        save(cv, img, f"face/{shape}", man, {k: v for k, v in F.items()})


# ---------------------------------------------------------------- hair
def strands(cv, img, top, bottom, n, spread, curve=0.0, width=1.8, fill=110, alpha=200):
    pls = []
    for i in range(n):
        t = i / max(1, n - 1)
        x0 = top[0] + (t - 0.5) * spread[0]
        x1 = bottom[0] + (t - 0.5) * spread[1]
        mid = ((x0 + x1) / 2 + curve * (t - 0.5) * 30, (top[1] + bottom[1]) / 2)
        pls.append([(x0, top[1]), mid, (x1, bottom[1])])
    return lines(cv, img, pls, width=width, fill=fill, alpha=alpha)


def shine(cv, polys, alpha=80):
    m = cv.mask(polys)
    img = Image.new("RGBA", (cv.W, cv.H), (255, 255, 255, 0))
    img.putalpha(m.filter(ImageFilter.GaussianBlur(2 * SS)).point(lambda v: int(v * alpha / 255)))
    return img


def build_hair(man):
    W = 112  # half head width incl. volume
    cr = -262
    styles = {}
    # each style: back polys (behind head+body), front polys (fringe over forehead), shine band polys
    styles["long_straight"] = dict(
        back=[[(-W - 4, -150), (-W - 10, -30), (-W - 6, 120), (-W + 4, 260), (-60, 300), (0, 290), (60, 300), (W - 4, 260), (W + 6, 120), (W + 10, -30), (W + 4, -150), (W * 0.7, cr + 4), (0, cr - 18), (-W * 0.7, cr + 4)]],
        front=[[(-W, -120), (-W - 6, -60), (-W + 14, 40), (-W + 30, -40), (-W + 40, -150), (-30, -168), (-10, -140), (20, -170), (60, -160), (W - 36, -150), (W - 26, -40), (W - 12, 40), (W + 6, -60), (W, -120), (W * 0.8, cr + 20), (0, cr - 14), (-W * 0.8, cr + 20)]],
        sh=[[(-70, -228), (-20, -238), (40, -236), (76, -224), (60, -214), (0, -222), (-56, -214)]])
    styles["long_wavy"] = dict(
        back=[[(-W - 8, -150), (-W - 20, -40), (-W - 4, 40), (-W - 22, 140), (-W + 2, 250), (-70, 310), (0, 296), (70, 310), (W - 2, 250), (W + 22, 140), (W + 4, 40), (W + 20, -40), (W + 8, -150), (W * 0.7, cr + 4), (0, cr - 22), (-W * 0.7, cr + 4)]],
        front=[[(-W - 2, -120), (-W - 14, -40), (-W + 6, 60), (-W + 26, -20), (-W + 34, -140), (-46, -176), (-12, -150), (14, -178), (54, -168), (W - 34, -140), (W - 26, -20), (W - 6, 60), (W + 14, -40), (W + 2, -120), (W * 0.8, cr + 18), (0, cr - 18), (-W * 0.8, cr + 18)]],
        sh=[[(-74, -226), (-24, -240), (36, -238), (80, -222), (62, -212), (0, -224), (-58, -212)]])
    styles["ponytail"] = dict(
        back=[[(-W + 6, -170), (-W + 14, -110), (-50, -96), (0, -100), (50, -96), (W - 14, -110), (W - 6, -170), (W * 0.7, cr + 6), (0, cr - 14), (-W * 0.7, cr + 6)],
              [(W - 30, cr + 30), (W + 26, cr + 40), (W + 40, -120), (W + 30, 40), (W + 4, 140), (W - 6, 40), (W - 2, -110)]],
        front=[[(-W + 2, -130), (-W + 8, -70), (-W + 24, -120), (-36, -176), (0, -158), (36, -178), (W - 24, -120), (W - 8, -70), (W - 2, -130), (W * 0.8, cr + 22), (0, cr - 10), (-W * 0.8, cr + 22)]],
        sh=[[(-66, -230), (-10, -240), (50, -232), (40, -220), (-10, -226), (-54, -218)]])
    styles["bob"] = dict(
        back=[[(-W - 10, -150), (-W - 14, -40), (-W - 6, 10), (-80, 26), (0, 20), (80, 26), (W + 6, 10), (W + 14, -40), (W + 10, -150), (W * 0.7, cr + 2), (0, cr - 20), (-W * 0.7, cr + 2)]],
        front=[[(-W - 4, -130), (-W - 10, -40), (-W + 10, 6), (-W + 26, -60), (-W + 30, -150), (-50, -164), (-W * 0.2, -150), (0, -160), (40, -150), (W - 30, -150), (W - 26, -60), (W - 10, 6), (W + 10, -40), (W + 4, -130), (W * 0.8, cr + 18), (0, cr - 16), (-W * 0.8, cr + 18)]],
        sh=[[(-70, -226), (-14, -238), (46, -234), (72, -222), (40, -216), (-10, -224), (-56, -214)]])
    styles["bun"] = dict(
        back=[[(-W + 6, -170), (-W + 12, -110), (0, -104), (W - 12, -110), (W - 6, -170), (W * 0.7, cr + 6), (0, cr - 14), (-W * 0.7, cr + 6)]],
        front=[[(-W + 2, -128), (-W + 6, -70), (-W + 22, -126), (-40, -172), (0, -150), (40, -174), (W - 22, -126), (W - 6, -70), (W - 2, -128), (W * 0.8, cr + 22), (0, cr - 10), (-W * 0.8, cr + 22)],
               [(-46, cr - 6), (-52, cr - 50), (-20, cr - 84), (20, cr - 84), (52, cr - 50), (46, cr - 6), (0, cr + 6)]],
        sh=[[(-60, -232), (0, -240), (52, -232), (36, -222), (-20, -226)], [(-26, cr - 66), (10, cr - 74), (24, cr - 60), (-10, cr - 56)]])
    styles["braid"] = dict(
        back=[[(-W + 4, -170), (-W + 10, -110), (0, -100), (W - 10, -110), (W - 4, -170), (W * 0.7, cr + 4), (0, cr - 16), (-W * 0.7, cr + 4)]],
        front=[[(-W + 2, -128), (-W + 4, -60), (-W + 22, -120), (-30, -170), (6, -152), (40, -172), (W - 22, -120), (W - 4, -60), (W - 2, -128), (W * 0.8, cr + 22), (0, cr - 12), (-W * 0.8, cr + 22)],
               [(W - 34, -80), (W - 6, -70), (W + 8, 0), (W - 4, 70), (W + 6, 140), (W - 12, 220), (W - 30, 232), (W - 40, 150), (W - 30, 70), (W - 44, 0)]],
        sh=[[(-60, -230), (0, -240), (54, -230), (36, -220), (-20, -224)]])
    styles["short_messy"] = dict(
        back=[[(-W + 2, -150), (-W + 6, -80), (0, -70), (W - 6, -80), (W - 2, -150), (W * 0.7, cr + 2), (0, cr - 20), (-W * 0.7, cr + 2)]],
        front=[[(-W + 4, -112), (-W - 8, -150), (-W + 2, -196), (-W - 4, -214), (-60, cr - 16), (-20, cr - 30), (20, cr - 22), (66, cr - 26), (W + 4, -212), (W - 2, -190), (W + 10, -150), (W - 4, -112), (W - 20, -150), (W - 34, -132), (40, -168), (20, -150), (0, -172), (-24, -152), (-44, -170), (-W + 34, -130), (-W + 20, -150)]],
        sh=[[(-60, -228), (0, -244), (54, -232), (30, -220), (-24, -226)]])
    styles["side_part"] = dict(
        back=[[(-W + 2, -150), (-W + 6, -84), (0, -76), (W - 6, -84), (W - 2, -150), (W * 0.7, cr + 4), (0, cr - 16), (-W * 0.7, cr + 4)]],
        front=[[(-W + 4, -116), (-W - 4, -170), (-W + 10, -214), (-50, cr - 14), (10, cr - 22), (70, cr - 10), (W + 2, -210), (W + 6, -160), (W - 4, -116), (W - 22, -158), (W - 50, -176), (30, -190), (-20, -196), (-40, -172), (-W + 26, -150), (-W + 16, -134)]],
        sh=[[(-50, -232), (10, -244), (64, -228), (40, -218), (-10, -228)]])
    styles["curly"] = dict(
        back=[[(-W - 4, -150), (-W - 6, -90), (0, -80), (W + 6, -90), (W + 4, -150), (W * 0.74, cr), (0, cr - 26), (-W * 0.74, cr)]],
        front=[[(-W - 6, -110), (-W - 18, -150), (-W - 6, -190), (-W - 14, -224), (-70, cr - 22), (-36, cr - 36), (0, cr - 28), (36, cr - 38), (72, cr - 22), (W + 14, -224), (W + 6, -190), (W + 18, -150), (W + 6, -110), (W - 16, -140), (W - 26, -168), (50, -160), (26, -176), (0, -160), (-26, -178), (-52, -160), (-W + 26, -168), (-W + 16, -140)]],
        sh=[[(-56, -236), (-6, -248), (46, -240), (24, -228), (-24, -232)]])
    styles["buzz"] = dict(
        back=[[(-W + 8, -150), (-W + 10, -100), (0, -92), (W - 10, -100), (W - 8, -150), (W * 0.7, cr + 10), (0, cr - 4), (-W * 0.7, cr + 10)]],
        front=[[(-W + 10, -140), (-W + 8, -190), (-60, cr + 4), (0, cr - 6), (60, cr + 4), (W - 8, -190), (W - 10, -140), (W - 30, -170), (0, -178), (-W + 30, -170)]],
        sh=[[(-40, -238), (10, -246), (50, -236), (20, -230)]])
    styles["undercut"] = dict(
        back=[[(-W + 8, -150), (-W + 10, -100), (0, -92), (W - 10, -100), (W - 8, -150), (W * 0.7, cr + 8), (0, cr - 8), (-W * 0.7, cr + 8)]],
        front=[[(-W + 12, -150), (-W + 10, -200), (-70, cr - 18), (-10, cr - 40), (60, cr - 30), (W - 4, -214), (W - 2, -170), (W - 20, -160), (40, -184), (0, -170), (-40, -186), (-W + 30, -176)]],
        sh=[[(-50, -244), (10, -258), (56, -246), (26, -236), (-20, -240)]])
    styles["man_bun"] = dict(
        back=[[(-W + 4, -150), (-W + 8, -96), (0, -88), (W - 8, -96), (W - 4, -150), (W * 0.7, cr + 4), (0, cr - 14), (-W * 0.7, cr + 4)]],
        front=[[(-W + 6, -120), (-W + 2, -180), (-60, cr - 4), (0, cr - 12), (60, cr - 4), (W - 2, -180), (W - 6, -120), (W - 24, -160), (20, -180), (-30, -176), (-W + 24, -160)],
               [(-36, cr - 4), (-40, cr - 40), (0, cr - 64), (40, cr - 40), (36, cr - 4)]],
        sh=[[(-40, -240), (12, -250), (52, -238), (24, -230)], [(-16, cr - 50), (12, cr - 56), (18, cr - 44)]])
    for name, st in styles.items():
        cv = Canvas(-W - 50, cr - 100, W + 60, 340)
        # keep side locks off the face: below the brow line, front hair may not cross the face outline
        st["front"] = [[(math.copysign(max(abs(x), 100), x if x != 0 else 1) if (y > -138 and abs(x) < 100) else x, y) for (x, y) in poly] for poly in st["front"]]
        for layer in ("back", "front"):
            m = cv.mask(st[layer])
            img = toon(cv, m, base=226, shade=176, ink=40, ink_px=3.0, shade_off=12, light=(-1, -1))
            ys = [p[1] for poly in st[layer] for p in poly]
            img = clip(strands(cv, img, (0, min(ys) + 10), (0, max(ys) - 6), 9, (W * 1.2, W * 1.9), curve=1.0, width=1.6, fill=120, alpha=170), m)
            save(cv, img, f"hair/{name}_{layer}", man)
        save(cv, shine(cv, st["sh"]), f"hair/{name}_shine", man)


# ---------------------------------------------------------------- outfits
SHIRT_WHITE = (238, 238, 242)
JEANS = (66, 92, 140)
BLACK = (40, 40, 46)
SHOE_DARK = (52, 40, 36)
SNEAKER = (236, 236, 236)
SOCK_NAVY = (40, 46, 74)
TIE_RED = (150, 36, 50)


def torso_cloth_poly(rig, body, drop=0, flare=0, neck_open=26):
    """Cloth covering the torso polygon, optionally extended below the hips (skirts, kurtas, dresses)."""
    S = SKELETON[rig]; ny = S["neck"][1]
    pts = []
    for (x, y) in body:
        if y <= ny + 12:   # neckline
            pts.append((max(-neck_open, min(neck_open, x)) * 1.0, ny + 14))
        else:
            pts.append((x * 1.04, y))
    if drop:
        # replace bottom (y > 40) with a hem
        pts = [p for p in pts if p[1] < 40]
        i = max(range(len(pts)), key=lambda k: pts[k][0])
        lx = -110 - flare; rx = 110 + flare
        hem = [(rx, drop), (0, drop + 10), (lx, drop)]
        # insert hem after rightmost-low point ordering: simply rebuild left->right
        left = [p for p in pts if p[0] < 0]; right = [p for p in pts if p[0] >= 0]
        pts = sorted(right, key=lambda p: p[1]) + hem + sorted(left, key=lambda p: -p[1])
    return pts


def build_outfits(man, bodies):
    outfits = ["college_blazer", "hoodie", "kurta", "tshirt_jeans", "formal_shirt", "denim_jacket", "sweater", "party_dress"]
    for rig in ("female", "male"):
        S = SKELETON[rig]; Wd = WIDTH[rig]; body = bodies[rig]
        sh = abs(S["shoulder_l"][0]); ny = S["neck"][1]; hy = S["head"][1]
        f = rig == "female"
        for o in outfits:
            base = f"outfit/{rig}/{o}"
            # ---------- torso
            cv = Canvas(-sh - 60, hy - 10, sh + 60, 420)
            skirt = f and o in ("college_blazer", "party_dress")
            long_top = o == "kurta"
            drop = 250 if (skirt and o == "college_blazer") else (330 if o == "party_dress" else (260 if long_top else 0))
            flare = 30 if o in ("college_blazer", "party_dress") else (10 if long_top else 0)
            poly = torso_cloth_poly(rig, body, drop=drop, flare=flare, neck_open=30 if o not in ("hoodie", "sweater") else 34)
            m = cv.mask([poly])
            tint = toon(cv, m, base=232, shade=182, ink=40)
            col = blank(cv)
            if o == "college_blazer":
                # white shirt V + tie (fixed), blazer lapels with piping (tinted)
                v = cv.mask([[(-34, ny + 12), (0, ny + 120), (34, ny + 12)]], smooth=False)
                col = over(col, colored(cv, v, SHIRT_WHITE))
                tie = cv.mask([[(-8, ny + 20), (8, ny + 20), (12, ny + 100), (0, ny + 118), (-12, ny + 100)]], smooth=False)
                col = over(col, colored(cv, tie, TIE_RED))
                col = lines(cv, col, [[(-36, ny + 12), (-6, ny + 140), (-10, -120)], [(36, ny + 12), (6, ny + 140), (10, -120)]], width=3, fill=(245, 245, 250))
                col = lines(cv, col, [[(-60, -200), (-30, -196)]], width=4, fill=(220, 190, 90))  # crest
                if skirt:
                    pl = [[(x, 10), (x * 1.18, drop - 4)] for x in range(-90, 100, 30)]
                    tint = lines(cv, tint, pl, width=2, fill=120, smooth=False)
            elif o == "hoodie":
                tint = lines(cv, tint, [[(-30, ny + 16), (-20, ny + 60), (0, ny + 70), (20, ny + 60), (30, ny + 16)], [(-60, -40), (60, -40)]], width=3, fill=90)
                col = lines(cv, col, [[(-12, ny + 60), (-14, ny + 140)], [(12, ny + 60), (14, ny + 140)]], width=3, fill=(235, 235, 235))
                pocket = cv.mask([[(-70, -110), (70, -110), (84, -20), (-84, -20)]], smooth=False)
                tint = over(tint, toon(cv, pocket, base=220, shade=190, ink=60, ink_px=2.4))
            elif o == "kurta":
                tint = lines(cv, tint, [[(0, ny + 16), (0, ny + 150)]], width=3, fill=100)
                for yy in range(int(ny) + 40, int(ny) + 150, 30):
                    tint = lines(cv, tint, [[(-3, yy), (3, yy)]], width=6, fill=200, smooth=False)
                col = lines(cv, col, [[(-90, drop - 20), (90, drop - 20)]], width=6, fill=(210, 170, 70), smooth=False)
            elif o == "tshirt_jeans":
                tint = lines(cv, tint, [[(-26, ny + 16), (0, ny + 36), (26, ny + 16)]], width=3, fill=110)
                star = cv.mask([[(0, -230), (12, -200), (44, -198), (18, -178), (28, -146), (0, -166), (-28, -146), (-18, -178), (-44, -198), (-12, -200)]], smooth=False)
                col = over(col, colored(cv, star, (250, 250, 250), ink_px=1.5))
            elif o == "formal_shirt":
                tint = lines(cv, tint, [[(0, ny + 30), (0, 60)], [(-30, ny + 14), (-8, ny + 34)], [(30, ny + 14), (8, ny + 34)]], width=3, fill=100)
                for yy in range(int(ny) + 60, 40, 60):
                    tint = lines(cv, tint, [[(-4, yy), (4, yy)]], width=6, fill=170, smooth=False)
            elif o == "denim_jacket":
                v = cv.mask([[(-40, ny + 12), (0, ny + 160), (40, ny + 12)]], smooth=False)
                col = over(col, colored(cv, v, (40, 40, 44)))
                tint = lines(cv, tint, [[(-90, -260), (-40, -262)], [(40, -262), (90, -260)], [(-6, ny + 160), (-6, 40)], [(6, ny + 160), (6, 40)]], width=3, fill=120)
                col = lines(cv, col, [[(-70, -280), (-40, -280)], [(40, -280), (70, -280)]], width=2, fill=(220, 200, 150))
            elif o == "sweater":
                for yy in (ny + 40, -250, -80):
                    tint = lines(cv, tint, [[(-sh, yy), (0, yy + 10), (sh, yy)]], width=2, fill=150)
                tint = lines(cv, tint, [[(-34, ny + 16), (0, ny + 34), (34, ny + 16)]], width=6, fill=170)
            elif o == "party_dress":
                col = lines(cv, col, [[(-80, -150), (0, -140), (80, -150)]], width=4, fill=(240, 220, 160))
                for x in range(-80, 90, 40):
                    tint = lines(cv, tint, [[(x * 0.6, -120), (x * 1.3, drop - 6)]], width=2, fill=150)
            if not f and o == "party_dress":
                # male "party" = kurta-length sherwani style top
                pass
            save(cv, tint, f"{base}/torso_t", man)
            save(cv, col, f"{base}/torso_c", man)
            # ---------- sleeves
            sleeve = {"college_blazer": "full", "hoodie": "full", "kurta": "full", "tshirt_jeans": "short", "formal_shirt": "full", "denim_jacket": "full", "sweater": "full", "party_dress": "none" if f else "full"}[o]
            for side, sx in (("l", -1), ("r", 1)):
                for part, a, b in (("upper_arm", "shoulder", "elbow"), ("forearm", "elbow", "wrist")):
                    L = dist(S[f"{a}_{side}"], S[f"{b}_{side}"]); w0, w1 = Wd[part]
                    cv = Canvas(-w0 * 0.9, -w0 * 0.9, w0 * 0.9, L + w1)
                    if sleeve == "none" or (sleeve == "short" and part == "forearm"):
                        save(cv, blank(cv), f"{base}/{part}_{side}_t", man); continue
                    ext = 1.18
                    length = L * (0.55 if (sleeve == "short" and part == "upper_arm") else 1.0)
                    poly = limb_poly(length, w0 * ext, w1 * ext * (1.15 if part == "forearm" else 1.0), bulge=1.08)
                    if part == "forearm":
                        poly = [(x, min(y, length - 4)) for (x, y) in poly]
                    m = cv.mask([poly])
                    img = toon(cv, m, base=232, shade=182, ink=40)
                    img = lines(cv, img, [[(-w0 * 0.2, length * 0.45), (0, length * 0.5), (w0 * 0.15, length * 0.42)]], width=1.6, fill=150)
                    if part == "forearm":
                        img = lines(cv, img, [[(-w1 * 0.6, length - 18), (w1 * 0.6, length - 18)]], width=2.4, fill=120, smooth=False)
                    save(cv, img, f"{base}/{part}_{side}_t", man)
                # ---------- legs: fixed-colour bottoms (jeans/trousers/stockings) per outfit
                for part, a, b in (("thigh", "hip", "knee"), ("shin", "knee", "ankle")):
                    L = dist(S[f"{a}_{side}"], S[f"{b}_{side}"]); w0, w1 = Wd[part]
                    cv = Canvas(-w0 * 0.8, -w0 * 0.8, w0 * 0.8, L + w1)
                    if skirt or o == "party_dress" and f:
                        if part == "shin" and o == "college_blazer":
                            m = cv.mask([limb_poly(L, w0 * 1.04, w1 * 1.04)])
                            save(cv, colored(cv, m, SOCK_NAVY), f"{base}/{part}_{side}_c", man)
                        else:
                            save(cv, blank(cv), f"{base}/{part}_{side}_c", man)
                        continue
                    colr = {"tshirt_jeans": JEANS, "denim_jacket": (58, 58, 66), "hoodie": (70, 76, 90), "kurta": (238, 232, 220), "formal_shirt": (48, 50, 60),
                            "college_blazer": (44, 48, 70), "sweater": (120, 96, 76), "party_dress": (36, 34, 40)}[o]
                    poly = limb_poly(L, w0 * 1.1, w1 * (1.25 if part == "shin" else 1.12))
                    if part == "shin":
                        poly = [(x, min(y, L + 6)) for (x, y) in poly]
                    m = cv.mask([poly])
                    img = colored(cv, m, colr)
                    if colr == JEANS:
                        img = lines(cv, img, [[(w0 * 0.25 * sx, 10), (w1 * 0.3 * sx, L)]], width=1.6, fill=(200, 170, 110))
                    save(cv, img, f"{base}/{part}_{side}_c", man)
                # ---------- shoes
                cv = Canvas(-46, -16, 46, 76)
                shoe = [(-26, -10), (-34, 30), (-36, 54), (-18, 66), (sx * 34, 62), (sx * 38 * 0.7, 40), (28, 26), (26, -10)]
                m = cv.mask([shoe])
                c = SNEAKER if o in ("tshirt_jeans", "hoodie", "denim_jacket") else (SHOE_DARK if o != "party_dress" else (30, 30, 34))
                img = colored(cv, m, c)
                if c == SNEAKER:
                    img = lines(cv, img, [[(-30, 50), (sx * 30, 52)]], width=4, fill=(200, 60, 60), smooth=False)
                save(cv, img, f"{base}/foot_{side}_c", man)


# ---------------------------------------------------------------- accessories
def build_acc(man):
    # head-anchored accessories in head-local units (soft face anchors)
    F = FACE["soft"]
    cv = Canvas(-120, -170, 120, -40)
    img = blank(cv)
    d = ImageDraw.Draw(img)
    for sx in (-1, 1):
        cx = sx * F["eye_dx"]
        d.ellipse([cv.P((cx - 30, F["eye_y"] - 26)), cv.P((cx + 30, F["eye_y"] + 26))], outline=(40, 34, 30, 255), width=int(4 * SS))
    lines(cv, img, [[(-14, F["eye_y"] - 6), (0, F["eye_y"] - 12), (14, F["eye_y"] - 6)], [(-74, F["eye_y"] - 8), (-104, F["eye_y"] - 14)], [(74, F["eye_y"] - 8), (104, F["eye_y"] - 14)]], width=3.5, fill=(40, 34, 30))
    save(cv, img, "acc/glasses_round", man, {"anchor": "head"})
    img = blank(cv); d = ImageDraw.Draw(img)
    for sx in (-1, 1):
        cx = sx * F["eye_dx"]
        d.rounded_rectangle([cv.P((cx - 32, F["eye_y"] - 22)), cv.P((cx + 32, F["eye_y"] + 22))], radius=int(8 * SS), outline=(30, 30, 34, 255), width=int(5 * SS))
    lines(cv, img, [[(-12, F["eye_y"] - 8), (12, F["eye_y"] - 8)], [(-76, F["eye_y"] - 10), (-104, F["eye_y"] - 14)], [(76, F["eye_y"] - 10), (104, F["eye_y"] - 14)]], width=4, fill=(30, 30, 34), smooth=False)
    save(cv, img, "acc/glasses_square", man, {"anchor": "head"})
    cv = Canvas(-130, -110, 130, -40); img = blank(cv); d = ImageDraw.Draw(img)
    for sx in (-1, 1):
        x = sx * (F["w"] / 2 + 14)
        d.line([cv.P((x, -90)), cv.P((x, -72))], fill=(210, 170, 60, 255), width=int(3 * SS))
        d.ellipse([cv.P((x - 7, -74)), cv.P((x + 7, -60))], fill=(230, 190, 70, 255), outline=(120, 90, 30, 255))
    save(cv, img, "acc/earrings", man, {"anchor": "head"})
    cv = Canvas(-20, -160, 20, -120); img = blank(cv); d = ImageDraw.Draw(img)
    d.ellipse([cv.P((-6, -150)), cv.P((6, -138))], fill=(190, 30, 50, 255))
    save(cv, img, "acc/bindi", man, {"anchor": "head"})
    cv = Canvas(-80, -20, 80, 120); img = blank(cv)
    m = cv.mask([[(-60, 0), (60, 0), (64, 40), (20, 60), (36, 110), (10, 112), (0, 64), (-60, 40)]])
    save(cv, colored(cv, m, (180, 60, 70)), "acc/scarf", man, {"anchor": "neck"})
    cv = Canvas(-30, -16, 30, 30); img = blank(cv)
    m = cv.mask([[(-22, -10), (22, -10), (24, 22), (-24, 22)]])
    img = colored(cv, m, (50, 46, 44)); d = ImageDraw.Draw(img)
    d.ellipse([cv.P((-12, -6)), cv.P((12, 18))], fill=(220, 220, 225, 255), outline=(60, 60, 60, 255))
    save(cv, img, "acc/watch", man, {"anchor": "wrist_l"})
    cv = Canvas(-150, -560, 150, -120)
    m = cv.mask([[(-120, -520), (120, -520), (130, -200), (100, -150), (-100, -150), (-130, -200)]])
    save(cv, colored(cv, m, (70, 80, 110)), "acc/backpack", man, {"anchor": "pelvis", "behind": True})
    # beard: tintable (hair colour), head-local
    cv = Canvas(-110, -90, 110, 30)
    m = cv.mask([[(-96, -86), (-80, -30), (-40, 10), (0, 22), (40, 10), (80, -30), (96, -86), (60, -40), (24, -26), (0, -36), (-24, -26), (-60, -40)]])
    img = toon(cv, m, base=210, shade=180, ink=60, ink_px=2)
    save(cv, img, "acc/beard", man, {"anchor": "head", "tint": "hair"})


# ---------------------------------------------------------------- props
def build_props(man):
    P = {}
    def prop(name, box, polys_colors, grip=(0, 0), extra_lines=None, ell=None):
        cv = Canvas(*box)
        img = blank(cv)
        for poly, col in polys_colors:
            m = cv.mask([poly])
            img = over(img, colored(cv, m, col))
        if ell:
            for (cx, cy, rx, ry, col) in ell:
                m = cv.mask([], ellipses=[(cx, cy, rx, ry)])
                img = over(img, colored(cv, m, col))
        if extra_lines:
            for pl, w, c in extra_lines:
                img = lines(cv, img, [pl], width=w, fill=c)
        save(cv, img, f"prop/{name}", man, {"grip": list(grip)})
    prop("phone", (-24, -50, 24, 50), [([(-20, -46), (20, -46), (20, 46), (-20, 46)], (30, 30, 36)), ([(-16, -38), (16, -38), (16, 36), (-16, 36)], (90, 140, 210))])
    prop("rose", (-30, -120, 30, 40), [([(-3, -60), (3, -60), (3, 36), (-3, 36)], (60, 120, 60)), ([(-10, -30), (-28, -40), (-12, -20)], (70, 140, 70))],
         ell=[(0, -80, 20, 22, (200, 30, 50)), (0, -86, 10, 10, (160, 20, 40))])
    prop("bag", (-60, -40, 60, 90), [([(-50, -10), (50, -10), (56, 80), (-56, 80)], (120, 80, 50))], grip=(0, -30), extra_lines=[([(-30, -10), (-24, -36), (24, -36), (30, -10)], 5, (90, 60, 40))])
    prop("coffee_cup", (-26, -40, 26, 40), [([(-18, -30), (18, -30), (14, 34), (-14, 34)], (245, 245, 240)), ([(-19, -6), (19, -6), (18, 10), (-18, 10)], (140, 90, 60)), ([(-22, -38), (22, -38), (20, -28), (-20, -28)], (60, 50, 50))])
    prop("chai_glass", (-20, -32, 20, 32), [([(-14, -26), (14, -26), (10, 26), (-10, 26)], (230, 236, 240)), ([(-12, -10), (12, -10), (10, 24), (-10, 24)], (190, 130, 80))])
    prop("book", (-40, -52, 40, 52), [([(-34, -46), (34, -46), (34, 46), (-34, 46)], (60, 90, 150)), ([(-28, -46), (-22, -46), (-22, 46), (-28, 46)], (40, 60, 110))])
    prop("notebook", (-36, -48, 36, 48), [([(-30, -42), (30, -42), (30, 42), (-30, 42)], (230, 200, 90))], extra_lines=[([(-30, -30), (30, -30)], 2, (150, 120, 40)), ([(-30, 0), (30, 0)], 2, (150, 120, 40))])
    prop("laptop", (-80, -60, 80, 30), [([(-70, -50), (70, -50), (70, 10), (-70, 10)], (60, 64, 72)), ([(-62, -44), (62, -44), (62, 4), (-62, 4)], (120, 170, 230)), ([(-78, 10), (78, 10), (70, 22), (-70, 22)], (150, 154, 162))], grip=(-60, 16))
    prop("umbrella", (-130, -260, 130, 60), [([(-120, -170), (-60, -240), (0, -256), (60, -240), (120, -170), (60, -180), (0, -172), (-60, -180)], (60, 60, 120)), ([(-3, -250), (3, -250), (3, 30), (-3, 30)], (40, 40, 40))],
         grip=(0, 20), extra_lines=[([(0, 30), (6, 46), (16, 44)], 5, (40, 40, 40))])
    prop("guitar", (-60, -220, 60, 140), [([(-6, -210), (6, -210), (6, 0), (-6, 0)], (110, 70, 40)), ([(-50, 10), (-40, -30), (-20, -40), (20, -40), (40, -30), (50, 10), (40, 120), (0, 132), (-40, 120)], (190, 120, 60))],
         grip=(0, -100), ell=[(0, 40, 14, 14, (40, 30, 20))])
    prop("earphones", (-30, -30, 30, 30), [], extra_lines=[([(-20, -20), (-10, 10), (0, 24), (10, 10), (20, -20)], 3, (240, 240, 240))], ell=[(-20, -22, 6, 6, (245, 245, 245)), (20, -22, 6, 6, (245, 245, 245))])
    prop("id_card", (-24, -40, 24, 36), [([(-20, -20), (20, -20), (20, 32), (-20, 32)], (240, 240, 245)), ([(-14, -12), (0, -12), (0, 6), (-14, 6)], (140, 160, 200))], extra_lines=[([(0, -20), (0, -38)], 3, (200, 40, 40))])
    prop("file_folder", (-44, -54, 44, 54), [([(-38, -48), (38, -48), (38, 48), (-38, 48)], (220, 160, 70)), ([(-38, -48), (-4, -48), (2, -40), (38, -40), (38, -36), (-38, -36)], (200, 140, 60))])
    prop("gift_box", (-40, -48, 40, 40), [([(-34, -20), (34, -20), (34, 34), (-34, 34)], (200, 50, 80)), ([(-5, -20), (5, -20), (5, 34), (-5, 34)], (240, 200, 80))], extra_lines=[([(-16, -36), (0, -22), (16, -36)], 5, (240, 200, 80))])
    prop("letter", (-34, -24, 34, 24), [([(-30, -20), (30, -20), (30, 20), (-30, 20)], (245, 238, 220))], extra_lines=[([(-30, -20), (0, 4), (30, -20)], 2, (170, 150, 120))], ell=[(0, 4, 5, 5, (190, 40, 50))])
    prop("water_bottle", (-16, -56, 16, 50), [([(-12, -36), (12, -36), (12, 46), (-12, 46)], (120, 190, 230)), ([(-8, -52), (8, -52), (8, -36), (-8, -36)], (40, 60, 120))])
    prop("keys", (-24, -24, 24, 30), [], extra_lines=[([(-6, 0), (-6, 24)], 4, (200, 190, 120)), ([(6, 0), (8, 26)], 4, (190, 190, 200))], ell=[(0, -8, 12, 12, (210, 200, 130))])
    prop("cap", (-110, -60, 120, 20), [([(-96, 10), (-90, -30), (-50, -56), (20, -58), (80, -34), (96, 10)], (200, 60, 50)), ([(60, 6), (116, 10), (110, 18), (60, 16)], (170, 50, 40))], grip=(0, 10))
    prop("mic", (-18, -60, 18, 60), [([(-6, -20), (6, -20), (8, 56), (-8, 56)], (40, 40, 44))], ell=[(0, -36, 16, 18, (150, 150, 160))])
    prop("ring_box", (-24, -24, 24, 24), [([(-20, -6), (20, -6), (20, 20), (-20, 20)], (150, 30, 50)), ([(-20, -20), (20, -20), (20, -6), (-20, -6)], (120, 20, 40))], ell=[(0, -10, 6, 6, (240, 220, 120))])


def main():
    man = {}
    bodies = {}
    for rig in ("female", "male"):
        bodies[rig] = build_skin(rig, man)
    build_faces(man)
    build_hair(man)
    build_outfits(man, bodies)
    build_acc(man)
    build_props(man)
    meta = {"skeleton": SKELETON, "face": FACE, "scale": SCALE, "parts": man}
    json.dump(meta, open(os.path.join(OUT, "rig.json"), "w"), separators=(",", ":"))
    tot = sum(os.path.getsize(os.path.join(dp, f)) for dp, _, fs in os.walk(OUT) for f in fs)
    print(f"parts={len(man)} bytes={tot}")


if __name__ == "__main__":
    main()
