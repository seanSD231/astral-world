"""The tarot pack faces, drawn from nothing at full size.

Every pack until now was a 564-pixel original blown up to 1024, and no amount of sharpening puts
back detail that was never there: the lettering went soft and the ring of symbols went to mush.
This draws the whole face at 1024 instead -- frame, banner, type, ring, medallion and plates -- so
every edge is as crisp as the canvas allows.

The layout is deliberately the same as the old one, down to the centre and the ring radius, because
the pack is drawn into a fixed rectangle in the GUI and anything that moved would have to be
chased in the Java as well. Only the quality changes.

    python3 tools/art/pack_new.py            # every pack, all three finishes
    python3 tools/art/pack_new.py seer       # one, and a preview to /tmp
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = 'src/main/resources/assets/astral_world/textures/gui/tarot'
N = 1024

# the old 564-space geometry, carried over so the GUI needs no changes
K = N / 564.0
CX, CY = int(282 * K), int(285 * K)
RING_R = int(117 * K)
RING_SZ = int(42 * K)
MED_SZ = int(136 * K)
MED_R = int(76 * K)
TITLE_BOX = (int(128 * K), int(84 * K), int(448 * K), int(132 * K))
SUB_BOX = (int(120 * K), int(428 * K), int(444 * K), int(468 * K))
FOOT_BOX = (int(228 * K), int(474 * K), int(354 * K), int(502 * K))

GOLD = (226, 186, 96)
GOLD_LIT = (252, 232, 170)
GOLD_DEEP = (138, 102, 38)
INK = (14, 22, 32)

# the twenty-two, in the order the ring runs: alphabetical, from the top
PATHWAYS = ['apothecary', 'apprentice', 'arbiter', 'assassin', 'bard', 'corpse_collector',
            'criminal', 'hunter', 'lawyer', 'marauder', 'monster', 'mystery_pryer', 'planter',
            'prisoner', 'reader', 'sailor', 'savant', 'secrets_supplicant', 'seer', 'sleepless',
            'spectator', 'warrior']

COLOUR = {'spectator': (0x55, 0xFF, 0xFF), 'bard': (0xFF, 0xFF, 0x55), 'warrior': (0xFF, 0x8A, 0x1E),
          'sailor': (0x55, 0x55, 0xFF), 'apprentice': (0x6F, 0xA2, 0xFF), 'marauder': (0x9B, 0x82, 0xB8),
          'mystery_pryer': (0xAA, 0x00, 0xAA), 'prisoner': (0xAA, 0x00, 0xAA), 'seer': (0xC5, 0x66, 0xE6),
          'secrets_supplicant': (0xAA, 0x00, 0x00), 'hunter': (0xAA, 0x00, 0x00), 'criminal': (0xAA, 0x00, 0x00),
          'assassin': (0xFF, 0x55, 0xFF), 'corpse_collector': (0x55, 0xFF, 0x55), 'planter': (0x55, 0xFF, 0x55),
          'apothecary': (0x00, 0xAA, 0x00), 'lawyer': (0x2A, 0x2A, 0xCC), 'savant': (0xFF, 0xAA, 0x00),
          'arbiter': (0xFF, 0xAA, 0x00), 'reader': (0xFF, 0xFF, 0xFF), 'monster': (0xAA, 0xAA, 0xAA),
          'sleepless': (0x77, 0x77, 0x77), 'ancient_one': (0xE0, 0xC0, 0x60)}

TITLES = {'secrets_supplicant': 'SECRETS SUPPLICANT', 'corpse_collector': 'CORPSE COLLECTOR',
          'mystery_pryer': 'MYSTERY PRYER', 'ancient_one': 'ANCIENT ONE'}

FONT_DIRS = ['/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf',
             '/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf',
             '/usr/share/fonts/truetype/freefont/FreeSerifBold.ttf']


def font(size):
    for f in FONT_DIRS:
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


# ---------------------------------------------------------------- small helpers

def noise(w, h, seed, amp):
    """A little grain, so the flat fills do not look like flat fills."""
    im = Image.new('L', (w // 4, h // 4))
    px = im.load()
    for y in range(h // 4):
        for x in range(w // 4):
            v = (x * 374761393 + y * 668265263 + seed * 1274126177) & 0xFFFF
            px[x, y] = 128 + int((v / 65535.0 - .5) * amp * 2)
    return im.resize((w, h), Image.BICUBIC)


def glow(layer, radius, times=1):
    g = layer
    for _ in range(times):
        g = g.filter(ImageFilter.GaussianBlur(radius))
    return g


def tint(sym, rgb, alpha=255):
    """A symbol recoloured, keeping its own shape."""
    out = Image.new('RGBA', sym.size, rgb + (0,))
    a = sym.split()[3]
    if alpha != 255:
        a = a.point(lambda v: v * alpha // 255)
    out.putalpha(a)
    return out


def centred(d, box, text, fnt, fill, shadow=None, shadow_at=(0, 3)):
    x0, y0, x1, y1 = box
    w = d.textlength(text, font=fnt)
    asc, desc = fnt.getmetrics()
    x = x0 + (x1 - x0 - w) / 2
    y = y0 + (y1 - y0 - (asc + desc)) / 2
    if shadow:
        d.text((x + shadow_at[0], y + shadow_at[1]), text, font=fnt, fill=shadow)
    d.text((x, y), text, font=fnt, fill=fill)


def fitted(d, box, text, fill, shadow=None, start=70, bold=0):
    """The largest size of type that still fits the plate it has to sit in."""
    x0, y0, x1, y1 = box
    size = start
    while size > 12:
        f = font(size)
        if d.textlength(text, font=f) <= (x1 - x0) and sum(f.getmetrics()) <= (y1 - y0) * 1.18:
            break
        size -= 2
    f = font(size)
    if bold:
        for dx in range(-bold, bold + 1):
            for dy in range(-bold, bold + 1):
                if dx or dy:
                    centred(d, box, text, f, shadow or fill, shadow_at=(dx, dy))
    centred(d, box, text, f, fill, shadow)


# ---------------------------------------------------------------- the face

def ground(accent, finish):
    """The field the whole thing sits on: deep, lit from the middle, grained, and darkened at the rim."""
    base = Image.new('RGB', (N, N), (18, 46, 54) if finish == 'plain' else (26, 34, 50))
    lit = Image.new('L', (N, N), 0)
    d = ImageDraw.Draw(lit)
    for i in range(26):
        r = int(N * (.72 - i * .025))
        d.ellipse((CX - r, CY - r, CX + r, CY + r), fill=int(14 + i * 7))
    lit = lit.filter(ImageFilter.GaussianBlur(40))
    wash = Image.new('RGB', (N, N), tuple(min(255, c // 2 + 26) for c in accent))
    base = Image.composite(wash, base, lit.point(lambda v: v // 3))

    g = noise(N, N, 7, 16).point(lambda v: v)
    base = Image.blend(base, Image.merge('RGB', (g, g, g)), .10)

    vig = Image.new('L', (N, N), 0)
    ImageDraw.Draw(vig).ellipse((-N // 5, -N // 5, N + N // 5, N + N // 5), fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(90))
    return Image.composite(base, Image.new('RGB', (N, N), (6, 12, 18)), vig).convert('RGBA')


def frame(im, accent, finish):
    """A double gold rule round the edge with a corner flourish at each turn."""
    d = ImageDraw.Draw(im)
    m = int(16 * K)
    d.rectangle((m, m, N - m, N - m), outline=GOLD_DEEP + (255,), width=int(9 * K))
    d.rectangle((m, m, N - m, N - m), outline=GOLD + (255,), width=int(4 * K))
    i = int(30 * K)
    d.rectangle((i, i, N - i, N - i), outline=GOLD_DEEP + (190,), width=int(2 * K))
    j = int(38 * K)
    d.rectangle((j, j, N - j, N - j), outline=GOLD + (120,), width=int(2 * K))

    arm = int(46 * K)
    for sx, sy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        px = j if sx > 0 else N - j
        py = j if sy > 0 else N - j
        d.line((px, py, px + sx * arm, py), fill=GOLD + (255,), width=int(5 * K))
        d.line((px, py, px, py + sy * arm), fill=GOLD + (255,), width=int(5 * K))
        r = int(11 * K)
        d.ellipse((px + sx * arm - r, py - r, px + sx * arm + r, py + r), outline=GOLD + (255,), width=int(3 * K))
        d.ellipse((px - r, py + sy * arm - r, px + r, py + sy * arm + r), outline=GOLD + (255,), width=int(3 * K))


def plate(im, box, accent, notch=True, fill=(10, 16, 28, 236)):
    """A dark plate with notched ends and a gold edge: what the lettering sits on."""
    x0, y0, x1, y1 = box
    pad = int(14 * K)
    x0 -= pad; x1 += pad; y0 -= int(8 * K); y1 += int(8 * K)
    d = ImageDraw.Draw(im)
    cut = int((y1 - y0) * .42) if notch else 0
    pts = [(x0 + cut, y0), (x1 - cut, y0), (x1, (y0 + y1) // 2), (x1 - cut, y1),
           (x0 + cut, y1), (x0, (y0 + y1) // 2)]
    d.polygon(pts, fill=fill)
    d.line(pts + [pts[0]], fill=GOLD + (255,), width=int(4 * K))
    d.line([(x0 + cut + int(6 * K), y0 + int(5 * K)), (x1 - cut - int(6 * K), y0 + int(5 * K))],
           fill=GOLD_LIT + (120,), width=int(2 * K))


def rune_rings(im, accent):
    """Faint circles under the symbols: the field was an empty wash without them."""
    layer = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for r, a, w in ((RING_R + int(30 * K), 26, 2), (RING_R - int(30 * K), 20, 2),
                    (RING_R + int(66 * K), 16, 2), (int(MED_R * 1.5), 22, 2)):
        d.ellipse((CX - r, CY - r, CX + r, CY + r), outline=GOLD + (a,), width=int(w * K))
    for i in range(72):                       # a dashed one, for the look of a dial
        a0 = i * 5
        r = RING_R + int(50 * K)
        d.arc((CX - r, CY - r, CX + r, CY + r), a0, a0 + 2.4, fill=GOLD + (40,), width=int(3 * K))
    im.alpha_composite(layer)


def ring(im, pack, accent, finish):
    """The twenty-two round the outside, with this pack's own one picked out."""
    syms = {}
    for p in PATHWAYS:
        f = os.path.join(OUT, 'sym_%s.png' % p)
        syms[p] = Image.open(f).convert('RGBA') if os.path.exists(f) else None

    lights = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    for i, p in enumerate(PATHWAYS):
        a = -math.pi / 2 + i * 2 * math.pi / len(PATHWAYS)
        x = CX + math.cos(a) * RING_R
        y = CY + math.sin(a) * RING_R
        c = COLOUR.get(p, GOLD)
        if syms[p] is None:
            continue
        s = syms[p].resize((RING_SZ, RING_SZ), Image.LANCZOS)
        lights.alpha_composite(tint(s, c, 185), (int(x - RING_SZ / 2), int(y - RING_SZ / 2)))
    im.alpha_composite(glow(lights, 9))

    for i, p in enumerate(PATHWAYS):
        a = -math.pi / 2 + i * 2 * math.pi / len(PATHWAYS)
        x = CX + math.cos(a) * RING_R
        y = CY + math.sin(a) * RING_R
        c = COLOUR.get(p, GOLD)
        if p == pack:
            d = ImageDraw.Draw(im)
            r = int(RING_SZ * .82)
            d.ellipse((x - r, y - r, x + r, y + r), outline=GOLD + (255,), width=int(4 * K))
            d.ellipse((x - r + int(6 * K), y - r + int(6 * K), x + r - int(6 * K), y + r - int(6 * K)),
                      outline=GOLD_LIT + (120,), width=int(2 * K))
        if syms[p] is None:
            continue
        s = syms[p].resize((RING_SZ, RING_SZ), Image.LANCZOS)
        lit = (255, 255, 255) if p == pack else tuple(min(255, v + 60) for v in c)
        im.alpha_composite(tint(s, lit, 255 if p == pack else 225),
                           (int(x - RING_SZ / 2), int(y - RING_SZ / 2)))

    # the little crosses between the symbols, as the old face had
    d = ImageDraw.Draw(im)
    for i in range(len(PATHWAYS)):
        a = -math.pi / 2 + (i + .5) * 2 * math.pi / len(PATHWAYS)
        x = CX + math.cos(a) * (RING_R + int(46 * K))
        y = CY + math.sin(a) * (RING_R + int(46 * K))
        n = int(9 * K)
        d.line((x - n, y, x + n, y), fill=GOLD + (150,), width=int(3 * K))
        d.line((x, y - n, x, y + n), fill=GOLD + (150,), width=int(3 * K))


