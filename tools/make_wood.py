#!/usr/bin/env python3
"""
Makes the Walnut and Oak backgrounds (app/src/main/res/drawable-nodpi/wood_*.webp).

The sources are CC0 veneer scans from Poly Haven (https://polyhaven.com). Raw veneer
is pale; a hi-fi plinth is oiled, which darkens and warms it and deepens the figure.
So each scan is cropped to a phone-sized piece of wood (45 cm across, the grain running
parallel to the front edge), shifted to the oiled color, and its figure strengthened.

Needs numpy and Pillow:  python3 tools/make_wood.py
"""
import io
import json
import pathlib
import urllib.request

import numpy as np
from PIL import Image

OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi"
SIZE = (1080, 2340)                 # a phone screen; larger ones scale it up a little

#        Poly Haven asset           metres across  oiled color (sRGB)  figure  WebP quality
WOODS = {
    "walnut": ("black_walnut_veneer_02", 0.45, (78, 53, 37), 1.40, 88),
    "oak": ("red_oak_veneer", 0.45, (204, 172, 128), 1.35, 85),
}
SCAN_WIDTH_M = 1.0                  # both scans show a metre of veneer


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "PLVinyl tools/make_wood.py"})
    with urllib.request.urlopen(req) as r:
        return r.read()


def to_linear(a):
    return np.where(a <= 0.04045, a / 12.92, ((a + 0.055) / 1.055) ** 2.4)


def to_srgb(a):
    a = np.clip(a, 0, 1)
    return np.where(a <= 0.0031308, a * 12.92, 1.055 * a ** (1 / 2.4) - 0.055)


def make(asset: str, width_m: float, oiled: tuple, figure: float) -> Image.Image:
    files = json.loads(fetch(f"https://api.polyhaven.com/files/{asset}"))
    scan = Image.open(io.BytesIO(fetch(files["Diffuse"]["4k"]["jpg"]["url"]))).convert("RGB")

    # A piece of wood the size of the deck, from the middle of the scan.
    cw = int(width_m * scan.width / SCAN_WIDTH_M)
    ch = int(cw * SIZE[1] / SIZE[0])
    x0, y0 = (scan.width - cw) // 2, (scan.height - ch) // 2
    piece = scan.crop((x0, y0, x0 + cw, y0 + ch)).resize(SIZE, Image.LANCZOS)

    # Oil it: the average shifts to the oiled color, the figure deepens (in linear light).
    a = to_linear(np.asarray(piece).astype(np.float32) / 255)
    a *= to_linear(np.array(oiled, np.float32) / 255) / a.reshape(-1, 3).mean(0)
    lum = (a @ np.array([0.2126, 0.7152, 0.0722], np.float32))[..., None]
    a *= (lum / lum.mean()) ** (figure - 1)
    return Image.fromarray((to_srgb(a) * 255 + 0.5).astype(np.uint8))


if __name__ == "__main__":
    for name, (asset, width_m, oiled, figure, quality) in WOODS.items():
        out = OUT / f"wood_{name}.webp"
        make(asset, width_m, oiled, figure).save(out, "WEBP", quality=quality, method=6)
        print(f"{out.name}: {out.stat().st_size // 1024} KB")
