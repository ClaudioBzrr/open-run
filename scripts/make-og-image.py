#!/usr/bin/env python3
"""Compose the 1200x630 social preview from the captured app screenshots."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SHOTS = ROOT / "docs" / "assets" / "screenshots"
OUT = ROOT / "docs" / "assets" / "og-image.png"
W, H = 1200, 630


def font(size, bold=False):
    candidates = [
        "C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ]
    for candidate in candidates:
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            continue
    return ImageFont.load_default()


def rounded_phone(path, height):
    image = Image.open(path).convert("RGB")
    width = int(image.width * height / image.height)
    image = image.resize((width, height), Image.LANCZOS)
    radius = max(18, height // 18)
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, image.width, image.height), radius=radius, fill=255)
    frame = Image.new("RGB", (width + 16, height + 16), "#1d2823")
    frame.paste(image, (8, 8), mask)
    return frame, mask


def main():
    canvas = Image.new("RGB", (W, H), "#09251b")
    draw = ImageDraw.Draw(canvas)

    for y in range(H):
        t = y / H
        r = int(9 + (11 - 9) * t)
        g = int(37 + (110 - 37) * t)
        b = int(27 + (79 - 27) * t)
        draw.line((0, y, W, y), fill=(r, g, b))

    draw.ellipse((820, -220, 1420, 380), fill="#124b38")
    draw.ellipse((-220, 380, 360, 900), fill="#0d3a2a")

    draw.text((72, 64), "LOCAL-FIRST ANDROID TRACKER", font=font(18, True), fill="#8fe3bb")
    draw.text((70, 104), "Open Run", font=font(100, True), fill="#fffdf7")
    draw.text((72, 235), "Your run. Your data.", font=font(42, True), fill="#e8f8ee")
    draw.text((72, 285), "No cloud required.", font=font(42, True), fill="#b8f0d6")
    draw.text((74, 365), "Accurate GPS  ·  exact splits  ·  GPX/FIT", font=font(22), fill="#c6ddcf")
    draw.text((74, 400), "Encrypted backup  ·  no account", font=font(22), fill="#c6ddcf")

    badge_y = 480
    for label, x in (("FREE APK", 74), ("ANDROID 8+", 204), ("EN / PT", 336)):
        text_font = font(15, True)
        box = draw.textbbox((0, 0), label, font=text_font)
        width = box[2] - box[0] + 30
        draw.rounded_rectangle((x, badge_y, x + width, badge_y + 38), radius=19, outline="#8fe3bb", width=1)
        draw.text((x + 15, badge_y + 10), label, font=text_font, fill="#e8f8ee")

    if (SHOTS / "01-home.png").exists():
        front, _ = rounded_phone(SHOTS / "01-home.png", 470)
        canvas.paste(front, (800, 110))
    if (SHOTS / "03-detail.png").exists():
        back, _ = rounded_phone(SHOTS / "03-detail.png", 390)
        back = back.rotate(-8, expand=True, resample=Image.BICUBIC, fillcolor="#09251b")
        canvas.paste(back, (700, 180))
        front, _ = rounded_phone(SHOTS / "01-home.png", 470)
        canvas.paste(front, (850, 100))

    draw.text((74, 572), "github.com/ClaudioBzrr/open-run", font=font(17), fill="#9ec9b4")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(OUT, optimize=True)
    print(OUT)


if __name__ == "__main__":
    main()
