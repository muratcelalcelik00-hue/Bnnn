#!/usr/bin/env python3
"""Draws the mod's own small textures (not taken from Polyfield): ammo icons,
the muzzle flash, a white texture for flat-coloured parts and the mod icon."""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "polyfield")
BRASS = (201, 154, 64, 255)
BRASS_D = (140, 98, 38, 255)
BRASS_L = (236, 198, 112, 255)
TIP = (150, 72, 48, 255)
LEAD = (110, 110, 120, 255)


def save(img, rel):
    p = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def bullet(d, x, y, length, tip=LEAD):
    """Vertical cartridge with its base at (x, y)."""
    for i in range(length):
        yy = y - i
        if i < length - 3:
            d.point((x, yy), BRASS_D)
            d.point((x + 1, yy), BRASS)
            d.point((x + 2, yy), BRASS_L if i % 3 else BRASS)
        else:
            d.point((x + 1, yy), tip)
            if i == length - 3:
                d.point((x, yy), tip)
                d.point((x + 2, yy), tip)


def ammo_pistol():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i, x in enumerate((2, 6, 10)):
        bullet(d, x, 13 - (i % 2), 7)
    return img


def ammo_rifle():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((1, 13, 14, 14), fill=(70, 70, 70, 255))
    for x in (1, 4, 7, 10):
        bullet(d, x, 12, 11, TIP)
    return img


def ammo_belt():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i in range(5):
        x = 1 + i * 3
        y = 14 - i
        d.line((x, y, x + 2, y), fill=(60, 60, 60, 255))
        bullet(d, x, y - 1, 8, TIP)
    return img


def rocket():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    olive = (88, 98, 52, 255)
    dark = (52, 58, 30, 255)
    for i in range(10):
        x, y = 3 + i, 12 - i
        d.line((x - 1, y, x + 1, y), fill=olive)
        d.point((x, y + 1), dark)
    d.polygon([(12, 1), (14, 1), (14, 3)], fill=(150, 60, 40, 255))
    d.line((1, 14, 4, 11), fill=(70, 70, 70, 255))
    d.point((1, 12), (70, 70, 70, 255))
    d.point((3, 14), (70, 70, 70, 255))
    return img


def flash():
    n = 64
    img = Image.new("RGB", (n, n), (0, 0, 0))
    px = img.load()
    for y in range(n):
        for x in range(n):
            dx, dy = (x - n / 2 + 0.5) / (n / 2), (y - n / 2 + 0.5) / (n / 2)
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            star = 0.55 + 0.45 * abs(math.cos(a * 3.0)) ** 6
            v = max(0.0, 1.0 - r / star) ** 1.6
            core = max(0.0, 1.0 - r / 0.28)
            px[x, y] = (min(255, int(255 * (v + core))), min(255, int(210 * v + 255 * core)), min(255, int(120 * v * v + 230 * core)))
    return img.filter(ImageFilter.GaussianBlur(0.8))


def icon():
    n = 128
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((4, 4, n - 5, n - 5), radius=22, fill=(58, 66, 44, 255), outline=(30, 34, 22, 255), width=4)
    d.polygon([(24, 70), (88, 58), (104, 56), (104, 62), (90, 66), (60, 74), (58, 84), (48, 86), (46, 76), (24, 80)],
              fill=(30, 30, 30, 255))
    d.polygon([(20, 72), (36, 69), (38, 82), (22, 88)], fill=(120, 60, 30, 255))
    for i in range(3):
        d.rectangle((30 + i * 20, 98, 40 + i * 20, 104), fill=(201, 154, 64, 255))
    return img


if __name__ == "__main__":
    save(Image.new("RGBA", (16, 16), (255, 255, 255, 255)), "textures/misc/white.png")
    save(flash(), "textures/misc/muzzle_flash.png")
    save(ammo_pistol(), "textures/item/pistol_ammo.png")
    save(ammo_rifle(), "textures/item/rifle_ammo.png")
    save(ammo_belt(), "textures/item/mg_belt.png")
    save(rocket(), "textures/item/rocket.png")
    save(icon(), "icon.png")
    print("textures written to", OUT)
