"""The three Numismatics coins this server does not use (cog, crown, sun) redrawn as amethyst, emerald and diamond.

Drawn from scratch at 16x16, the size Numismatics uses, so none of its art is copied. The shapes keep the gear
silhouette of the coin family (the GUI outline sprites are shape-only, so they still match and are left alone).

Run: python3 tools/art/coins.py
Writes into src/main/resources/assets/numismatics/textures/item/coin/ , which overrides Numismatics' own three
sprites because neoforge.mods.toml orders this mod AFTER numismatics.
"""
import math, os
from PIL import Image

N = 16
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../src/main/resources/assets/numismatics/textures/item/coin/')

# dark rim, body, lit body, gem sparkle
PALETTES = {
    'cog':   (0x3A1352, 0x6E2BA2, 0xA963DE, 0xE6CCFF),   # amethyst
    'crown': (0x0B3A20, 0x139044, 0x3FD47E, 0xCFFFE0),   # emerald
    'sun':   (0x15485A, 0x2E9FBA, 0x7FDCEE, 0xF2FFFF),   # diamond
}

def rgb(h, a=255): return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)
def mix(a, b, f): return tuple(int(a[i] + (b[i] - a[i]) * f) for i in range(3)) + (255,)

def coin(name):
    dark, body, lit, spark = (rgb(c) for c in PALETTES[name])
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    px = im.load()
    c = (N - 1) / 2.0
    for y in range(N):
        for x in range(N):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            th = math.atan2(dy, dx)
            rout = 6.6 + 1.0 * math.cos(8 * th)          # eight teeth, like the rest of the family
            gem = abs(dx) + abs(dy)                       # a cut stone in the middle
            if gem <= 3.6:
                if gem > 2.8: col = dark                  # the stone's own edge
                elif dy < -0.6: col = lit if dx < 0.6 else mix(body, lit, .5)      # the table and the near crown facet
                elif dy < 0.9: col = mix(body, lit, .35) if dx < 0 else body       # the girdle
                else: col = mix(dark, body, .45 if dx < 0 else .15)                # the pavilion, in shadow
                if -2.1 < dx < -0.4 and -2.1 < dy < -0.9: col = spark              # the glint
                px[x, y] = col
                continue
            if r > rout or r < 4.1: continue
            f = max(0.0, min(1.0, (-dx - dy) / (2 * rout) + 0.5))
            col = mix(mix(dark, body, 0.5), lit, f * f)
            if r > rout - 0.9 or r < 4.9: col = mix(col, dark, 0.62)   # rim and the setting round the stone
            px[x, y] = col
    return im

os.makedirs(OUT, exist_ok=True)
for n in PALETTES:
    coin(n).save(os.path.join(OUT, n + '.png'))
    print('wrote', n + '.png')
