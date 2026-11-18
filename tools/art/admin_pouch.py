#!/usr/bin/env python3
"""The 564x564 opening-screen pouch for the admin pack.

The other pouches are hand-made art and far better than anything drawn from scratch here, so this one is built from
one of them: the Secrets Supplicant pack, which already carries the Hanged Man's rose-and-cross in its middle. The
body is taken down toward black while the gold and the symbols keep their brightness, and the three pieces of text
are replaced.

Run from the repo root:  python3 tools/art/admin_pouch.py
"""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont

GUI = "src/main/resources/assets/astral_world/textures/gui/tarot"
BASE = os.path.join(GUI, "pack_secrets_supplicant.png")
OUT = os.path.join(GUI, "pack_admin.png")

SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
GOLD = (206, 164, 82)
GOLD_HI = (248, 216, 140)

TITLE_PLATE = (108, 78, 468, 138)      # inside the gold edge of the top plate
SUB_STRIP = (112, 430, 452, 466)       # the pathway name, sitting straight on the body
FOOT_PLATE = (220, 472, 362, 504)      # the "3 CARDS" plate


def font(size):
    return ImageFont.truetype(SERIF, size)


def darken(img):
    """Push the crimson body toward black and leave the gold and the symbols where they are: a curve that bites
    hardest in the midtones and barely touches the highlights."""
    a = np.asarray(img, np.float32)
    rgb, al = a[:, :, :3], a[:, :, 3:4]
    v = rgb / 255.0
    k = 0.30 + 0.70 * np.power(v, 1.9)
    rgb = rgb * k
    rgb[:, :, 1] *= 0.88                       # a blacker red, not a brown one
    rgb[:, :, 2] *= 0.86
    return Image.fromarray(np.concatenate([np.clip(rgb, 0, 255), al], axis=2).astype(np.uint8), "RGBA")


def plate_fill(img, box, pad=3):
    """The flattest dark colour inside a plate, so it can be painted out without touching its border."""
    x0, y0, x1, y1 = box
    crop = np.asarray(img.convert("RGB").crop((x0 + pad, y0 + pad, x1 - pad, y1 - pad)), np.int32)
    flat = crop.reshape(-1, 3)
    lum = flat.sum(axis=1)
    return tuple(int(c) for c in flat[np.argsort(lum)[len(lum) // 8]])


def plate(d, box, fill, r=10):
    """Repaint a plate's inside and put its gold edge back: the darkening takes the edge down with everything else."""
    d.rounded_rectangle(list(box), r, fill=fill + (255,), outline=GOLD + (255,), width=4)
    d.rounded_rectangle([box[0] + 7, box[1] + 7, box[2] - 7, box[3] - 7], max(2, r - 5), outline=GOLD_HI + (120,), width=1)


def centred(d, text, box, f, fill, shadow=(0, 0, 0, 190), dy=0):
    x0, y0, x1, y1 = box
    w = d.textlength(text, font=f)
    bb = f.getbbox(text)
    x = (x0 + x1) / 2 - w / 2
    y = (y0 + y1) / 2 - (bb[3] + bb[1]) / 2 + dy
    d.text((x + 2, y + 2), text, font=f, fill=shadow)
    d.text((x, y), text, font=f, fill=fill)


def build():
    img = darken(Image.open(BASE).convert("RGBA"))
    d = ImageDraw.Draw(img, "RGBA")

    # the title
    plate(d, TITLE_PLATE, plate_fill(img, TITLE_PLATE))
    centred(d, "ADMIN PACK", TITLE_PLATE, font(44), (255, 238, 192, 255))

    # the name, straight on the body: repaint the strip from a clean row under it, then letter it
    row = img.crop((SUB_STRIP[0], SUB_STRIP[3] + 4, SUB_STRIP[2], SUB_STRIP[3] + 5))
    img.paste(row.resize((SUB_STRIP[2] - SUB_STRIP[0], SUB_STRIP[3] - SUB_STRIP[1])), (SUB_STRIP[0], SUB_STRIP[1]))
    d = ImageDraw.Draw(img, "RGBA")
    centred(d, "BEARER OF SINS", SUB_STRIP, font(30), (238, 62, 58, 255))

    # the count
    plate(d, FOOT_PLATE, plate_fill(img, FOOT_PLATE), r=8)
    centred(d, "1 CARD", FOOT_PLATE, font(23), (248, 240, 228, 255))
    return img


if __name__ == "__main__":
    build().save(OUT)
    print("pack_admin.png")
