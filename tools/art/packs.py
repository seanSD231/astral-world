"""Card pack item sprites, drawn as pixel art instead of smooth vector art.

The old ones were 188x188 with soft gradients and small type: at the size an item is actually
drawn they turned to mush. These are 64x64, hard-edged, four tones to a colour, no text, with
the pathway's own symbol stamped in the middle so you can tell them apart at a glance.

Run from the repo root:  python3 tools/art/packs.py
"""
import os
from PIL import Image

OUT = "src/main/resources/assets/astral_world/textures/item"
SYM = "src/main/resources/assets/astral_world/textures/gui/tarot"
S = 64

INK = (18, 15, 24, 255)
GOLD = [(90, 64, 18, 255), (156, 114, 36, 255), (214, 170, 62, 255), (246, 224, 140, 255)]
SILVER = [(60, 62, 72, 255), (108, 112, 126, 255), (168, 174, 190, 255), (232, 238, 248, 255)]

PATH_RGB = {
    "spectator": (0x55, 0xFF, 0xFF), "bard": (0xFF, 0xFF, 0x55), "warrior": (0xFF, 0xE0, 0x55),
    "sailor": (0x55, 0x80, 0xFF), "marauder": (0x44, 0x66, 0xE0), "apprentice": (0x77, 0x88, 0xFF),
    "seer": (0xAA, 0x40, 0xCC), "mystery_pryer": (0x88, 0x30, 0xBB), "prisoner": (0x99, 0x44, 0xAA),
    "secrets_supplicant": (0xCC, 0x30, 0x30), "hunter": (0xAA, 0x22, 0x22), "criminal": (0x99, 0x33, 0x44),
    "assassin": (0xFF, 0x66, 0xDD), "corpse_collector": (0x55, 0xDD, 0x66), "planter": (0x66, 0xCC, 0x44),
    "apothecary": (0x22, 0xAA, 0x55), "lawyer": (0x33, 0x55, 0xCC), "savant": (0xFF, 0xB0, 0x30),
    "arbiter": (0xFF, 0x99, 0x22), "reader": (0xE8, 0xEC, 0xF4), "monster": (0xAA, 0xAA, 0xB4),
    "sleepless": (0x7A, 0x7A, 0x88), "ancient_one": (0x6A, 0x3C, 0xC0),
}


def ramp(rgb):
    """Four tones from one colour: the whole sprite is built out of these."""
    r, g, b = rgb
    out = []
    for k in (0.32, 0.58, 0.85, 1.12):
        out.append((min(255, int(r * k)), min(255, int(g * k)), min(255, int(b * k)), 255))
    return out


SYM_N = 22


def sym_stamp(path):
    """
    The pathway symbol as a small relief. The source is a gold emblem on a filled gold disc, so a
    plain brightness cut just gives back the disc: what reads at this size is the emblem's own
    shading, so each pixel is placed in one of three bands between the darkest and brightest of it.
    """
    f = os.path.join(SYM, f"sym_{path}.png")
    if not os.path.exists(f):
        return None
    im = Image.open(f).convert("RGBA").resize((SYM_N, SYM_N), Image.BOX)
    px = im.load()
    lum, lo, hi = {}, 255, 0
    for y in range(SYM_N):
        for x in range(SYM_N):
            r, g, b, a = px[x, y]
            if a < 120:
                continue
            L = (r * 299 + g * 587 + b * 114) // 1000
            lum[(x, y)] = L
            lo = min(lo, L)
            hi = max(hi, L)
    if not lum or hi - lo < 8:
        return None
    out = {}
    for k, L in lum.items():
        t = (L - lo) / (hi - lo)
        out[k] = 1 if t < .38 else 2 if t < .72 else 3
    return out


