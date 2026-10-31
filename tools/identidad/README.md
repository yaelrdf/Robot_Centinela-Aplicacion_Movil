# Herramientas de identidad visual V.I.S.O.R.

Scripts con los que se vectorizaron el isotipo y los íconos del *Manual de identidad corporativa* (ADR-008).
Solo hacen falta si hay que regenerar los recursos; el resultado ya está en `app/src/main/res/`.

Requisitos (en un entorno virtual): `pip install potracer numpy scipy pillow cairosvg`, y `pdfimages` (poppler).

```bash
# 1. Extraer las imágenes del PDF (isotipo: pág. 9; íconos: pág. 22)
pdfimages -png -f 9  -l 9  "Manual de identidad corporativa.pdf" p9/i
pdfimages -png -f 22 -l 22 "Manual de identidad corporativa.pdf" p22/i

# 2. Isotipo en capas (círculo, tinta clara y núcleos naranja) -> iso.json
python vec_logo.py p9/i-000.png iso.json

# 3. VectorDrawable del isotipo, ícono adaptativo y splash (lee iso.json junto al script)
python gen_logo_res.py ../../app/src/main/res

# 4. Íconos de trazo (20 sustituyen por nombre a los íconos del proyecto)
python gen_icons.py p22 ../../app/src/main/res
```

Las imágenes del PDF son de baja resolución (íconos de 200 px), así que el trazado puede dejar bordes algo
irregulares. Si el equipo de diseño entrega los SVG originales, conviene usarlos en lugar de estos scripts.
