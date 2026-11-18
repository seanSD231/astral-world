#!/usr/bin/env python3
"""The inventory sprite for every card pack: a 188px miniature of that pack's own pouch art.

This is how the sprites were always made, and it is what they go back to. A spell of hand-drawn 64px pixel art
replaced them for a few versions and was worse: the whole point of the sprite is that it is recognisably the pouch
you are about to tear open.

Run from the repo root:  python3 tools/art/pack_sprites.py
"""
import os
from PIL import Image

GUI = "src/main/resources/assets/astral_world/textures/gui/tarot"
OUT = "src/main/resources/assets/astral_world/textures/item"
SIZE = 188


def main():
    made = 0
    for f in sorted(os.listdir(GUI)):
        if not f.startswith("pack_") or not f.endswith(".png"):
            continue
        name = f[len("pack_"):-len(".png")] + "_pack.png"
        Image.open(os.path.join(GUI, f)).convert("RGBA") \
             .resize((SIZE, SIZE), Image.LANCZOS) \
             .save(os.path.join(OUT, name))
        made += 1
    print(f"{made} pack sprites")


if __name__ == "__main__":
    main()