def pack(path, rarity):
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = im.load()

    def put(x, y, c):
        if 0 <= x < S and 0 <= y < S:
            d[x, y] = c

    def rect(x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                put(x, y, c)

    body = ramp(PATH_RGB.get(path, (150, 150, 160)))
    trim = SILVER if rarity == "" else GOLD
    X0, X1, Y0, Y1 = 14, 50, 4, 60

    rect(X0 - 2, Y0 - 1, X1 + 2, Y1 + 1, INK)                       # the outline, drawn first
    for y in range(Y0, Y1):                                          # the foil, four flat bands
        k = (y - Y0) / (Y1 - Y0)
        tone = body[0] if k < .18 else body[1] if k < .52 else body[2] if k < .84 else body[1]
        rect(X0, y, X1, y + 1, tone)

    cx0, cy0 = (X0 + X1) // 2, (Y0 + Y1) // 2
    for y in range(Y0 + 8, Y1 - 8):                                  # one hard shine stripe, kept off the medallion
        x = X0 + 5 + (y - Y0) // 3
        for k in range(3):
            if (x + k - cx0) ** 2 + (y - cy0) ** 2 > 210:
                put(x + k, y, body[3])
        if (x + 3 - cx0) ** 2 + (y - cy0) ** 2 > 210:
            put(x + 3, y, body[2])

    for x in range(X0, X1):                                          # the crimped seal at the top
        t = (x - X0) % 4
        rect(x, Y0, x + 1, Y0 + (5 if t < 2 else 3), trim[1])
        put(x, Y0 + (5 if t < 2 else 3), INK)
    rect(X0, Y0 + 6, X1, Y0 + 7, trim[0])

    rect(X0, Y1 - 7, X1, Y1 - 6, trim[0])                            # and the one at the foot
    for x in range(X0, X1):
        t = (x - X0) % 4
        rect(x, Y1 - (5 if t < 2 else 3), x + 1, Y1, trim[1])

    rect(X0, Y0 + 8, X0 + 1, Y1 - 8, trim[2])                        # side rails
    rect(X1 - 1, Y0 + 8, X1, Y1 - 8, trim[0])

    cx, cy = (X0 + X1) // 2, (Y0 + Y1) // 2
    for y in range(-14, 15):                                         # the dark medallion the symbol sits on
        for x in range(-14, 15):
            r2 = x * x + y * y
            if r2 <= 196:
                put(cx + x, cy + y, INK if r2 > 169 else (26, 22, 34, 255))
    for y in range(-14, 15):                                         # a thin ring of trim round it
        for x in range(-14, 15):
            r2 = x * x + y * y
            if 150 <= r2 <= 169:
                put(cx + x, cy + y, trim[1])

    stamp = sym_stamp(path)
    if stamp:
        o = SYM_N // 2
        for (x, y), band in stamp.items():
            put(cx - o + x, cy - o + y, trim[band])
    else:
        for y in range(-8, 9):                                       # no symbol for this one: a plain star
            for x in range(-8, 9):
                if abs(x) + abs(y) <= 8 and (abs(x) < 2 or abs(y) < 2):
                    put(cx + x, cy + y, trim[2])

    for i in range(3):                                               # three pips: three cards
        px_ = X0 + 9 + i * 9
        rect(px_, Y1 - 13, px_ + 5, Y1 - 10, trim[2])
        put(px_, Y1 - 13, INK)
        put(px_ + 4, Y1 - 13, INK)

    if rarity == "shiny":
        for (sx, sy) in ((X0 + 4, Y0 + 11), (X1 - 6, Y0 + 17), (X0 + 7, Y1 - 19), (X1 - 8, Y1 - 15)):
            put(sx, sy, GOLD[3]); put(sx - 1, sy, GOLD[2]); put(sx + 1, sy, GOLD[2])
            put(sx, sy - 1, GOLD[2]); put(sx, sy + 1, GOLD[2])
    if rarity == "rainbow":
        hue = [(255, 90, 90, 255), (255, 170, 60, 255), (250, 240, 90, 255), (110, 230, 120, 255),
               (90, 180, 255, 255), (180, 120, 255, 255)]
        for y in range(Y0 + 8, Y1 - 8):
            c = hue[(y - Y0) // 3 % len(hue)]
            put(X0, y, c)
            put(X1 - 1, y, c)
        for x in range(X0, X1):
            c = hue[(x - X0) // 3 % len(hue)]
            put(x, Y0 + 6, c)
            put(x, Y1 - 7, c)
    return im


def main():
    made = 0
    for f in sorted(os.listdir(OUT)):
        if not f.endswith("_pack.png"):
            continue
        name = f[:-len("_pack.png")]
        rarity = ""
        for r in ("shiny", "rainbow"):
            if name.endswith("_" + r):
                rarity = r
                name = name[:-len(r) - 1]
        pack(name, rarity).save(os.path.join(OUT, f))
        made += 1
    print(f"{made} pack sprites")


if __name__ == "__main__":
    main()
