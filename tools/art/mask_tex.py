"""The mask item icons: a Venetian masquerade mask in cut filigree, one per colour.

Sixteen pixels square leaves no room for real lace, so the silhouette has to carry it: two lobes
over the eyes with the openings cut clean out, a crest standing off the bridge, points swept back
at the temples, and stones set along the brow. The lace is the same on every mask and only the
glitter changes colour, as the real ones do.
"""
from PIL import Image
import os

OUT = 'src/main/resources/assets/astral_world/textures/item'
FIL = (12, 11, 17, 255)
GEM = (238, 242, 255, 255)
TRIM = {'black': (0x5A, 0x57, 0x6A), 'white': (0xF2, 0xF2, 0xF8), 'crimson': (0xD2, 0x40, 0x54),
        'azure': (0x4A, 0x86, 0xD8), 'verdant': (0x46, 0xC0, 0x78), 'gilded': (0xE0, 0xC0, 0x60)}
N = 16


def ell(x, y, cx, cy, rx, ry):
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0


def shape():
    body = [[False] * N for _ in range(N)]
    hole = [[False] * N for _ in range(N)]
    crest = [[False] * N for _ in range(N)]
    for y in range(N):
        for x in range(N):
            px, py = x + .5, y + .5
            lobe = ell(px, py, 4.4, 8.4, 4.6, 3.3) or ell(px, py, 11.6, 8.4, 4.6, 3.3)
            bridge = abs(px - 8) <= 1.7 and 6.0 <= py <= 10.8
            if (lobe or bridge) and 5.4 <= py <= 12.6:
                body[y][x] = True
            if ell(px, py, 4.4, 8.8, 2.3, 1.7) or ell(px, py, 11.6, 8.8, 2.3, 1.7):
                hole[y][x] = True
            stem = abs(px - 8) <= .9 and 4.0 <= py <= 6.2          # a fleur off the bridge:
            bulb = ell(px, py, 8, 3.0, 1.7, 1.7)                   # stem, bulb and point
            tip = abs(px - 8) <= .5 and 1.0 <= py <= 1.8
            if stem or bulb or tip:
                crest[y][x] = True
    return body, hole, crest


def build(name):
    tr = TRIM[name] + (255,)
    body, hole, crest = shape()
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    px = im.load()
    on = [[False] * N for _ in range(N)]
    for y in range(N):
        for x in range(N):
            if (body[y][x] and not hole[y][x]) or crest[y][x]:
                on[y][x] = True
                px[x, y] = FIL
    # glitter: the rim of the thing, and the edge of each eye opening
    for y in range(N):
        for x in range(N):
            if not on[y][x]:
                continue
            edge = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if nx < 0 or ny < 0 or nx >= N or ny >= N or not on[ny][nx]:
                    edge = True
            if edge and (y <= 6 or hole[y][x - 1 if x else x] or any(
                    0 <= x + d < N and hole[y][x + d] for d in (-1, 1)) or any(
                    0 <= y + d < N and hole[y + d][x] for d in (-1, 1))):
                px[x, y] = tr
    # the stones, set along the brow either side of the bridge
    for x, y in ((2, 6), (4, 6), (5, 6), (10, 6), (11, 6), (13, 6)):
        if on[y][x]:
            px[x, y] = GEM
    im.save(os.path.join(OUT, 'mask_%s.png' % name), optimize=True)


for n in TRIM:
    build(n)
Image.open(os.path.join(OUT, 'mask_azure.png')).resize((256, 256), Image.NEAREST).save('/tmp/mask.png')
print('ok')
