#!/usr/bin/env python3
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

U = 1024
SS = 4
N = U * SS

INK = (177, 177, 177)
TILE = (44, 44, 44)
TILE_HEX = '#2C2C2C'

SQUIRCLE_N = 5.0

SHIELD_CX = 501.0
STROKE = 37.0
SHIELD_L = 188.0
SHOULDER_R = 28.0
SHOULDER_Y = 260.0
SIDE_BOTTOM_Y = 586.0
APEX = (SHIELD_CX, 165.5)
TIP = (SHIELD_CX, 863.5)
TOP_C1 = (235.0, SHOULDER_Y)
TOP_C2 = (342.1, 250.0)
BOT_C1 = (SHIELD_L, 618.0)
BOT_C2 = (206.0, 761.9)

CELL = 170.0
CELL_R = 41.0
GAP = 36.0
GRID_CX, GRID_CY = SHIELD_CX, 486.0

LOCK_ANGLE = -20.0
LOCK_C = (744.0, 727.0)
BODY_W, BODY_H = 196.0, 154.0
BODY_R = 26.0
SHACKLE_DY = 124.0
SHACKLE_R = 51.5
SHACKLE_LEG = 47.0
KEYHOLE_R = 25.0
KEYHOLE_CY = -12.0
KEYHOLE_WAIST = 13.5
KEYHOLE_FOOT = 25.0
KEYHOLE_BOTTOM = 37.0
KNOCKOUT = 28.0
KNOCKOUT_SMOOTH = 44.0

SAFE_SCALE = 72.0 / 108.0

LEGACY_SIZES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
ADAPTIVE_SIZES = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}


def s(v):
    return v * SS


def mask():
    return Image.new('L', (N, N), 0)


def bez(p0, p1, p2, p3, n=160):
    t = np.linspace(0, 1, n)[:, None]
    p0, p1, p2, p3 = (np.array(p, float) for p in (p0, p1, p2, p3))
    pts = ((1-t)**3)*p0 + 3*((1-t)**2)*t*p1 + 3*(1-t)*t*t*p2 + (t**3)*p3
    return [tuple(p) for p in pts]


def arc(cx, cy, r, a0, a1, n=96):
    return [(cx + r*math.cos(a), cy + r*math.sin(a))
            for a in np.linspace(math.radians(a0), math.radians(a1), n)]


def densify(pts, step=1.0):
    out = []
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        n = max(1, int(math.hypot(x1-x0, y1-y0) / step))
        out += [(x0 + (x1-x0)*i/n, y0 + (y1-y0)*i/n) for i in range(n)]
    return out + [pts[-1]]


def stroke_path(img, pts, width, closed=False, round_caps=False):
    d = ImageDraw.Draw(img)
    h = s(width) / 2.0
    pts = [(s(x), s(y)) for x, y in pts]
    if closed:
        pts = pts + [pts[0]]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy)
        if L < 1e-9:
            continue
        nx, ny = -dy / L * h, dx / L * h
        d.polygon([(x0+nx, y0+ny), (x1+nx, y1+ny),
                   (x1-nx, y1-ny), (x0-nx, y0-ny)], fill=255)
    for x, y in (pts if (closed or round_caps) else pts[1:-1]):
        d.ellipse([x-h, y-h, x+h, y+h], fill=255)
    return img


def rounded_rect_pts(cx, cy, w, h, r, n=40):
    x0, y0, x1, y1 = cx-w/2, cy-h/2, cx+w/2, cy+h/2
    return (arc(x1-r, y0+r, r, -90, 0, n) + arc(x1-r, y1-r, r, 0, 90, n) +
            arc(x0+r, y1-r, r, 90, 180, n) + arc(x0+r, y0+r, r, 180, 270, n))


def fill(img, pts):
    ImageDraw.Draw(img).polygon([(s(x), s(y)) for x, y in pts], fill=255)
    return img


def local(pts):
    a = math.radians(LOCK_ANGLE)
    ca, sa = math.cos(a), math.sin(a)
    return [(LOCK_C[0] + x*ca - y*sa, LOCK_C[1] + x*sa + y*ca) for x, y in pts]


def superellipse(exponent, n=4000):
    a = np.linspace(0, 2*math.pi, n)
    c, sn = np.cos(a), np.sin(a)
    e = 2.0 / exponent
    return list(zip(512 + 512*np.sign(c)*np.abs(c)**e,
                    512 + 512*np.sign(sn)*np.abs(sn)**e))