def medallion(im, pack, accent):
    """The disc at the middle, with this pack's symbol large on it."""
    halo = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    for i in range(14):
        r = MED_R + int((14 - i) * 6 * K)
        hd.ellipse((CX - r, CY - r, CX + r, CY + r), fill=accent + (10,))
    im.alpha_composite(glow(halo, 22))

    d = ImageDraw.Draw(im)
    d.ellipse((CX - MED_R, CY - MED_R, CX + MED_R, CY + MED_R), fill=(8, 18, 30, 252))
    inner = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    idr = ImageDraw.Draw(inner)
    for i in range(18):
        r = int(MED_R * (1 - i * .05))
        idr.ellipse((CX - r, CY - r, CX + r, CY + r), fill=accent + (7,))
    im.alpha_composite(glow(inner, 14))
    d.ellipse((CX - MED_R, CY - MED_R, CX + MED_R, CY + MED_R), outline=GOLD + (255,), width=int(5 * K))
    r2 = MED_R + int(13 * K)
    d.ellipse((CX - r2, CY - r2, CX + r2, CY + r2), outline=GOLD_DEEP + (210,), width=int(3 * K))

    f = os.path.join(OUT, 'sym_%s.png' % pack)
    if not os.path.exists(f):
        # a pack with no pathway of its own gets a mark drawn for it rather than an empty disc
        star = Image.new('RGBA', (N, N), (0, 0, 0, 0))
        sd = ImageDraw.Draw(star)
        for i in range(16):
            a = i * math.pi / 8
            ln = MED_SZ * (.46 if i % 2 == 0 else .27)
            sd.line((CX, CY, CX + math.cos(a) * ln, CY + math.sin(a) * ln),
                    fill=accent + (235,), width=int((7 if i % 2 == 0 else 4) * K))
        r = int(MED_SZ * .17)
        sd.ellipse((CX - r, CY - r, CX + r, CY + r), fill=(255, 255, 255, 240))
        im.alpha_composite(glow(star, 20))
        im.alpha_composite(star)
        return
    if os.path.exists(f):
        s = Image.open(f).convert('RGBA').resize((MED_SZ, MED_SZ), Image.LANCZOS)
        soft = Image.new('RGBA', (N, N), (0, 0, 0, 0))
        soft.alpha_composite(tint(s, accent, 200), (CX - MED_SZ // 2, CY - MED_SZ // 2))
        im.alpha_composite(glow(soft, 26))
        im.alpha_composite(tint(s, tuple(min(255, v + 90) for v in accent)),
                           (CX - MED_SZ // 2, CY - MED_SZ // 2))


def finish_pass(im, finish, accent):
    """Shiny lays a sheet of foil over it; rainbow runs the spectrum across the whole face."""
    if finish == 'plain':
        return im
    w = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(w)
    if finish == 'shiny':
        for i in range(-N, N * 2, int(52 * K)):
            d.polygon([(i, 0), (i + int(26 * K), 0), (i + int(26 * K) + N, N), (i + N, N)],
                      fill=(255, 236, 170, 26))
        im.alpha_composite(glow(w, 7))
        tone = Image.new('RGBA', (N, N), (255, 206, 110, 26))
        im.alpha_composite(tone)
    else:
        bars = Image.new('RGBA', (N, N), (0, 0, 0, 0))
        bd = ImageDraw.Draw(bars)
        cols = [(255, 90, 90), (255, 180, 70), (250, 240, 90), (110, 230, 120),
                (90, 200, 255), (140, 130, 255), (225, 120, 235)]
        step = int(N * 1.6 / len(cols))
        for i, c in enumerate(cols):
            x = -N // 3 + i * step
            bd.polygon([(x, 0), (x + step, 0), (x + step + N, N), (x + N, N)], fill=c + (46,))
        im.alpha_composite(glow(bars, 30))
    return im


def build(pack, finish):
    accent = COLOUR.get(pack, GOLD)
    im = ground(accent, finish)
    rune_rings(im, accent)
    ring(im, pack, accent, finish)
    medallion(im, pack, accent)
    im = finish_pass(im, finish, accent)
    frame(im, accent, finish)

    d = ImageDraw.Draw(im)
    plate(im, TITLE_BOX, accent)
    fitted(d, TITLE_BOX, 'TAROT PACK', GOLD_LIT + (255,), (60, 40, 10, 255), start=78, bold=1)

    name = TITLES.get(pack, pack.replace('_', ' ').upper())
    lit = tuple(min(255, v + 80) for v in accent)
    soft = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    fitted(ImageDraw.Draw(soft), SUB_BOX, name, accent + (210,), start=72)
    im.alpha_composite(glow(soft, 13))
    fitted(d, SUB_BOX, name, lit + (255,), (0, 0, 0, 220), start=72, bold=1)

    plate(im, FOOT_BOX, accent, notch=False, fill=(26, 16, 46, 240))
    fitted(d, FOOT_BOX, '3 CARDS', GOLD_LIT + (255,), (40, 26, 6, 255), start=44)
    return im


def save(im, path):
    im.convert('RGB').quantize(colors=255, method=Image.FASTOCTREE) \
        .save(path, optimize=True)


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    packs = [only] if only else [p for p in PATHWAYS] + ['ancient_one']
    n = 0
    for p in packs:
        for finish, suffix in (('plain', ''), ('shiny', '_shiny'), ('rainbow', '_rainbow')):
            im = build(p, finish)
            path = os.path.join(OUT, 'pack_%s%s.png' % (p, suffix))
            save(im, path)
            n += 1
        if only:
            build(p, 'plain').save('/tmp/pack_preview.png')
            build(p, 'shiny').save('/tmp/pack_preview_shiny.png')
    print('wrote', n)


if __name__ == '__main__':
    main()
