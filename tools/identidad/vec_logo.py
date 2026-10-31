"""Vectoriza el isotipo V.I.S.O.R. del manual (pág. 9) en tres capas: círculo, tinta clara y núcleos naranja."""
import json
import sys

import numpy as np
import potrace
from PIL import Image

SRC = sys.argv[1]
OUT = sys.argv[2]

img = np.asarray(Image.open(SRC).convert("RGB")).astype(float)
H, W, _ = img.shape
NAVY = np.array([4, 24, 57.0])
OFFW = np.array([238, 238, 238.0])
ORANGE = np.array([255, 87, 34.0])

d = {n: np.linalg.norm(img - c, axis=2) for n, c in (("navy", NAVY), ("off", OFFW), ("org", ORANGE))}
cls = np.argmin(np.stack([d["navy"], d["off"], d["org"]]), axis=0)  # 0 navy, 1 off-white, 2 orange

# Círculo exterior: extremos de los píxeles navy
ys, xs = np.nonzero(cls == 0)
cx = (xs.min() + xs.max() + 1) / 2
cy = (ys.min() + ys.max() + 1) / 2
r = ((xs.max() - xs.min() + 1) + (ys.max() - ys.min() + 1)) / 4
print("círculo", cx, cy, r, file=sys.stderr)

yy, xx = np.mgrid[0:H, 0:W]
inside = (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 <= (r - 1.0) ** 2

# "Blancura" sub-píxel: proyección sobre el eje navy -> off-white (para curvas suaves)
axis = OFFW - NAVY
t = ((img - NAVY) @ axis) / (axis @ axis)
# El naranja no es tinta clara: se excluye
org = cls == 2
ink = inside & (t > 0.5) & ~org
# Los núcleos naranja (con su anillo) quedan como huecos de la tinta

# Núcleos naranja: círculos ajustados por componente
from scipy import ndimage  # noqa: E402

lab, n = ndimage.label(org & inside)
cores = []
for i in range(1, n + 1):
    m = lab == i
    a = m.sum()
    if a < 500:
        continue
    y0, x0 = ndimage.center_of_mass(m)
    cores.append({"cx": float(x0 + 0.5), "cy": float(y0 + 0.5), "r": float(np.sqrt(a / np.pi))})
cores.sort(key=lambda c: c["cx"])
print("núcleos", cores, file=sys.stderr)


def trace(mask):
    bm = potrace.Bitmap(mask)
    plist = bm.trace(turdsize=8, alphamax=1.0, opticurve=True, opttolerance=0.3)
    parts = []
    for curve in plist:
        s = curve.start_point
        parts.append(f"M{s.x:.1f},{s.y:.1f}")
        for seg in curve.segments:
            if seg.is_corner:
                parts.append(f"L{seg.c.x:.1f},{seg.c.y:.1f}L{seg.end_point.x:.1f},{seg.end_point.y:.1f}")
            else:
                parts.append(
                    f"C{seg.c1.x:.1f},{seg.c1.y:.1f} {seg.c2.x:.1f},{seg.c2.y:.1f} {seg.end_point.x:.1f},{seg.end_point.y:.1f}"
                )
        parts.append("Z")
    return "".join(parts)


path = trace(~ink)
json.dump({"size": [W, H], "circle": {"cx": float(cx), "cy": float(cy), "r": float(r)}, "cores": cores, "ink": path}, open(OUT, "w"))
print("ink path chars", len(path), file=sys.stderr)
