"""Draws the card binder item: an oxblood leather cover with brass corners, a clasp, and card edges showing."""
from PIL import Image

W = 16
im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
px = im.load()

LEATHER_D = (74, 30, 36, 255)
LEATHER   = (104, 44, 50, 255)
LEATHER_L = (134, 62, 66, 255)
BRASS     = (232, 192, 74, 255)
BRASS_D   = (158, 124, 40, 255)
PAGE      = (226, 216, 196, 255)
PAGE_D    = (176, 166, 148, 255)
SPINE     = (58, 22, 28, 255)

# cover, 2..14 across and 1..15 down, with the spine down the left
for y in range(1, 15):
    for x in range(2, 14):
        px[x, y] = LEATHER_D if (x + y) % 7 == 0 else LEATHER
for y in range(1, 15):
    px[2, y] = SPINE
    px[3, y] = LEATHER_D
# a highlight along the top edge of the cover
for x in range(4, 13):
    px[x, 1] = LEATHER_L

# the page block peeking out on the right, three card edges
for y in range(3, 13):
    px[13, y] = PAGE if y % 3 else PAGE_D
px[13, 2] = PAGE_D
px[13, 13] = PAGE_D

# brass corners
for x, y in ((4, 2), (5, 2), (4, 3), (11, 2), (12, 2), (12, 3),
             (4, 13), (4, 12), (5, 13), (11, 13), (12, 13), (12, 12)):
    px[x, y] = BRASS
for x, y in ((5, 3), (11, 3), (5, 12), (11, 12)):
    px[x, y] = BRASS_D

# the clasp across the middle of the fore-edge
for y in (7, 8):
    px[12, y] = BRASS
    px[13, y] = BRASS
px[11, 7] = BRASS_D
px[11, 8] = BRASS_D

# a small card face embossed on the cover
for y in range(5, 11):
    for x in range(6, 10):
        px[x, y] = BRASS_D if (x in (6, 9) or y in (5, 10)) else LEATHER_D
px[7, 7] = BRASS
px[8, 8] = BRASS

im.save("src/main/resources/assets/astral_world/textures/item/card_binder.png")
print("wrote card_binder.png")
