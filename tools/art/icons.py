"""Inventory sprites of the Uniqueness items and the Sword of Twilight's two textures. Drawn at 8x and scaled down to 64x64.
Run: python3 tools/art/icons.py   (writes into src/main/resources/assets/astral_world/textures/item/)"""
import math, os, random
from PIL import Image, ImageDraw, ImageFilter
SS, N = 8, 64
W = SS * N
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../src/main/resources/assets/astral_world/textures/item/')

def rgb(h, a=255): return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)
def mix(a, b, f): return tuple(int(a[i] * (1 - f) + b[i] * f) for i in range(3)) + (255,)
def new(): return Image.new('RGBA', (W, W), (0, 0, 0, 0))
def P(x, y): return (x * SS, y * SS)                      # sprite pixels -> canvas
def _on(d, col, fn):
    """Draw with fn(draw); a translucent colour is blended over what is there (ImageDraw alone would overwrite it)."""
    if len(col) < 4 or col[3] == 255: fn(d); return
    lay = Image.new('RGBA', d._image.size, (0, 0, 0, 0)); fn(ImageDraw.Draw(lay)); d._image.alpha_composite(lay)
def disc(d, x, y, r, col): _on(d, col, lambda q: q.ellipse([(x - r) * SS, (y - r) * SS, (x + r) * SS, (y + r) * SS], fill=col))
def poly(d, pts, col): _on(d, col, lambda q: q.polygon([P(*p) for p in pts], fill=col))
def line(d, pts, w, col): _on(d, col, lambda q: q.line([P(*p) for p in pts], fill=col, width=max(1, int(w * SS)), joint='curve'))
def arc(d, x, y, r, a0, a1, w, col): _on(d, col, lambda q: q.arc([(x - r) * SS, (y - r) * SS, (x + r) * SS, (y + r) * SS], a0, a1, fill=col, width=max(1, int(w * SS))))
def grad_disc(d, x, y, r, stops):
    """stops: [(t, rgba)] from the centre (t=0) to the rim (t=1); blended over what is there."""
    lay = Image.new('RGBA', d._image.size, (0, 0, 0, 0)); q = ImageDraw.Draw(lay)
    steps = int(r * SS)
    for i in range(steps, 0, -1):
        t = i / steps
        for k in range(len(stops) - 1):
            if stops[k][0] <= t <= stops[k + 1][0]:
                f = (t - stops[k][0]) / max(1e-6, stops[k + 1][0] - stops[k][0]); a, b = stops[k][1], stops[k + 1][1]
                col = tuple(int(a[j] * (1 - f) + b[j] * f) for j in range(4)); break
        q.ellipse([x * SS - i, y * SS - i, x * SS + i, y * SS + i], fill=col)
    d._image.alpha_composite(lay)
def glow(im, col, radius, strength=1.0):
    """A soft halo of col behind everything already drawn."""
    a = im.split()[3].filter(ImageFilter.GaussianBlur(radius * SS))
    a = a.point(lambda v: min(255, int(v * strength)))
    halo = Image.new('RGBA', im.size, col[:3] + (0,)); halo.putalpha(a)
    return Image.alpha_composite(halo, im)
def outline(im, col, px=1.0):
    a = im.split()[3].filter(ImageFilter.MaxFilter(int(px * SS) * 2 + 1))
    o = Image.new('RGBA', im.size, col[:3] + (0,)); o.putalpha(a)
    return Image.alpha_composite(o, im)
def save(im, name, hard=False):
    im = im.resize((N, N), Image.LANCZOS)
    if hard:   # crisp edges for the extruded hand model
        px = im.load()
        for y in range(N):
            for x in range(N):
                r, g, b, a = px[x, y]; px[x, y] = (r, g, b, 255) if a >= 110 else (0, 0, 0, 0)
    im.save(OUT + name + '.png'); print('wrote', name)
def curve(pts, n=60):
    """Catmull-Rom through pts."""
    out = []
    for i in range(len(pts) - 1):
        p0, p1, p2, p3 = pts[max(i - 1, 0)], pts[i], pts[i + 1], pts[min(i + 2, len(pts) - 1)]
        for s in range(n):
            t = s / n
            out.append(tuple(.5 * ((2 * p1[k]) + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t * t + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t ** 3) for k in range(2)))
    out.append(pts[-1]); return out
