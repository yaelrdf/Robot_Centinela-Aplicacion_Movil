# ADR-008: Identidad visual V.I.S.O.R. (Manual de identidad corporativa)

- **Estado:** aceptada
- **Fecha:** 2026-10-31

## Contexto

La app usaba la guía de interfaces "Lunar Base" (§12) y no tenía logo ni ícono propio. El Manual de
identidad corporativa define la marca **V.I.S.O.R.** ("Protección con precisión robótica"): isotipo,
paleta, tipografías, iconografía y pantalla de bienvenida. Los activos solo existen como imágenes dentro
del PDF del manual.

## Decisión

1. **Nombre visible:** "V.I.S.O.R." (`app_name`). El `applicationId` `com.robotcentinela.app` no cambia.
2. **Isotipo, ícono del launcher y splash:** el isotipo se vectorizó desde la imagen del manual (págs. 9 y 10)
   en tres capas (círculo, tinta y núcleos naranja) y se generó como `VectorDrawable` en dos variantes
   (fondo claro y fondo azul). El ícono es adaptativo (fondo Black Blue, con capa monocromática) y la pantalla
   de inicio usa `androidx.core:core-splashscreen`; la pantalla de carga de Compose replica la pág. 27.
3. **Paleta:** tokens oficiales en `VisorPalette`: Black Blue `#041839` (fondo oscuro, splash, texto en
   claro), Off White `#EEEEEE`, Navy `#2D4059`, Bright Orange `#FF5722` (acento y avisos), validado
   `#B5D0A2`, alerta `#F7B32D` y crítico `#C00000`. Se conservan los tokens derivados de contraste de la guía
   (4.5:1 en texto, 3:1 en gráficos): el amarillo solo se usa como relleno con texto oscuro y el rojo crítico
   se aclara en tema oscuro cuando va como texto o ícono (`criticalText`).
4. **Tipografía:** Questrial (OFL 1.1, `app/OFL-Questrial.txt`) en titulares; Poppins sigue en cuerpo e interfaces.
5. **Iconografía:** los 24 íconos de la pág. 22 se vectorizaron; 20 sustituyen, por nombre, a los íconos
   equivalentes del proyecto. Los demás (campana, ajustes, flechas, etc.) siguen siendo Material Symbols,
   porque el manual no los incluye.

## Consecuencias

- El vectorizado parte de imágenes de 200 px (íconos) y 1000 px (isotipo): puede haber bordes ligeramente
  irregulares. Lo ideal es sustituirlos por los SVG originales cuando el equipo de diseño los entregue.
- El manual pide mostrar el lema entre comillas y en mayúsculas y minúsculas; la pantalla de bienvenida
  conserva "BIENVENIDO" en mayúsculas como en el mockup.
