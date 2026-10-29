#!/usr/bin/env python3
"""
castgen — turns generated painted character frames into the v4 cast kit (assets/cast2/<id>/).

Input (in RAW dir, all 768x1376 on flat chroma green, produced by image edits of ONE base so they are pixel-aligned):
  <id>_neutral.png                 base pose
  <id>_<expr>.png                  same image, only the face changed
  <id>_<expr>_mouth.png            same as <expr>, mouth open (lip-sync)
  <id>_<expr>_blink.png            same as <expr>, eyes closed
Output:
  base.webp       keyed RGBA full body (drawn once; never changes -> no popping)
  <expr>.webp     face-rect crop of the expression (feathered alpha), drawn over the base
  <expr>_mouth.webp / <expr>_blink.webp   small feathered patches (mouth band / eye band only)
  manifest.json   rects for every patch + face anchor
Because the body never changes and every patch is feathered over pixel-identical surroundings, crossfading
expressions, blinking and lip-sync can't misalign or pop.

usage: python3 castgen.py RAW_DIR OUT_DIR [ids...]
"""
import json, os, sys
import numpy as np
from PIL import Image

CELL = 16


def load(p):
    return np.asarray(Image.open(p).convert("RGB")).astype(np.float32)


def key(rgb):
    """Chroma-key flat green -> RGBA with soft edges and despill."""
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    m = np.maximum(r, b)
    green = g - m  # >0 where greenish
    a = np.clip(1.0 - (green - 30.0) / 70.0, 0, 1)
    a[(g > 200) & (r < 90) & (b < 90)] = 0
    out = rgb.copy()
    spill = (green > 0) & (a > 0)
    out[..., 1] = np.where(spill, np.minimum(g, m + 8), g)
    return np.dstack([out, a * 255.0])


