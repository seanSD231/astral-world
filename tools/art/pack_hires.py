"""Re-authors the 68 pack pouches at 1128px.

The committed pouches are 564px but carry only about 280px of real detail (halving and
doubling one changes it by less than 1%), and the opening screen draws them at 900-1200
physical pixels, so everything on them is a 3x upscale of something soft. The parts that
matter most are the 22 pathway symbols round the ring and the big one in the middle, and
those we have at full resolution: LOTM's own 128px art, redrawn to 384 by sharpen_syms.py.

So: take the pouch, erase the old blurry symbols by diffusing the panel back over them,
double it with a good filter, and paint the sharp symbols in at their own size. The frame,
the plates and the lettering are left as the doubled art with a light vector-trace pass to
firm up their edges -- they are big shapes and they hold up.

The geometry was measured off the art itself by correlating each symbol against it:
  ring      22 pathways in alphabetical order, slot k at -90 + k * 360/22 degrees,
            radius 117, size 42, about the centre (282, 285) of the 564px art
  medallion the pack's own pathway at 136, on the same centre

Run: python3 tools/art/pack_hires.py          (all of them)
     python3 tools/art/pack_hires.py assassin (one, and a preview beside it)
"""
import os, sys, glob, math
import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
GUI = os.path.join(HERE, '../../src/main/resources/assets/astral_world/textures/gui/tarot')
SYM = GUI                      # the 384px sharpened symbols live beside the pouches
K = 2                          # work at 1128, then settle at OUT_SZ
OUT_SZ = 1024                  # what gets written: a power of two, and still above what the
                               # opening screen draws (about 900-1200 physical pixels)

PATHS = ['apothecary', 'apprentice', 'arbiter', 'assassin', 'bard', 'corpse_collector',
         'criminal', 'hunter', 'lawyer', 'marauder', 'monster', 'mystery_pryer', 'planter',
         'prisoner', 'reader', 'sailor', 'savant', 'secrets_supplicant', 'seer', 'sleepless',
         'spectator', 'warrior']

CX, CY = 282.0, 285.0          # the middle of the ring, in 564 space
RING_R, RING_SZ = 117.0, 36.0
MED_SZ, MED_R = 136.0, 76.0   # the emblem, and the gold rim the art draws round it


def load(p):
    return np.asarray(Image.open(p).convert('RGBA')).astype(np.float32) / 255.0


def save(a, p):
    """Written at OUT_SZ on a 256 colour palette. These are flat emblem art, so a palette
    costs about a hundredth of a colour of accuracy and five sixths of the file: truecolour
    at this size would have put sixty megabytes of pouches in the jar."""
    im = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), 'RGBA')
    if im.size[0] != OUT_SZ:
        im = im.resize((OUT_SZ, OUT_SZ), Image.LANCZOS)
    im.quantize(colors=256, method=Image.FASTOCTREE, dither=Image.NONE).save(p, optimize=True)


def slot_xy(k):
    ang = math.radians(-90 + k * 360.0 / 22)
    return CX + math.cos(ang) * RING_R, CY + math.sin(ang) * RING_R


GOLD_LO = np.array([.26, .17, .03], np.float32)   # the shiny pouches tint everything on them
GOLD_HI = np.array([1.0, .84, .44], np.float32)   # to one gold, from a near-black well


def sym_rgba(name, size, shiny=False):
    """The symbol as it is: its emblem over its own dark backing roundel. Laying it on as
    light instead (colour times alpha, screened) throws the roundel away and the ring reads
    as a row of glowing blobs with nothing holding them."""
    im = Image.open(os.path.join(SYM, 'sym_%s.png' % name)).convert('RGBA')
    im = im.resize((size, size), Image.LANCZOS)
    t = np.asarray(im).astype(np.float32) / 255.0
    # the roundel is held back to a third: at full strength the ring reads as a row of dark
    # coins, and the pouch only ever had a hint of one under each emblem
    lum = t[..., :3].max(-1)
    if shiny:
        t[..., :3] = GOLD_LO + (GOLD_HI - GOLD_LO) * lum[..., None]
    t[..., 3] = t[..., 3] * np.clip(.30 + 2.2 * lum, 0, 1)
    return t


def disc(h, w, cx, cy, r, soft=1.5):
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    return np.clip((r - d) / soft + .5, 0, 1)


def inpaint(rgb, mask, rounds=70):
    """Diffuse what is around a hole back into it. The panel under a symbol is a smooth
    gradient with a sheen across it, so a few hundred rounds of blur-and-restore rebuild
    it well enough that nothing shows once the new symbol is painted over the top."""
    out = rgb.copy()
    m = mask[..., None]
    keep = 1 - m
    for i in range(rounds):
        im = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))
        bl = np.asarray(im.filter(ImageFilter.GaussianBlur(3.0))).astype(np.float32) / 255.0
        out = rgb * keep + bl * m
    return out


def trace(rgb, amount=.55, radius=1.1):
    """A light vector-trace: steepen the edges that survived the doubling."""
    im = Image.fromarray((np.clip(rgb, 0, 1) * 255).astype(np.uint8))
    bl = np.asarray(im.filter(ImageFilter.GaussianBlur(radius))).astype(np.float32) / 255.0
    return np.clip(rgb + (rgb - bl) * amount, 0, 1)


