"""Genera los VectorDrawable del isotipo V.I.S.O.R. a partir de iso.json."""
import json
import sys
from pathlib import Path

RES = Path(sys.argv[1])
d = json.load(open(Path(__file__).with_name("iso.json")))
c, cores, ink = d["circle"], d["cores"], d["ink"]
R = c["r"]
SIZE = 2 * R
X0, Y0 = c["cx"] - R, c["cy"] - R

NAVY, OFFW, ORANGE = "#FF041839", "#FFEEEEEE", "#FFFF5722"


def circle_path(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f}a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0Z"


circle = circle_path(c["cx"], c["cy"], R)
cores_path = "".join(circle_path(o["cx"], o["cy"], o["r"] + 1) for o in cores)


def vector(width, height, vw, vh, body, comment):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- {comment} -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{width}dp"\n    android:height="{height}dp"\n'
        f'    android:viewportWidth="{vw}"\n    android:viewportHeight="{vh}">\n{body}</vector>\n'
    )


def group(inner, scale, tx, ty):
    return (
        f'    <group android:scaleX="{scale:.5f}" android:scaleY="{scale:.5f}" '
        f'android:translateX="{tx:.3f}" android:translateY="{ty:.3f}">\n{inner}    </group>\n'
    )


def path(data, color, even_odd=False):
    ft = ' android:fillType="evenOdd"' if even_odd else ""
    return f'        <path android:fillColor="{color}"{ft}\n            android:pathData="{data}" />\n'


def isotype(circle_color, ink_color):
    return path(circle, circle_color) + path(ink, ink_color, True) + path(cores_path, ORANGE)


# Isotipo recortado a su círculo (viewport = caja del círculo). `scale` 1 y traslado a origen.
def standalone(circle_color, ink_color, comment):
    body = group(isotype(circle_color, ink_color), 1.0, -X0, -Y0)
    return vector(96, 96, f"{SIZE:.2f}", f"{SIZE:.2f}", body, comment)


COMMENT = "Isotipo V.I.S.O.R. vectorizado del Manual de identidad corporativa (pág. 9). No alterar colores ni proporciones (pág. 16)."
(RES / "drawable").mkdir(parents=True, exist_ok=True)
(RES / "drawable/ic_visor_isotipo.xml").write_text(
    standalone(NAVY, OFFW, COMMENT + " Para fondos claros."), encoding="utf-8"
)
(RES / "drawable/ic_visor_isotipo_inverso.xml").write_text(
    standalone(OFFW, NAVY, COMMENT + " Para fondos oscuros (azul Black Blue)."), encoding="utf-8"
)


def centered(diameter, canvas):
    s = diameter / SIZE
    return s, canvas / 2 - s * c["cx"], canvas / 2 - s * c["cy"]


# Ícono adaptativo: lienzo de 108 dp, zona segura de 66 dp (círculo de 62 dp).
s, tx, ty = centered(62, 108)
(RES / "drawable/ic_launcher_foreground.xml").write_text(
    vector(108, 108, 108, 108, group(isotype(OFFW, NAVY), s, tx, ty), COMMENT + " Primer plano del ícono del launcher."),
    encoding="utf-8",
)
mono = path(circle + ink, "#FF000000", True) + path(cores_path, "#FF000000")
(RES / "drawable/ic_launcher_monochrome.xml").write_text(
    vector(108, 108, 108, 108, group(mono, s, tx, ty), COMMENT + " Capa monocromática (Android 13+)."),
    encoding="utf-8",
)

# Splash (Android 12+): lienzo de 288 dp, visible el círculo de 192 dp.
s2, tx2, ty2 = centered(186, 288)
(RES / "drawable/ic_splash_isotipo.xml").write_text(
    vector(288, 288, 288, 288, group(isotype(OFFW, NAVY), s2, tx2, ty2), COMMENT + " Ícono de la pantalla de inicio del sistema."),
    encoding="utf-8",
)

(RES / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
adaptive = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
    '    <background android:drawable="@color/visor_black_blue" />\n'
    '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
    '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
    "</adaptive-icon>\n"
)
for n in ("ic_launcher", "ic_launcher_round"):
    (RES / f"mipmap-anydpi-v26/{n}.xml").write_text(adaptive, encoding="utf-8")
print("ok")
