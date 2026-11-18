#!/usr/bin/env python3
"""Draws the six mask items: a 16x16 face plate in the mask's own colour, with hard pixels, a dark outline,
three tones and two eye slits, in the vanilla style."""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..",
                   "src/main/resources/assets/astral_world/textures/item")

# name, plate colour, cloth (strap/shadow) colour
MASKS = [
    ("black",   (0x3A, 0x38, 0x44), (0x17, 0x16, 0x1C)),
    ("white",   (0xEC, 0xEC, 0xF2), (0xB4, 0xB4, 0xC0)),
    ("crimson", (0xD2, 0x40, 0x54), (0x7A, 0x14, 0x20)),
    ("azure",   (0x4A, 0x86, 0xD8), (0x16, 0x30, 0x5E)),
    ("verdant", (0x46, 0xC0, 0x78), (0x14, 0x43, 0x2A)),
    ("gilded",  (0xE0, 0xC0, 0x60), (0x5C, 0x46, 0x12)),
]

# . empty  O outline  D dark  M mid  L light  S strap  s strap shade  E eye slit
ART = """
................
....OOOOOOOO....
...OSSSSSSSSO...
..OSsssssssSO...
..OLLLLLLLLMO...
.OLLLLLLLLLLMO..
.OLEEELLEEELMO..
.OLEEELLEEELMO..
.OLLLLLLLLLLMO..
.OMLLLLLLLLMMO..
..OMLLLLLLMMO...
..OMMLLLLMMO....
...OMMLLMMO.....
....OMMMMO......
.....OMMO.......
......OO........
""".strip("\n").split("\n")


def shade(c, k):
    return tuple(min(255, int(v * k)) for v in c)


for name, plate, cloth in MASKS:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    tones = {
        "O": (0x11, 0x0F, 0x14, 255),
        "D": shade(plate, .55) + (255,),
        "M": shade(plate, .78) + (255,),
        "L": plate + (255,),
        "S": cloth + (255,),
        "s": shade(cloth, .70) + (255,),
        "E": (0x0A, 0x09, 0x0D, 255),
    }
    for y, row in enumerate(ART):
        for x, ch in enumerate(row):
            if ch in tones:
                px[x, y] = tones[ch]
    img.save(os.path.join(OUT, "mask_%s.png" % name))
    print("mask_%s.png" % name)
