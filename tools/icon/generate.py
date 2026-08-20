#!/usr/bin/env python3
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

SS = 4

INK = (177, 177, 177)
TILE = (44, 44, 44)

SQUIRCLE_N = 5.0
ART_FRAC = 0.72
SAFE_SCALE = 72.0 / 108.0

ARTWORK = Path(__file__).with_name('artwork.png')

LEGACY_SIZES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
ADAPTIVE_SIZES = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}

_art = None


def artwork():
    global _art
    if _art is None:
        _art = Image.open(ARTWORK).convert('L')
    return _art


def ink_mask(size):
    art = artwork()
    k = size * ART_FRAC / max(art.width, art.height)
    w, h = max(1, round(art.width * k)), max(1, round(art.height * k))
    canvas = Image.new('L', (size, size), 0)
    canvas.paste(art.resize((w, h), Image.LANCZOS), ((size - w) // 2, (size - h) // 2))
    return np.asarray(canvas, np.float32) / 255.0


def superellipse(exponent, size, n=4000):
    a = np.linspace(0, 2 * math.pi, n)
    c, sn = np.cos(a), np.sin(a)
    e = 2.0 / exponent
    r = (size - 1) / 2.0
    return list(zip(r + r * np.sign(c) * np.abs(c) ** e,
                    r + r * np.sign(sn) * np.abs(sn) ** e))


def tile_mask(kind, size):
    if kind == 'square':
        return np.ones((size, size), np.float32)
    n = size * SS
    img = Image.new('L', (n, n), 0)
    d = ImageDraw.Draw(img)
    if kind == 'circle':
        d.ellipse([0, 0, n - 1, n - 1], fill=255)
    else:
        d.polygon(superellipse(SQUIRCLE_N, n), fill=255)
    return np.asarray(img.resize((size, size), Image.LANCZOS), np.float32) / 255.0


def render(size, tile='squircle', scale=1.0, color=INK, opaque=False):
    inner = round(size * scale)
    ink = ink_mask(inner)
    rgba = np.zeros((inner, inner, 4), np.float32)
    if tile is None:
        rgba[..., 0:3] = np.array(color, np.float32) / 255.0
        rgba[..., 3] = ink
    else:
        for c in range(3):
            rgba[..., c] = TILE[c] / 255 + (color[c] - TILE[c]) / 255 * ink
        rgba[..., 3] = tile_mask(tile, inner)
    img = Image.fromarray((np.clip(rgba, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA')
    if inner != size:
        canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        canvas.alpha_composite(img, ((size - inner) // 2, (size - inner) // 2))
        img = canvas
    return img.convert('RGB') if opaque else img


def save(img, path):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix == '.webp':
        img.save(path, 'WEBP', lossless=True, quality=100, method=6)
    else:
        img.save(path)
    print('%-64s %dx%d' % (path, *img.size))


def main(root):
    root = Path(root)
    res = root / 'app/src/main/res'

    for density, size in LEGACY_SIZES.items():
        save(render(size), res / f'mipmap-{density}/ic_launcher.webp')
        save(render(size, tile='circle'), res / f'mipmap-{density}/ic_launcher_round.webp')

    for density, size in ADAPTIVE_SIZES.items():
        save(render(size, tile=None, scale=SAFE_SCALE),
             res / f'mipmap-{density}/ic_launcher_foreground.webp')
        save(render(size, tile=None, scale=SAFE_SCALE, color=(255, 255, 255)),
             res / f'mipmap-{density}/ic_launcher_monochrome.webp')

    save(render(1024), root / 'dizdar_icon.png')
    save(render(512, tile='square', opaque=True), root / 'dizdar_icon_play_512.png')


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[2])