def diff_cells(a, b, thr=10.0):
    d = np.abs(a - b).mean(2)
    H, W = d.shape
    g = d[: H // CELL * CELL, : W // CELL * CELL].reshape(H // CELL, CELL, W // CELL, CELL).mean((1, 3))
    return g > thr, d


def bbox_cells(mask, clip=None):
    ys, xs = np.nonzero(mask)
    if clip is not None:
        x0, y0, x1, y1 = clip
        sel = (xs * CELL >= x0) & (xs * CELL < x1) & (ys * CELL >= y0) & (ys * CELL < y1)
        ys, xs = ys[sel], xs[sel]
    if len(xs) == 0:
        return None
    return [int(xs.min() * CELL), int(ys.min() * CELL), int((xs.max() + 1) * CELL), int((ys.max() + 1) * CELL)]


def largest_blob_box(mask, clip):
    """Bounding box of the biggest 4-connected cell blob inside clip (ignores stray noise cells)."""
    x0, y0, x1, y1 = clip
    m = mask.copy()
    m[:, : x0 // CELL] = False; m[:, x1 // CELL:] = False; m[: y0 // CELL, :] = False; m[y1 // CELL:, :] = False
    seen = np.zeros_like(m); best = None
    for sy, sx in zip(*np.nonzero(m)):
        if seen[sy, sx]:
            continue
        stack = [(sy, sx)]; seen[sy, sx] = True; cells = []
        while stack:
            y, x = stack.pop(); cells.append((y, x))
            for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                if 0 <= ny < m.shape[0] and 0 <= nx < m.shape[1] and m[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True; stack.append((ny, nx))
        if best is None or len(cells) > len(best):
            best = cells
    if not best:
        return None
    ys = [c[0] for c in best]; xs = [c[1] for c in best]
    return [int(min(xs)) * CELL, int(min(ys)) * CELL, (int(max(xs)) + 1) * CELL, (int(max(ys)) + 1) * CELL]


def pad(r, p, W, H):
    return [int(max(0, r[0] - p)), int(max(0, r[1] - p)), int(min(W, r[2] + p)), int(min(H, r[3] + p))]


def feather(h, w, f):
    y = np.minimum(np.arange(h), np.arange(h)[::-1])[:, None]
    x = np.minimum(np.arange(w), np.arange(w)[::-1])[None, :]
    t = np.clip(np.minimum(y, x) / float(f), 0, 1)
    return t * t * (3 - 2 * t)


def save_patch(rgba, r, path, f=14):
    x0, y0, x1, y1 = r
    p = rgba[y0:y1, x0:x1].copy()
    p[..., 3] *= feather(y1 - y0, x1 - x0, f)
    Image.fromarray(p.clip(0, 255).astype(np.uint8), "RGBA").save(path, "WEBP", quality=88, method=6)


def build(raw, out, cid):
    base = load(f"{raw}/{cid}_neutral.png")
    H, W = base.shape[:2]
    exprs = ["neutral"] + sorted({f[len(cid) + 1:-4] for f in os.listdir(raw)
                                  if f.startswith(cid + "_") and f.endswith(".png") and not f.endswith(("_mouth.png", "_blink.png"))
                                  and f != f"{cid}_neutral.png" and not (cid == "player" and False)})
    exprs = [e for e in exprs if "_" not in e or e == "neutral"]
    os.makedirs(out, exist_ok=True)
    kb = key(base)
    alpha = kb[..., 3] > 8
    ys, xs = np.nonzero(alpha)
    body = [int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1]
    Image.fromarray(kb.clip(0, 255).astype(np.uint8), "RGBA").save(f"{out}/base.webp", "WEBP", quality=86, method=6)
    # face rect: built around the neutral blink (eyes) + mouth diffs, which are tightly localised, then expanded to
    # cover brows/cheeks/chin. Expression edits sometimes also nudge hair or wet strands outside the face; those
    # changes are deliberately ignored so every expression patch stays small and the body never changes.
    imgs = {}
    for e in exprs:
        for suf in ["", "_mouth", "_blink"]:
            p = f"{raw}/{cid}_{e}{suf}.png"
            if os.path.exists(p) and not (e == "neutral" and suf == ""):
                imgs[e + suf] = load(p)
    head_clip = [0, 0, W, int(H * 0.55)]
    def box_of(k, thr=10.0, allblobs=False):
        if k not in imgs:
            return None
        m, _ = diff_cells(base, imgs[k], thr)
        return bbox_cells(m, head_clip) if allblobs else largest_blob_box(m, head_clip)
    eyes = box_of("neutral_blink", allblobs=True)
    mouth = box_of("neutral_mouth")
    if eyes and mouth:
        core = [min(eyes[0], mouth[0]), eyes[1], max(eyes[2], mouth[2]), mouth[3]]
    elif eyes:
        hgt = eyes[3] - eyes[1]
        core = [eyes[0], eyes[1], eyes[2], eyes[3] + int(hgt * 2.2)]
    else:
        core = [int(W * 0.3), int(H * 0.15), int(W * 0.7), int(H * 0.4)]
    cw, ch = core[2] - core[0], core[3] - core[1]
    union = [core[0] - int(cw * 0.22), core[1] - int(ch * 0.30), core[2] + int(cw * 0.22), core[3] + int(ch * 0.18)]
    face = pad(union, 24, W, H)
    man = {"w": W, "h": H, "body": body, "face": face, "expr": {}}
    for e in exprs:
        src = base if e == "neutral" else imgs[e]
        ent = {}
        if e != "neutral":
            save_patch(key(src), face, f"{out}/{e}.webp", f=20)
        mouth_box = None
        if e + "_mouth" in imgs:
            m, _ = diff_cells(src, imgs[e + "_mouth"], 8.0)
            fx0, fy0, fx1, fy1 = face
            mid = fy0 + (fy1 - fy0) * 0.45
            bb = largest_blob_box(m, [fx0, int(mid), fx1, fy1])
            if bb:
                mouth_box = pad(bb, 14, W, H)
                save_patch(key(imgs[e + "_mouth"]), mouth_box, f"{out}/{e}_mouth.webp", f=10)
                ent["mouth"] = mouth_box
        if e + "_blink" in imgs:
            m, _ = diff_cells(src, imgs[e + "_blink"], 10.0)
            fx0, fy0, fx1, fy1 = face
            lim = (mouth_box[1] - 6) if mouth_box else int(fy0 + (fy1 - fy0) * 0.62)
            bb = bbox_cells(m, [fx0, fy0, fx1, lim])  # both eyes (separate blobs)
            if bb:
                bb = pad(bb, 12, W, H); bb[3] = min(bb[3], lim)
                save_patch(key(imgs[e + "_blink"]), bb, f"{out}/{e}_blink.webp", f=10)
                ent["blink"] = bb
        man["expr"][e] = ent
    # head anchor (centre of face rect) for camera framing
    man["head"] = [(face[0] + face[2]) / 2, (face[1] + face[3]) / 2]
    json.dump(man, open(f"{out}/manifest.json", "w"), indent=1)
    sizes = sum(os.path.getsize(f"{out}/{f}") for f in os.listdir(out))
    print(f"{cid}: exprs={exprs} face={face} files={len(os.listdir(out))} bytes={sizes}")


if __name__ == "__main__":
    raw, outroot = sys.argv[1], sys.argv[2]
    ids = sys.argv[3:]
    for cid in ids:
        build(raw, f"{outroot}/{cid}", cid)
