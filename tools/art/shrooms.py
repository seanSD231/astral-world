"""The three mushrooms, drawn the way Minecraft draws an item: 16x16, hard pixels, a dark
outline, three tones to a colour and nothing soft anywhere.

The old ones were 64x64 glows with blurred edges, which read as a smear next to a vanilla
mushroom in the same hotbar.

Run from the repo root:  python3 tools/art/shrooms.py
"""
import os
from PIL import Image

OUT = "src/main/resources/assets/astral_world/textures/item"
N = 16
INK = (28, 20, 24, 255)

STEM = [(118, 104, 92, 255), (168, 152, 134, 255), (214, 202, 184, 255)]

# id: (cap tones dark/mid/light, spot colour, shape)
KIND = {
    "wide_shroom":   ([(124, 54, 10, 255), (196, 92, 20, 255), (238, 142, 46, 255)], (250, 228, 190, 255), "wide"),
    "slim_shroom":   ([(16, 104, 92, 255), (30, 168, 146, 255), (96, 226, 200, 255)], (226, 255, 248, 255), "slim"),
    "shrink_shroom": ([(132, 24, 28, 255), (196, 40, 44, 255), (236, 86, 86, 255)], (248, 240, 236, 255), "small"),
}


def draw(kind):
    cap, spot, shape = KIND[kind]
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = im.load()

    def put(x, y, c):
        if 0 <= x < N and 0 <= y < N:
            d[x, y] = c

    def row(y, x0, x1, c):
        for x in range(x0, x1 + 1):
            put(x, y, c)

    if shape == "wide":
        # a broad flat cap that overhangs a short stem
        rows = [(3, 5, 10), (4, 3, 12), (5, 2, 13), (6, 1, 14), (7, 1, 14)]
        stem = [(8, 6, 9), (9, 6, 9), (10, 6, 9), (11, 5, 10), (12, 5, 10)]
        spots = [(5, 4), (4, 7), (5, 11), (6, 3), (6, 9), (6, 13)]
    elif shape == "slim":
        # tall and narrow, with a long stalk
        rows = [(1, 6, 9), (2, 5, 10), (3, 4, 11), (4, 4, 11), (5, 5, 10)]
        stem = [(6, 7, 8), (7, 7, 8), (8, 7, 8), (9, 7, 8), (10, 6, 9), (11, 6, 9), (12, 5, 10)]
        spots = [(2, 7), (3, 5), (3, 9), (4, 6), (4, 10)]
    else:
        # small and round, the classic
        rows = [(5, 6, 9), (6, 4, 11), (7, 3, 12), (8, 3, 12)]
        stem = [(9, 6, 9), (10, 6, 9), (11, 5, 10), (12, 5, 10)]
        spots = [(6, 6), (7, 4), (7, 9), (8, 7), (8, 11)]

    for (y, x0, x1) in stem:                                   # stem first, the cap sits over it
        row(y, x0, x1, STEM[1])
        put(x0, y, STEM[0])
        put(x1, y, STEM[2])
    bot = max(y for (y, _, _) in stem)
    row(bot + 1, min(x0 for (_, x0, _) in stem), max(x1 for (_, _, x1) in stem), INK)
    for (y, x0, x1) in stem:
        put(x0 - 1, y, INK)
        put(x1 + 1, y, INK)

    for (y, x0, x1) in rows:                                   # the cap: dark rim, mid field, a lit top edge
        row(y, x0, x1, cap[1])
        put(x0, y, cap[0])
        put(x0 + 1, y, cap[0])
        put(x1, y, cap[0])
        put(x1 - 1, y, cap[0])
        put(x0 - 1, y, INK)
        put(x1 + 1, y, INK)
    top = min(y for (y, _, _) in rows)
    for (y, x0, x1) in rows:
        if y == top:
            row(y - 1, x0, x1, INK)
            row(y, x0 + 2, x1 - 2, cap[2])
        if y == top + 1:
            row(y, x0 + 3, x1 - 3, cap[2])
    low = max(y for (y, _, _) in rows)
    for (y, x0, x1) in rows:
        if y == low:
            row(y + 1, x0, x1, INK)

    for (y, x) in spots:
        put(x, y, spot)
    return im


def main():
    for k in KIND:
        draw(k).save(os.path.join(OUT, k + ".png"))
    print(f"{len(KIND)} mushrooms")


if __name__ == "__main__":
    main()