def feather(d, x, y, ang, ln, wd, col, tip=None):
    c, s = math.cos(ang), math.sin(ang); nx, ny = -s, c
    pts = [(x, y), (x + c * ln * .35 + nx * wd, y + s * ln * .35 + ny * wd), (x + c * ln, y + s * ln), (x + c * ln * .35 - nx * wd, y + s * ln * .35 - ny * wd)]
    poly(d, pts, col)
    if tip: poly(d, [(x + c * ln * .68 + nx * wd * .55, y + s * ln * .68 + ny * wd * .55), (x + c * ln, y + s * ln), (x + c * ln * .68 - nx * wd * .55, y + s * ln * .68 - ny * wd * .55)], tip)
    line(d, [(x, y), (x + c * ln * .9, y + s * ln * .9)], .25, mix(col, (0, 0, 0), .35))

# ---------------- the Sword of Twilight (hand texture, tip to the upper right) ----------------
def sword(dark):
    L = 3   # extra supersampling for the rotation
    im = Image.new('RGBA', (W * L // 2, W * L // 2), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    u = W * L / 2 / 400.0; cx = W * L / 4
    def X(x, y): return (cx + x * u, (388 - y) * u)       # sword units: y up from the pommel (0) to the tip (372)
    def pg(pts, col): d.polygon([X(*p) for p in pts], fill=col)
    blade, flat, edge, linec = (rgb(0x0E0E12), rgb(0x1C1C24), rgb(0xE01020), rgb(0xFF2636)) if dark else (rgb(0xFAFAF6), rgb(0xE2E6EE), rgb(0xFFD23C), rgb(0xFFF0B0))
    gold, goldL, goldD, inlay = (rgb(0x16161C), rgb(0x2C2C36), rgb(0x08080A), rgb(0xE01020)) if dark else (rgb(0xE8C860), rgb(0xFFF0B0), rgb(0xA8862C), rgb(0x27305A))
    grip, gripD = (rgb(0x121218), rgb(0x050507)) if dark else (rgb(0x2A44A6), rgb(0x16245E))
    # blade: an edge of light, the body, a flat down the middle
    def bl(w0, w1, tipw, col, y0=112, y1=316, yt=372): pg([(-w0, y0), (-w1, y1), (0, yt), (w1, y1), (w0, y0)], col)
    bl(21, 18, 0, edge); bl(16.5, 13.5, 0, blade, yt=362); bl(8, 6, 0, flat, y1=300, yt=336)
    pg([(-1.6, 124), (-1.2, 330), (0, 340), (1.2, 330), (1.6, 124)], linec if dark else rgb(0xC8CCD8))
    if dark:
        for k in range(5):
            y = 150 + k * 21; r = 8.5 - k * .5
            d.ellipse([X(-r, y + r), X(r, y - r)], fill=linec); d.ellipse([X(-r + 3.2, y + r - 3.2), X(r - 3.2, y - r + 3.2)], fill=blade)
        pg([(-5, 262), (0, 250), (5, 262), (0, 274)], linec)
    # grip, bands, pommel
    pg([(-7.5, 22), (-6.5, 98), (6.5, 98), (7.5, 22)], grip); pg([(-7.5, 22), (-7, 60), (-3, 60), (-3.5, 22)], gripD)
    for k in range(5): pg([(-7.6, 30 + k * 15), (-7.6, 34 + k * 15), (7.6, 37 + k * 15), (7.6, 33 + k * 15)], gripD if not dark else inlay)
    pg([(-10, 8), (-11, 22), (11, 22), (10, 8), (5, 1), (-5, 1)], gold); pg([(-6, 10), (-6.5, 20), (6.5, 20), (6, 10)], goldL)
    # the crescent guard: its horns sweep out and up toward the blade
    pts_o, pts_i = [], []
    for k in range(25):
        t = -1 + 2 * k / 24; x = t * 62; pts_o.append((x, 92 + 30 * t * t - 4)); pts_i.append((x * .97, 92 + 30 * t * t + 15 - 8 * abs(t) ** 1.5))
    pg(pts_o + pts_i[::-1], gold)
    pg([(p[0] * .94, p[1] + 5) for p in pts_o] + [(p[0] * .94, p[1] - 4) for p in pts_i[::-1]], goldL if not dark else gold)
    pg([(p[0] * .9, p[1] + 7.5) for p in pts_o] + [(p[0] * .9, p[1] + 10.5) for p in pts_o[::-1]], inlay)
    # the ricasso plate with its inlay pointing up the blade
    pg([(-17, 96), (-19, 122), (-10, 150), (0, 160), (10, 150), (19, 122), (17, 96)], gold)
    pg([(-9, 104), (-10, 124), (0, 148), (10, 124), (9, 104)], inlay); pg([(-3.5, 110), (-4, 124), (0, 136), (4, 124), (3.5, 110)], goldL if not dark else blade)
    pg([(-17, 96), (-19, 122), (-15, 124), (-13.5, 96)], goldD)
    im = im.rotate(-45, resample=Image.BICUBIC, expand=False)
    full = Image.new('RGBA', (W * L, W * L), (0, 0, 0, 0))
    # the rotated sword's pommel sits toward the lower left: place it so the whole length fills the sprite
    big = im.resize((int(im.size[0] * 2.55), int(im.size[1] * 2.55)), Image.LANCZOS)
    full.alpha_composite(big, ((W * L - big.size[0]) // 2 - int(W * L * .012), (W * L - big.size[1]) // 2 + int(W * L * .012)))
    full = full.resize((W, W), Image.LANCZOS)
    return outline(full, rgb(0x2A0408) if dark else rgb(0x6A4E10), .45)

save(sword(False), 'twilight_sword', hard=True)
save(sword(True), 'twilight_sword_dark', hard=True)

# ---------------- Feathered Serpent ----------------
def serpent():
    im = new(); d = ImageDraw.Draw(im)
    G0, G1, G2, GOLD, WH, RED = rgb(0x0C4A3A), rgb(0x14785C), rgb(0x26B48A), rgb(0xE8C860), rgb(0xF2FFF8), rgb(0xD83A3A)
    # wings behind the neck
    for sd in (-1, 1):
        for k in range(7):
            a = math.radians(-90 + sd * (28 + k * 17)); ln = 25 - k * 1.7
            feather(d, 39 + sd * 2, 26, a, ln, 3.0, [rgb(0x18A07A), rgb(0x22C89C), rgb(0x7EF0D0)][k % 3], tip=WH if k % 2 else RED)
    spine = curve([(13, 58), (10, 48), (20, 41), (34, 46), (45, 52), (55, 45), (52, 33), (40, 29), (36, 20), (43, 12)], 40)
    n = len(spine)
    for i, (x, y) in enumerate(spine):   # dark underlay
        u = i / (n - 1); disc(d, x, y, 1.6 + 4.6 * math.sin(math.pi * min(1, .12 + u * .88)) + .9, G0)
    for i, (x, y) in enumerate(spine):
        u = i / (n - 1); r = 1.6 + 4.6 * math.sin(math.pi * min(1, .12 + u * .88))
        disc(d, x, y, r, G1 if (i // 9) % 2 == 0 else mix(G1, G2, .45))
    for i in range(1, n - 1):            # gold belly and a bright back stripe, offset along the normal
        (x0, y0), (x1, y1) = spine[i - 1], spine[i + 1]; tx, ty = x1 - x0, y1 - y0; l = math.hypot(tx, ty) + 1e-6; nx, ny = -ty / l, tx / l
        u = i / (n - 1); r = 1.6 + 4.6 * math.sin(math.pi * min(1, .12 + u * .88)); x, y = spine[i]
        disc(d, x + nx * r * .5, y + ny * r * .5, r * .42, GOLD if (i // 6) % 2 == 0 else mix(GOLD, (120, 90, 20), .35))
        disc(d, x - nx * r * .45, y - ny * r * .45, r * .3, G2)
        if i % 14 == 7 and u < .86: feather(d, x - nx * r * .8, y - ny * r * .8, math.atan2(-ny, -nx) - .9, 6.5 + 4 * u, 1.5, rgb(0x22C89C), tip=WH)
    for k in range(3): feather(d, 13, 58, math.radians(110 + k * 32), 10 - k, 1.7, [rgb(0x22C89C), rgb(0x7EF0D0), rgb(0x18A07A)][k], tip=RED if k == 1 else WH)   # tail streamers
    # head, facing right
    hx, hy = 44, 11
    for k in range(5): feather(d, hx - 3, hy - 1, math.radians(-150 + k * 22), 12 - abs(k - 2) * 1.5, 1.8, [rgb(0x22C89C), rgb(0x7EF0D0), rgb(0x18A07A)][k % 3], tip=RED if k % 2 == 0 else WH)   # crest
    poly(d, [(hx - 6.5, hy - 4.5), (hx + 4, hy - 5), (hx + 12.5, hy - 1), (hx + 12, hy + 2), (hx + 3, hy + 5.5), (hx - 6.5, hy + 5)], G0)
    poly(d, [(hx - 5.5, hy - 3.5), (hx + 4, hy - 4), (hx + 11.5, hy - .6), (hx + 11, hy + 1), (hx + 3, hy + 2), (hx - 5.5, hy + 2)], G1)
    poly(d, [(hx - 5, hy + 2.2), (hx + 3, hy + 2.2), (hx + 10.5, hy + 1.6), (hx + 3, hy + 4.6), (hx - 5, hy + 4.2)], GOLD)
    poly(d, [(hx - 2, hy - 4.4), (hx - 7.5, hy - 10.5), (hx - 4.2, hy - 4.2)], GOLD); poly(d, [(hx + 1.5, hy - 4.6), (hx - 1.5, hy - 11), (hx + 3.6, hy - 4.6)], rgb(0xFFF0B0))   # horns
    disc(d, hx + 3.2, hy - 1.2, 1.7, rgb(0xFFD040)); poly(d, [(hx + 3, hy - 2.7), (hx + 3.7, hy - 1.2), (hx + 3, hy + .3), (hx + 2.6, hy - 1.2)], rgb(0x140808))
    poly(d, [(hx + 9.5, hy + 1.6), (hx + 10.3, hy + 4.4), (hx + 11, hy + 1.6)], WH); line(d, [(hx + 12, hy + 1.2), (hx + 15.5, hy + 2.4), (hx + 17, hy + 1.2)], .5, RED); line(d, [(hx + 15.5, hy + 2.4), (hx + 17, hy + 3.6)], .5, RED)
    line(d, [(hx + 8, hy + 2.4), (hx + 10, hy + 7), (hx + 8.5, hy + 10)], .45, GOLD)
    return glow(outline(im, rgb(0x062A20), .5), rgb(0x40FFC0), 2.2, .5)
save(serpent(), 'bone_greatsword')

# ---------------- Bandaged Doll ----------------
def doll():
    im = new(); d = ImageDraw.Draw(im); random.seed(4)
    C0, C1, C2, DK = rgb(0xE9DCC0), rgb(0xD2C19E), rgb(0xB39F7A), rgb(0x5A4A32)
    # arms and legs
    for sd in (-1, 1):
        line(d, [(32 + sd * 9, 34), (32 + sd * 15, 44), (32 + sd * 14, 50)], 5.5, C1); line(d, [(32 + sd * 4.5, 52), (32 + sd * 5.5, 60)], 6, C1)
        for k in range(3): line(d, [(32 + sd * 11.3 + sd * k * 1.6, 37 + k * 4.4), (32 + sd * 15.5 + sd * k * .4, 39.5 + k * 4.4)], .7, C2)
    # body: wrapped bandages
    d.rounded_rectangle([P(20, 30)[0], P(20, 30)[1], P(44, 56)[0], P(44, 56)[1]], radius=7 * SS, fill=C1)
    for k in range(7):
        y = 31.5 + k * 3.6; sk = (-1.6 if k % 2 else 1.6)
        poly(d, [(20.4, y - sk), (43.6, y + sk), (43.6, y + sk + 2.6), (20.4, y - sk + 2.6)], C0 if k % 2 else mix(C0, C2, .35))
        line(d, [(20.4, y - sk + 2.6), (43.6, y + sk + 2.6)], .35, C2)
    line(d, [(43, 46), (49, 52), (47, 58), (50, 62)], 2.2, C0); line(d, [(43, 46), (49, 52), (47, 58), (50, 62)], .4, C2)   # a loose end
    # head
    grad_disc(d, 32, 19, 13, [(0, C0), (.75, C0), (1, C1)])
    for k in range(3): line(d, [(19.6 + k * .4, 13 + k * 4.5), (44.4 - k * .4, 11.5 + k * 4.5)], .4, C2)
    disc(d, 27, 19, 2.6, rgb(0x2A2018)); disc(d, 26.2, 18.2, .8, rgb(0x8A7A60))                                              # a button eye
    line(d, [(35, 16.8), (39, 20.8)], .9, DK); line(d, [(39, 16.8), (35, 20.8)], .9, DK)                                      # a stitched one
    line(d, [(26.5, 26), (37.5, 25.4)], .7, DK)
    for k in range(5): line(d, [(27.5 + k * 2.2, 24.6), (27.9 + k * 2.2, 27.2)], .55, DK)
    disc(d, 22.5, 23.5, 1.8, rgb(0xD89A8A, 150)); disc(d, 41.5, 23, 1.8, rgb(0xD89A8A, 150))
    # a chain round the waist and its padlock
    for k in range(7): d.ellipse([P(19.5 + k * 3.6, 41.2 + (k % 2) * .6)[0], P(0, 41.2 + (k % 2) * .6)[1], P(23.6 + k * 3.6, 0)[0], P(0, 44.6 + (k % 2) * .6)[1]], outline=rgb(0x8A9098), width=int(.9 * SS))
    d.rounded_rectangle([P(29, 44)[0], P(29, 44)[1], P(35.5, 50)[0], P(35.5, 50)[1]], radius=SS, fill=rgb(0xC8A040)); d.arc([P(30, 40.5)[0], P(30, 40.5)[1], P(34.5, 46.5)[0], P(34.5, 46.5)[1]], 180, 360, fill=rgb(0x8A9098), width=int(.9 * SS)); disc(d, 32.2, 47, .9, rgb(0x3A2A10))
    return glow(outline(im, rgb(0x3A2E1E), .5), rgb(0xB0C8FF), 2.0, .35)
save(doll(), 'chained_bow')

# ---------------- Writhing Shadows ----------------
def shadows():
    im = new(); d = ImageDraw.Draw(im)
    grad_disc(d, 32, 32, 30, [(0, rgb(0x6A30C8, 0)), (.55, rgb(0x6A30C8, 70)), (1, rgb(0x6A30C8, 0))])
    d.ellipse([P(7, 43)[0], P(7, 43)[1], P(57, 60)[0], P(57, 60)[1]], fill=rgb(0x7A40D8)); d.ellipse([P(8.5, 44)[0], P(8.5, 44)[1], P(55.5, 59)[0], P(55.5, 59)[1]], fill=rgb(0x0A0414)); d.ellipse([P(15, 47)[0], P(15, 47)[1], P(49, 56.5)[0], P(49, 56.5)[1]], fill=rgb(0x020006))
    tents = [([(14, 52), (9, 40), (14, 30), (9, 22), (13, 17)], 3.4), ([(24, 53), (27, 40), (21, 28), (25, 16), (20, 8)], 4.2), ([(34, 54), (31, 42), (38, 31), (34, 18), (41, 10), (46, 13)], 4.6),
             ([(44, 53), (47, 42), (43, 34), (49, 26), (47, 19)], 3.6), ([(51, 51), (56, 43), (53, 36), (57, 31)], 2.8)]
    for pts, w in tents:
        c = curve(pts, 30); n = len(c)
        for i, (x, y) in enumerate(c): disc(d, x, y, w * (1 - i / n * .86) + .8, rgb(0x8A4AE0))
        for i, (x, y) in enumerate(c): disc(d, x, y, w * (1 - i / n * .86), rgb(0x0A0414) if i < n * .8 else mix(rgb(0x0A0414), rgb(0x7A40D8), (i / n - .8) / .2))
        for i in range(6, int(n * .75), 11): disc(d, c[i][0] + w * .35, c[i][1], w * .22, rgb(0xB080FF))   # suckers
    return im
save(shadows(), 'shadow_sword')

# ---------------- Wheel of Fortune ----------------
def wheel():
    im = new(); d = ImageDraw.Draw(im); cx = cy = 33
    disc(d, cx, cy, 27.5, rgb(0x6A4A10)); disc(d, cx, cy, 26.5, rgb(0xE8C860)); disc(d, cx, cy, 23.6, rgb(0x6A4A10))
    cols = [rgb(0xB8242C), rgb(0xF2E8D0), rgb(0x1E3C8C), rgb(0xF2E8D0), rgb(0x1E7A4A), rgb(0xF2E8D0), rgb(0x6A2C9A), rgb(0xF2E8D0)]
    for k in range(8): d.pieslice([P(cx - 23, cy - 23)[0], P(0, cy - 23)[1], P(cx + 23, 0)[0], P(0, cy + 23)[1]], k * 45 - 22.5, k * 45 + 22.5, fill=cols[k])
    grad_disc(d, cx, cy, 23, [(0, rgb(0xFFFFFF, 60)), (.6, rgb(0xFFFFFF, 0)), (1, rgb(0x000000, 70))])
    for k in range(8):
        a = math.radians(k * 45 + 22.5); line(d, [(cx, cy), (cx + math.cos(a) * 24, cy + math.sin(a) * 24)], 1.3, rgb(0xE8C860))
        b = math.radians(k * 45); disc(d, cx + math.cos(a) * 25.1, cy + math.sin(a) * 25.1, 1.5, rgb(0xFFF0B0)); disc(d, cx + math.cos(b) * 25.1, cy + math.sin(b) * 25.1, 1.2, rgb(0xD83A3A) if k % 2 else rgb(0x3A7AD8))
        disc(d, cx + math.cos(b) * 15, cy + math.sin(b) * 15, 2.0, mix(cols[k], (0, 0, 0), .3) if k % 2 == 0 else rgb(0xC8A040))
    disc(d, cx, cy, 6, rgb(0x6A4A10)); disc(d, cx, cy, 5, rgb(0xE8C860)); grad_disc(d, cx, cy, 3.2, [(0, rgb(0xFFFFFF)), (.5, rgb(0x7EE0FF)), (1, rgb(0x1E6ACC))])
    poly(d, [(cx - 4.5, 1.5), (cx + 4.5, 1.5), (cx, 12)], rgb(0x6A1014)); poly(d, [(cx - 3.2, 2.5), (cx + 3.2, 2.5), (cx, 10.2)], rgb(0xE83A40)); disc(d, cx, 3.6, 1.3, rgb(0xFFF0B0))
    return glow(im, rgb(0xFFD860), 1.8, .45)
save(wheel(), 'snake_rifle')

# ---------------- Mini Sun ----------------
def sun():
    im = new(); d = ImageDraw.Draw(im); cx = cy = 32
    grad_disc(d, cx, cy, 31, [(0, rgb(0xFFB030, 150)), (.5, rgb(0xFF9020, 90)), (1, rgb(0xFF7010, 0))])
    for k in range(16):
        a = math.radians(k * 22.5); ln = 27 if k % 2 == 0 else 21; w = .13 if k % 2 == 0 else .1
        poly(d, [(cx + math.cos(a - w) * 14, cy + math.sin(a - w) * 14), (cx + math.cos(a + .25) * ln, cy + math.sin(a + .25) * ln), (cx + math.cos(a + w) * 14, cy + math.sin(a + w) * 14)], rgb(0xFF8A1C) if k % 2 == 0 else rgb(0xFFC83C))
    disc(d, cx, cy, 16.2, rgb(0xE86A10))
    grad_disc(d, cx, cy, 15.4, [(0, rgb(0xFFFFFF)), (.35, rgb(0xFFF6C0)), (.7, rgb(0xFFD23C)), (1, rgb(0xFF9A1C))])
    for k in range(5): arc(d, cx, cy, 11 - k * 1.6, math.degrees(k * 2.4), math.degrees(k * 2.4) + 70, .8, rgb(0xFF9A20, 170))
    disc(d, cx - 5, cy - 5.5, 3.2, rgb(0xFFFFFF, 150))
    return im
save(sun(), 'spear_of_sun')

# ---------------- Ball of Lightning ----------------
def ball():
    im = new(); d = ImageDraw.Draw(im); cx = cy = 32; random.seed(11)
    grad_disc(d, cx, cy, 31, [(0, rgb(0x6A8CFF, 170)), (.5, rgb(0x7A50FF, 80)), (1, rgb(0x5030E0, 0))])
    def bolt(a, ln, w, col):
        pts = [(cx + math.cos(a) * 9, cy + math.sin(a) * 9)]
        for j in range(1, 6):
            r = 9 + (ln - 9) * j / 5; off = (random.random() - .5) * .5
            pts.append((cx + math.cos(a + off) * r, cy + math.sin(a + off) * r))
        line(d, pts, w * 2.4, rgb(0x5A7CFF, 200)); line(d, pts, w, col)
        if ln > 22: line(d, [pts[3], (pts[3][0] + math.cos(a + .9) * 7, pts[3][1] + math.sin(a + .9) * 7)], w * .6, col)
    for k in range(9): bolt(k * .698 + .2, 21 + random.random() * 9, .75, rgb(0xFFFFFF))
    grad_disc(d, cx, cy, 13.5, [(0, rgb(0xFFFFFF)), (.4, rgb(0xE6F0FF)), (.72, rgb(0x8AB0FF)), (1, rgb(0x4A5CE0))])
    for k in range(4):
        a = k * 1.7 + .5; c = curve([(cx + math.cos(a) * 11, cy + math.sin(a) * 11), (cx + math.cos(a + .8) * 5, cy + math.sin(a + .8) * 5), (cx + math.cos(a + 2.2) * 9, cy + math.sin(a + 2.2) * 9)], 12)
        line(d, c, .55, rgb(0xFFFFFF, 220))
    disc(d, cx - 4.5, cy - 5, 2.6, rgb(0xFFFFFF, 200))
    return im
save(ball(), 'storm_katana')

# ---------------- the three fun shrooms: a glowing toadstool, squat / thin / broad ----------------
def shroom(kind, cap, spot, stem):
    """kind 0 shrink (small and squat), 1 slim (tall and narrow), 2 wide (low and broad)."""
    im = new(); d = ImageDraw.Draw(im)
    cw = [15, 11, 25][kind]            # cap half width
    ch = [11, 13, 9][kind]             # cap height
    sw = [4.5, 2.6, 6.5][kind]         # stem half width
    sh = [15, 25, 11][kind]            # stem height
    cx, base = 32, 54
    capY = base - sh
    poly(d, [(cx - sw, base), (cx - sw * .8, capY + 2), (cx + sw * .8, capY + 2), (cx + sw, base)], stem)
    poly(d, [(cx - sw, base), (cx - sw * .4, base - 2), (cx - sw * .35, capY + 2), (cx - sw * .8, capY + 2)], mix(stem, (0, 0, 0), .3))
    d.pieslice([P(cx - cw, capY - ch)[0], P(0, capY - ch)[1], P(cx + cw, 0)[0], P(0, capY + ch)[1]], 180, 360, fill=cap)
    poly(d, [(cx - cw, capY), (cx + cw, capY), (cx + cw * .72, capY + 3.2), (cx - cw * .72, capY + 3.2)], mix(cap, (0, 0, 0), .45))
    for k in range(6):                 # gills under the cap
        line(d, [(cx - cw * .6 + k * cw * .24, capY + .6), (cx - cw * .55 + k * cw * .22, capY + 3.0)], .45, mix(cap, (0, 0, 0), .6))
    for (ox, oy, r) in [(-.42, -.46, .20), (.30, -.56, .15), (.58, -.26, .12), (-.04, -.72, .13), (-.70, -.20, .11)]:
        disc(d, cx + ox * cw, capY + oy * ch, r * cw, spot)
    disc(d, cx - cw * .42, capY - ch * .46, .20 * cw * .5, mix(spot, (255, 255, 255), .5))
    line(d, [(cx - sw * .2, base - 3), (cx - sw * .2, capY + 5)], .5, mix(stem, (255, 255, 255), .35))
    return glow(outline(im, mix(cap, (0, 0, 0), .65), .5), cap, 2.4, .55)

save(shroom(0, rgb(0xE03038), rgb(0xFFF0E0), rgb(0xF2E6D0)), 'shrink_shroom')
save(shroom(1, rgb(0x2FD9B0), rgb(0xE8FFF8), rgb(0xDCF2E8)), 'slim_shroom')
save(shroom(2, rgb(0xE86A20), rgb(0xFFE8C0), rgb(0xF0DCC0)), 'wide_shroom')
