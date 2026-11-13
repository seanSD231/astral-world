"""The three Numismatics coins this server does not use (cog, crown, sun) redrawn as amethyst, emerald and diamond.

Drawn from scratch at 16x16, the size Numismatics uses, so none of its art is copied. They are round struck coins,
to sit beside the LOTM bronze, silver and gold coins rather than the Numismatics gears, each with its stone cut
into the face. (Numismatics' GUI outline sprites are gear-shaped and are left alone; they are only slot hints.)

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
            if r > 6.5: continue                          # a round blank with a little margin, as the LOTM coins have
            f = max(0.0, min(1.0, (-dx - dy) / 11.0 + 0.5))
            col = mix(mix(dark, body, .55), lit, f * f)
            if (x + y) % 2 == 0: col = mix(col, lit, .14)             # a little dither, like a struck face
            if r > 5.4: col = dark                                    # a hard rim all the way round
            gem = abs(dx) + abs(dy)                       # the stone cut into the face
            if gem <= 3.1:
                if gem > 2.3: col = dark                                           # its own outline
                elif dy < -0.7: col = spark if dx < 0.4 else lit                   # the table, catching the light
                elif dy < 0.8: col = mix(body, lit, .55) if dx < 0 else mix(body, lit, .2)
                else: col = mix(dark, body, .55 if dx < 0 else .25)                # the pavilion, in shadow
            px[x, y] = col
    return im

os.makedirs(OUT, exist_ok=True)
OUT2 = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../src/main/resources/resourcepacks/gem_coins/assets/numismatics/textures/item/coin/')
os.makedirs(OUT2, exist_ok=True)
for n in PALETTES:
    coin(n).save(os.path.join(OUT, n + '.png'))
    coin(n).save(os.path.join(OUT2, n + '.png'))   # the always-on-top built-in pack (AstralMod registers it)
    print('wrote', n + '.png')
