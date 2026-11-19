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
from PIL import Image, ImageFilter, ImageDraw, ImageFont

SERIF = '/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf'
SANS = '/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf'

# The inside of each lettered plate, in the 564px space the art was drawn in -- the lettering only,
# well clear of the gold frame round it. Take in the frame and it is read as part of the text: the
# whole plate is then erased and the new words are set at the size of the plate.
TITLE_BOX, SUB_BOX, FOOT_BOX = (128, 88, 448, 129), (120, 433, 444, 463), (231, 478, 351, 499)

PRETTY = {'corpse_collector': 'CORPSE COLLECTOR', 'mystery_pryer': 'MYSTERY PRYER',
          'secrets_supplicant': 'SECRETS SUPPLICANT'}

HERE = os.path.dirname(os.path.abspath(__file__))
GUI = os.path.join(HERE, '../../src/main/resources/assets/astral_world/textures/gui/tarot')
SRC = os.path.join(HERE, 'pack_src')   # the hand-made 564px art, kept apart so this can be run again:
                                       # reading the pouches it had already written doubled everything
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


def words(path):
    return PRETTY.get(path, path.replace('_', ' ').upper())


def plate_text(rgb, alpha, box):
    """Where the lettering is in a plate, and what colour it is: the bright pixels inside it."""
    x0, y0, x1, y1 = box
    sub = rgb[y0:y1, x0:x1]
    lum = sub.max(-1)
    if lum.size == 0:
        return None
    hi = np.percentile(lum, 97)
    if hi < .35:
        return None
    m = lum > (hi * .62)
    if m.sum() < 60:
        return None
    ys, xs = np.nonzero(m)
    col = np.percentile(sub[m].reshape(-1, 3), 80, axis=0)
    return (x0 + xs.min(), y0 + ys.min(), x0 + xs.max() + 1, y0 + ys.max() + 1), col, m


def draw_text(img, box, text, col, serif):
    """Set `text` into `box` at full resolution, in the colour the old lettering was."""
    x0, y0, x1, y1 = box
    w, hgt = x1 - x0, y1 - y0
    if w < 8 or hgt < 6:
        return
    path = SERIF if serif else SANS
    size = max(8, int(hgt * 1.62))
    while size > 8:
        f = ImageFont.truetype(path, size)
        bb = f.getbbox(text)
        if bb[2] - bb[0] <= w and bb[3] - bb[1] <= hgt * 1.08:
            break
        size -= 1
    f = ImageFont.truetype(path, size)
    bb = f.getbbox(text)
    cx = x0 + (w - (bb[2] - bb[0])) / 2 - bb[0]
    cy = y0 + (hgt - (bb[3] - bb[1])) / 2 - bb[1]
    d = ImageDraw.Draw(img)
    r, g2, b = [int(max(0, min(255, c * 255))) for c in col]
    o = max(1, int(round(size * .07)))
    d.text((cx, cy), text, font=f, fill=(10, 8, 14, 235), stroke_width=o, stroke_fill=(10, 8, 14, 235))
    d.text((cx, cy), text, font=f, fill=(r, g2, b, 255))


def retext(big, rgb, alpha, stem, mid):
    """Erase the soft lettering and set it again at the size the texture is really drawn at.

    The pouches carry about 280px of real detail, so every word on them was a three times
    upscale of something blurred. The strings are known from the file name, the plates are at
    fixed places, and the colour is read off what was there."""
    name = stem[len('pack_'):]
    shiny, rainbow = name.endswith('_shiny'), name.endswith('_rainbow')
    special = name in ('admin', 'ancient_one')
    title = ('SHINY PACK' if shiny else 'RAINBOW PACK' if rainbow else
             'ANCIENT ONE' if name == 'ancient_one' else 'ADMIN PACK' if name == 'admin' else 'TAROT PACK')
    sub = 'BEARER OF SINS' if name == 'admin' else None if name == 'ancient_one' else words(mid) if mid else None
    foot = '1 CARD' if name == 'admin' else '3 CARDS'
    jobs = [(TITLE_BOX, title, not (shiny or rainbow or name == 'ancient_one')),
            (SUB_BOX, sub, True), (FOOT_BOX, foot, True)]

    h, w = alpha.shape
    hole = np.zeros((h, w), np.float32)
    found = []
    for box, text, serif in jobs:
        if not text:
            continue
        got = plate_text(rgb, alpha, box)
        if got is None:
            continue
        bbox, col, m = got
        x0, y0, x1, y1 = box
        hole[y0:y1, x0:x1] = np.maximum(hole[y0:y1, x0:x1], m.astype(np.float32))
        found.append((bbox, text, col, serif))
    if not found:
        return big
    # widen the hole a little so the old letters' dark outlines go with them
    hole = np.asarray(Image.fromarray((hole * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3))).astype(np.float32) / 255.
    clean = inpaint(rgb, hole, rounds=40)
    patch = np.asarray(Image.fromarray((np.clip(clean, 0, 1) * 255).astype(np.uint8))
                       .resize((w * K, h * K), Image.LANCZOS)).astype(np.float32) / 255.
    spread = np.asarray(Image.fromarray((hole * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3))
                        .resize((w * K, h * K), Image.LANCZOS)).astype(np.float32)[..., None] / 255.
    big = big * (1 - spread) + patch * spread

    img = Image.fromarray((np.clip(big, 0, 1) * 255).astype(np.uint8), 'RGB').convert('RGBA')
    for bbox, text, col, serif in found:
        draw_text(img, tuple(int(v * K) for v in bbox), text, col, serif)
    return np.asarray(img.convert('RGB')).astype(np.float32) / 255.


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

    big = retext(big, rgb, alpha, stem, mid)
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
    files = sorted(glob.glob(os.path.join(SRC, 'pack_*.png')))
    if only:
        files = [f for f in files if only in os.path.basename(f)]
    for f in files:
        print('rebuilt', rebuild(f, os.path.join(GUI, os.path.basename(f))))
