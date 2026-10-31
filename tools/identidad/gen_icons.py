"""Vectoriza los íconos de trazo del manual (pág. 22) y los escribe como VectorDrawable."""
import sys
from pathlib import Path

import numpy as np
import potrace
from PIL import Image, ImageFilter

SRC = Path(sys.argv[1])  # carpeta con i-000..i-048 de la pág. 22
RES = Path(sys.argv[2])
UP = 4

# (posición en la página 1..24, nombre del manual, ícono del proyecto que sustituye)
MAP = [
    (1, "historial", ["ic_history"]),
    (2, "ruta-editar", ["ic_edit_road"]),
    (3, "mapa", ["ic_map"]),
    (4, "tablero", ["ic_assignment"]),
    (5, "calendario-check", ["ic_event_available"]),
    (7, "ruta", ["ic_conversion_path"]),
    (9, "ondas-de-calor", ["ic_airwave"]),
    (10, "termometro", ["ic_thermostat"]),
    (11, "advertencia", ["ic_warning"]),
    (12, "reubicar", ["ic_my_location"]),
    (13, "menos", ["ic_remove"]),
    (14, "check", ["ic_check_circle"]),
    (15, "llama", ["ic_local_fire_department"]),
    (16, "salida", ["ic_logout"]),
    (17, "bateria", ["ic_battery_5_bar"]),
    (18, "porcentaje", ["ic_timelapse"]),
    (19, "ubicacion", ["ic_pin_drop"]),
    (20, "bandera", ["ic_flag"]),
    (21, "reloj", ["ic_schedule"]),
    (22, "gota", ["ic_water_drop"]),
]


def trace(mask):
    plist = potrace.Bitmap(mask).trace(turdsize=10, alphamax=1.0, opticurve=True, opttolerance=0.8)
    k = 1.0 / UP
    parts = []
    for curve in plist:
        s = curve.start_point
        parts.append(f"M{s.x * k:.2f},{s.y * k:.2f}")
        for seg in curve.segments:
            if seg.is_corner:
                parts.append(f"L{seg.c.x * k:.2f},{seg.c.y * k:.2f}L{seg.end_point.x * k:.2f},{seg.end_point.y * k:.2f}")
            else:
                parts.append(
                    f"C{seg.c1.x * k:.2f},{seg.c1.y * k:.2f} {seg.c2.x * k:.2f},{seg.c2.y * k:.2f} "
                    f"{seg.end_point.x * k:.2f},{seg.end_point.y * k:.2f}"
                )
        parts.append("Z")
    return "".join(parts)


for pos, nombre, targets in MAP:
    mask_file = SRC / f"i-{2 * pos:03d}.png"
    img = Image.open(mask_file).convert("L")
    img = img.resize((img.width * UP, img.height * UP), Image.BICUBIC).filter(ImageFilter.GaussianBlur(3))
    data = trace(~(np.asarray(img) > 127))
    for t in targets:
        xml = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            f"<!-- Ícono «{nombre}» de la iconografía V.I.S.O.R. (Manual de identidad corporativa, pág. 22), "
            "vectorizado. El color lo pone la interfaz. -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="200"\n    android:viewportHeight="200">\n'
            '    <path android:fillColor="#FF000000" android:fillType="evenOdd"\n'
            f'        android:pathData="{data}" />\n'
            "</vector>\n"
        )
        (RES / "drawable" / f"{t}.xml").write_text(xml, encoding="utf-8")
    print(pos, nombre, len(data), "->", ",".join(targets))
