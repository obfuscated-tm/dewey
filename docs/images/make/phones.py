"""Lays simulator screenshots side by side in phone frames: docs/images/ios-preview.png.

    python3 docs/images/make/phones.py todo.png grades.png classes.png class.png

Take the screenshots from a Debug build launched with -DeweyDemo (made-up
student, see ios/App/Engine/Demo.swift) on an iPhone 17 Pro simulator:
    xcrun simctl launch <device> com.owenleung.classwork -DeweyDemo -DeweyTab 3   (0 To Do, 1 Grades, 3 Classes; tap a book for class.png)
    xcrun simctl io <device> screenshot todo.png
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw

BG, FRAME = (227, 244, 238), (11, 61, 51)
W, BEZEL, GAP, MARGIN = 400, 14, 40, 44
out = Path(__file__).resolve().parent.parent / "ios-preview.png"

shots = [Image.open(p).convert("RGB") for p in sys.argv[1:]]
h = round(shots[0].height * W / shots[0].width)
fw, fh = W + 2 * BEZEL, h + 2 * BEZEL
canvas = Image.new("RGB", (2 * MARGIN + len(shots) * fw + (len(shots) - 1) * GAP, 2 * MARGIN + fh), BG)
for i, s in enumerate(shots):
    x = MARGIN + i * (fw + GAP)
    frame = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle((0, 0, fw - 1, fh - 1), radius=62, fill=FRAME)
    canvas.paste(frame, (x, MARGIN), frame)
    screen = s.resize((W, h), Image.LANCZOS)
    mask = Image.new("L", (W, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, W - 1, h - 1), radius=50, fill=255)
    canvas.paste(screen, (x + BEZEL, MARGIN + BEZEL), mask)
    # Simulator screenshots only sometimes include the Dynamic Island; draw it on all.
    cx = x + fw // 2
    ImageDraw.Draw(canvas).rounded_rectangle((cx - 62, MARGIN + BEZEL + 11, cx + 62, MARGIN + BEZEL + 47), radius=18, fill=(0, 0, 0))
canvas.save(out, optimize=True)
print(out, canvas.size)
