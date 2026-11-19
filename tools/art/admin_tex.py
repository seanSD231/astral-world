"""The admin card's own textures, written white on transparent so the renderer tints them.

The Bearing of Sins scene used LOTM's particle textures for its circles and halos, which is
a bet on which build of LOTM is installed; where they are missing the game draws a plain
square, which is what the floor circles turned into. These are ours.

Run: python3 tools/art/admin_tex.py
"""
import os, math
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '../../src/main/resources/assets/astral_world/textures/holo')
S = 512
C = S / 2.0


def canvas():
    im = Image.new('L', (S, S), 0)
    return im, ImageDraw.Draw(im)


def ring(d, r, w, v=255):
    d.ellipse((C - r, C - r, C + r, C + r), outline=v, width=max(1, int(round(w))))


def spokes(d, n, r0, r1, w, phase=0.0, v=255):
    for i in range(n):
        a = phase + i * math.tau / n
        d.line((C + math.cos(a) * r0, C + math.sin(a) * r0,
                C + math.cos(a) * r1, C + math.sin(a) * r1), fill=v, width=max(1, int(round(w))))


def star(d, n, step, r, w, phase=0.0, v=255):
    """An n-pointed star polygon {n/step}, the kind that sits inside a magic circle."""
    pts = [(C + math.cos(phase + i * math.tau / n) * r, C + math.sin(phase + i * math.tau / n) * r) for i in range(n)]
    k = 0
    for _ in range(n):
        j = (k + step) % n
        d.line((pts[k][0], pts[k][1], pts[j][0], pts[j][1]), fill=v, width=max(1, int(round(w))))
        k = j


def glyphs(d, n, r, size, v=255):
    """Short marks round a band: three or four strokes each, which read as writing at this size."""
    for i in range(n):
        a = i * math.tau / n
        cx, cy = C + math.cos(a) * r, C + math.sin(a) * r
        ux, uy = math.cos(a + math.pi / 2), math.sin(a + math.pi / 2)
        vx, vy = math.cos(a), math.sin(a)
        for k in (-1, 0, 1):
            if (i + k) % 3 == 0:
                continue
            ox, oy = ux * k * size * .34, uy * k * size * .34
            d.line((cx + ox - vx * size * .5, cy + oy - vy * size * .5,
                    cx + ox + vx * size * .5, cy + oy + vy * size * .5), fill=v, width=2)
        d.line((cx - ux * size * .55, cy - uy * size * .55, cx + ux * size * .55, cy + uy * size * .55),
               fill=v, width=2)


def write(name, alpha, bloom=0.0, boost=1.0):
    a = alpha
    if bloom:
        a = Image.eval(a, lambda t: t)
        g = a.filter(ImageFilter.GaussianBlur(bloom)).point(lambda t: min(255, int(t * 1.9)))
        a = Image.eval(Image.composite(Image.new('L', (S, S), 255), g, a.point(lambda t: 255 if t > 160 else 0)),
                       lambda t: min(255, int(t * boost)))
    white = Image.new('L', (S, S), 255)
    Image.merge('RGBA', (white, white, white, a)).save(os.path.join(OUT, name), optimize=True)
    print(name)


# ---- the great spell circle on the ground --------------------------------------------------
im, d = canvas()
ring(d, S * .485, 3)
ring(d, S * .455, 6)
ring(d, S * .40, 2)
glyphs(d, 44, S * .427, S * .038)
ring(d, S * .355, 3)
spokes(d, 22, S * .355, S * .40, 2)
star(d, 7, 3, S * .34, 4)
ring(d, S * .245, 2)
glyphs(d, 22, S * .215, S * .034)
ring(d, S * .185, 4)
star(d, 7, 2, S * .17, 3, math.pi / 7)
ring(d, S * .075, 3)
spokes(d, 7, S * .075, S * .17, 2, math.pi / 7)
write('adm_circle.png', im, bloom=1.6, boost=1.0)

# ---- a plain band of writing, for the circles that turn against it --------------------------
im, d = canvas()
ring(d, S * .47, 4)
ring(d, S * .40, 4)
glyphs(d, 36, S * .435, S * .05)
spokes(d, 36, S * .40, S * .47, 1)
write('adm_runes.png', im, bloom=1.4)

# ---- the halo band: a bright line in a soft glow, which is what a halo of light looks like ---
im, d = canvas()
ring(d, S * .42, 5)
core = im.copy()
glow = im.filter(ImageFilter.GaussianBlur(S * .035)).point(lambda t: min(255, int(t * 4.2)))
a = Image.composite(Image.new('L', (S, S), 255), glow, core.point(lambda t: 255 if t > 120 else 0))
white = Image.new('L', (S, S), 255)
Image.merge('RGBA', (white, white, white, a)).save(os.path.join(OUT, 'adm_halo.png'), optimize=True)
print('adm_halo.png')

# ---- rays, for the light coming down out of the cloud ---------------------------------------
im, d = canvas()
for i in range(28):
    a0 = i * math.tau / 28 + (i % 3) * .021
    wdt = .010 + .016 * ((i * 7) % 5) / 4.0
    far = S * (.30 + .22 * ((i * 11) % 7) / 6.0)
    pts = [(C, C),
           (C + math.cos(a0 - wdt) * far, C + math.sin(a0 - wdt) * far),
           (C + math.cos(a0 + wdt) * far, C + math.sin(a0 + wdt) * far)]
    d.polygon(pts, fill=255)
im = im.filter(ImageFilter.GaussianBlur(S * .012))
yy = Image.new('L', (S, S), 0)
ImageDraw.Draw(yy).ellipse((C - S * .5, C - S * .5, C + S * .5, C + S * .5), fill=255)
fade = yy.filter(ImageFilter.GaussianBlur(S * .13))
im = Image.composite(im, Image.new('L', (S, S), 0), fade.point(lambda t: 255 if t > 100 else 0))
white = Image.new('L', (S, S), 255)
Image.merge('RGBA', (white, white, white, im)).save(os.path.join(OUT, 'adm_rays.png'), optimize=True)
print('adm_rays.png')


# ---- the rainbow sea: the Hanged Man's own swirl, with its middle cut out ---------------------
# The sea belongs outside the spell circles, and a full disc of it paints over the grass they
# are supposed to stand on. One texture, masked to an annulus, is simpler than tiling quads
# round a ring and leaves no seams.
src = Image.open(os.path.join(OUT, 'rainbow_swirl.png')).convert('RGBA')
N = src.size[0]
yy, xx = [[(i - N / 2.0) for i in range(N)]], None
import numpy as np
gy, gx = np.mgrid[0:N, 0:N]
d = np.sqrt((gx - N / 2.0) ** 2 + (gy - N / 2.0) ** 2) / (N / 2.0)
# clear inside .46 of the radius, fade in to full by .60, and keep the outer edge's own falloff
k = np.clip((d - 0.46) / 0.14, 0, 1) ** 1.3
a = np.asarray(src)[..., 3].astype(np.float32) * k
out = np.asarray(src).copy()
out[..., 3] = np.clip(a, 0, 255).astype(np.uint8)
Image.fromarray(out, 'RGBA').save(os.path.join(OUT, 'adm_sea.png'), optimize=True)
print('adm_sea.png')
