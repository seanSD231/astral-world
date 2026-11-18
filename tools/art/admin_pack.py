#!/usr/bin/env python3
"""The 64x64 inventory icon for the admin pack: a black and gold packet with the Bearing of Sins on it."""
import os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "..",
                   "src/main/resources/assets/astral_world/textures/item")

BLACK = (12, 8, 11, 255)
BODY = (28, 14, 18, 255)
BODY2 = (44, 20, 26, 255)
GOLD = (198, 158, 78, 255)
GOLD_HI = (240, 206, 128, 255)
RED = (190, 28, 36, 255)
RED_HI = (240, 74, 70, 255)
WHITE = (248, 238, 232, 255)


def build():
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    L, R, T, B = 10, 53, 4, 59

    d.rectangle([L, T + 4, R, B - 4], fill=BODY)                       # the packet
    for x in range(L, R + 1, 3):                                       # serrated crimp, top and bottom
        d.rectangle([x, T, x + 1, T + 4], fill=GOLD)
        d.rectangle([x, B - 4, x + 1, B], fill=GOLD)
    d.rectangle([L, T + 4, R, T + 5], fill=GOLD_HI)
    d.rectangle([L, B - 5, R, B - 4], fill=GOLD_HI)

    for x in range(L + 2, R - 1, 7):                                   # a sheen down the foil
        d.rectangle([x, T + 6, x, B - 6], fill=BODY2)

    d.rectangle([L + 2, T + 7, R - 2, B - 7], outline=GOLD, width=1)   # the frame
    d.rectangle([L + 4, T + 9, R - 4, T + 16], fill=BLACK, outline=GOLD, width=1)   # the title plate
    for x in range(L + 6, R - 5, 2):
        d.rectangle([x, T + 12, x, T + 13], fill=GOLD_HI)

    cx, cy = 32, 34                                                    # the mark: a cross with a bloom over it
    d.ellipse([cx - 11, cy - 11, cx + 11, cy + 11], fill=(52, 12, 18, 255), outline=RED, width=1)
    d.rectangle([cx - 2, cy - 9, cx + 2, cy + 9], fill=RED)
    d.rectangle([cx - 9, cy - 2, cx + 9, cy + 2], fill=RED)
    for (ox, oy) in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
        d.ellipse([cx + ox - 3, cy + oy - 3, cx + ox + 3, cy + oy + 3], fill=RED_HI)
    d.rectangle([cx - 1, cy - 4, cx + 1, cy + 4], fill=WHITE)
    d.rectangle([cx - 4, cy - 1, cx + 4, cy + 1], fill=WHITE)

    d.rectangle([L + 9, B - 14, R - 9, B - 8], fill=BLACK, outline=GOLD, width=1)   # the count plate
    for x in range(L + 11, R - 9, 2):
        d.rectangle([x, B - 12, x, B - 11], fill=GOLD_HI)
    return im


if __name__ == "__main__":
    build().save(os.path.join(OUT, "admin_pack.png"))
    print("admin_pack.png")
