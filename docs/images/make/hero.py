"""Builds docs/images/hero.png from home.png, grades.png and archive.png (run shoot.mjs first).

    python3 docs/images/make/hero.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

IMG = Path(__file__).resolve().parent.parent
W, H = 2000, 1060
TOP, BOTTOM, RING = (230, 246, 240), (178, 228, 210), (29, 158, 117)

canvas = Image.new("RGB", (W, H))
draw = ImageDraw.Draw(canvas)
for y in range(H):  # top-left light to bottom-right mint, by row
    t = y / H
    draw.line((0, y, W, y), fill=tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM)))
# Ripples from the logo's drop, off the bottom edge.
rings = Image.new("RGBA", (W, H), (0, 0, 0, 0))
rd = ImageDraw.Draw(rings)
for r in (420, 620, 840, 1080):
    rd.ellipse((1500 - r, 1350 - r, 1500 + r, 1350 + r), outline=RING + (110,), width=5)
canvas.paste(rings, (0, 0), rings)


def card(name, width, pos, radius=26, inset=0):
    im = Image.open(IMG / name).convert("RGB")
    if inset:  # drop a backdrop margin around the card
        im = im.crop((inset, inset, im.width - inset, im.height - inset))
    im = im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1), radius=radius, fill=255)
    shadow = Image.new("RGBA", (im.width + 80, im.height + 80), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((40, 50, im.width + 40, im.height + 50), radius=radius, fill=(20, 60, 50, 70))
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    canvas.paste(shadow, (pos[0] - 40, pos[1] - 40), shadow)
    canvas.paste(im, pos, mask)


card("home.png", 520, (90, 70))
card("grades.png", 1180, (730, 70))
card("archive.png", 600, (1300, 540), radius=12, inset=32)
canvas.save(IMG / "hero.png", optimize=True)
print(IMG / "hero.png", canvas.size)