def shield_path():
    top = bez((215.0, SHOULDER_Y), TOP_C1, TOP_C2, APEX)
    bot = bez((SHIELD_L, SIDE_BOTTOM_Y), BOT_C1, BOT_C2, TIP)
    fillet = arc(SHIELD_L + SHOULDER_R, SHOULDER_Y + SHOULDER_R, SHOULDER_R, 270, 180, 40)
    left = list(reversed(top)) + fillet + [(SHIELD_L, SIDE_BOTTOM_Y)] + bot[1:]
    right = [(2*SHIELD_CX - x, y) for x, y in reversed(left)]
    return densify(left + right[1:])


def shield(dist):
    pts = shield_path()
    clear = s(KNOCKOUT + STROKE / 2)
    keep = [dist[int(s(y)), int(s(x))] >= clear for x, y in pts]
    cut = next(i for i in range(len(keep)) if keep[i] and not keep[i-1])
    order, keep = pts[cut:] + pts[:cut], keep[cut:] + keep[:cut]
    return stroke_path(mask(), [p for p, k in zip(order, keep) if k],
                       STROKE, round_caps=True)


def grid():
    m = mask()
    step = (CELL + GAP) / 2
    for sx in (-1, 1):
        for sy in (-1, 1):
            fill(m, rounded_rect_pts(GRID_CX + sx*step, GRID_CY + sy*step,
                                     CELL, CELL, CELL_R))
    return m


def padlock():
    m = mask()
    top = arc(0, -SHACKLE_DY, SHACKLE_R, 180, 360, 120)
    stroke_path(m, local([(-SHACKLE_R, -SHACKLE_DY + SHACKLE_LEG)] + top +
                         [(SHACKLE_R, -SHACKLE_DY + SHACKLE_LEG)]), STROKE)
    fill(m, local(rounded_rect_pts(0, 0, BODY_W, BODY_H, BODY_R)))
    return m


def keyhole():
    m = mask()
    fill(m, local(arc(0, KEYHOLE_CY, KEYHOLE_R, 0, 360, 160)))
    y = KEYHOLE_CY + math.sqrt(KEYHOLE_R**2 - KEYHOLE_WAIST**2) - 2
    fill(m, local([(-KEYHOLE_WAIST, y), (KEYHOLE_WAIST, y),
                   (KEYHOLE_FOOT, KEYHOLE_BOTTOM), (-KEYHOLE_FOOT, KEYHOLE_BOTTOM)]))
    return m


def arr(img):
    return np.array(img, dtype=np.float32) / 255.0


_ink = None


def ink_layer():
    global _ink
    if _ink is None:
        lock = padlock()
        dist = ndimage.distance_transform_edt(np.array(lock) <= 127)
        halo = ndimage.distance_transform_edt(~(dist <= s(KNOCKOUT))) <= s(KNOCKOUT_SMOOTH)
        halo = ndimage.distance_transform_edt(halo) > s(KNOCKOUT_SMOOTH)
        m = np.maximum(np.where(halo, 0.0, arr(grid())), arr(shield(dist)))
        _ink = np.clip(np.maximum(m, arr(lock)) - arr(keyhole()), 0, 1)
    return _ink


def tile_layer(kind):
    if kind == 'square':
        return np.ones((N, N), np.float32)
    if kind == 'circle':
        return arr(fill(mask(), arc(512, 512, 512, 0, 360, 4000)))
    return arr(fill(mask(), superellipse(SQUIRCLE_N)))


def render(size, tile='squircle', scale=1.0, color=INK, opaque=False):
    ink = ink_layer()
    rgba = np.zeros((N, N, 4), np.float32)
    if tile is None:
        rgba[..., 0:3] = np.array(color, np.float32) / 255.0
        rgba[..., 3] = ink
    else:
        for c in range(3):
            rgba[..., c] = TILE[c]/255 + (color[c]-TILE[c])/255 * ink
        rgba[..., 3] = tile_layer(tile)
    img = Image.fromarray((np.clip(rgba, 0, 1)*255 + 0.5).astype(np.uint8), 'RGBA')
    inner = round(size * scale)
    img = img.resize((inner, inner), Image.LANCZOS)
    if inner != size:
        canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        canvas.alpha_composite(img, ((size-inner)//2, (size-inner)//2))
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