def over(base, tile, cx, cy, opacity=1.0):
    """Alpha-composite a small RGBA tile onto the canvas, centred on (cx, cy)."""
    H, W = base.shape[:2]
    s = tile.shape[0]
    x0, y0 = int(round(cx - s / 2.0)), int(round(cy - s / 2.0))
    xs, ys, xe, ye = max(0, x0), max(0, y0), min(W, x0 + s), min(H, y0 + s)
    if xe <= xs or ye <= ys:
        return base
    t = tile[ys - y0:ye - y0, xs - x0:xe - x0]
    a = t[..., 3:4] * opacity
    base[ys:ye, xs:xe] = base[ys:ye, xs:xe] * (1 - a) + t[..., :3] * a
    return base


def pathway_of(stem):
    """Which pathway's symbol sits in the middle of this pouch."""
    name = stem[len('pack_'):]
    for suf in ('_rainbow', '_shiny'):
        if name.endswith(suf):
            name = name[:-len(suf)]
    if name == 'admin':
        name = 'secrets_supplicant'      # the admin pouch was built from the Supplicant's
    return name if name in PATHS else None


def rebuild(src, dst):
    stem = os.path.splitext(os.path.basename(src))[0]
    mid = pathway_of(stem)
    shiny = stem.endswith('_shiny')
    ringed = 'ancient_one' not in stem        # the Ancient One's pouch is a nebula: no ring, no medallion
    a = load(src)
    rgb, alpha = a[..., :3], a[..., 3]
    h, w = alpha.shape

    # ---- erase the old soft symbols -------------------------------------------------
    hole = np.zeros((h, w), np.float32)
    for k in range(22 if ringed else 0):
        x, y = slot_xy(k)
        hole = np.maximum(hole, disc(h, w, x, y, RING_SZ * .46, 2.0))
    if mid and ringed:
        hole = np.maximum(hole, disc(h, w, CX, CY, MED_R + 6, 2.5))
    clean = inpaint(rgb, hole * (alpha > .5))

    # ---- double it ------------------------------------------------------------------
    big = np.asarray(Image.fromarray((np.clip(clean, 0, 1) * 255).astype(np.uint8))
                     .resize((w * K, h * K), Image.LANCZOS)).astype(np.float32) / 255.0
    bigA = np.asarray(Image.fromarray((alpha * 255).astype(np.uint8))
                      .resize((w * K, h * K), Image.LANCZOS)).astype(np.float32) / 255.0
    big = trace(big)
    H, W = bigA.shape

    # ---- the medallion: a crisp disc and gold ring, and the symbol at its own size ----
    if mid and ringed:
        # the medallion's own well: near black, taken from the darkest of what was there,
        # with the gold rim the art draws round it
        # the well's own colour, read from the band between the emblem and the gold rim
        band = (disc(h, w, CX, CY, MED_R * .93, 1.0) - disc(h, w, CX, CY, MED_R * .78, 1.0)) > .5
        dark = np.median(rgb[band].reshape(-1, 3), axis=0)
        ring_c = np.array([.86, .70, .30], np.float32)
        rr = MED_R * K
        d_in = disc(H, W, CX * K, CY * K, rr, 2.0)[..., None]
        d_rim = (disc(H, W, CX * K, CY * K, rr + 2.2 * K, 1.5)
                 - disc(H, W, CX * K, CY * K, rr - .9 * K, 1.5))[..., None]
        big = big * (1 - d_in) + dark * d_in
        big = over(big, sym_rgba(mid, int(MED_SZ * K), shiny), CX * K, CY * K, 1.0)
        big = big * (1 - d_rim) + ring_c * d_rim

    # ---- the ring --------------------------------------------------------------------
    sz = int(RING_SZ * K)
    for k in range(22 if ringed else 0):
        x, y = slot_xy(k)
        big = over(big, sym_rgba(PATHS[k], sz, shiny), x * K, y * K, 1.0)
        if mid and PATHS[k] == mid:        # the pack's own pathway is circled, as before
            r2 = RING_SZ * .66 * K
            ann = (disc(H, W, x * K, y * K, r2 + 1.7 * K, 1.3)
                   - disc(H, W, x * K, y * K, r2, 1.3))[..., None]
            big = big * (1 - ann * .9) + np.array([.86, .70, .30], np.float32) * ann * .9

    save(np.concatenate([big, bigA[..., None]], -1), dst)
    return stem


def place(tile, H, W, cx, cy):
    """Drop a small premultiplied tile onto a black HxW canvas, centred on (cx, cy)."""
    out = np.zeros((H, W, 3), np.float32)
    s = tile.shape[0]
    x0, y0 = int(round(cx - s / 2.0)), int(round(cy - s / 2.0))
    xs, ys = max(0, x0), max(0, y0)
    xe, ye = min(W, x0 + s), min(H, y0 + s)
    if xe <= xs or ye <= ys:
        return out
    out[ys:ye, xs:xe] = tile[ys - y0:ye - y0, xs - x0:xe - x0]
    return out


if __name__ == '__main__':
    only = sys.argv[1] if len(sys.argv) > 1 else None
    files = sorted(glob.glob(os.path.join(GUI, 'pack_*.png')))
    if only:
        files = [f for f in files if only in os.path.basename(f)]
    for f in files:
        print('rebuilt', rebuild(f, f))
