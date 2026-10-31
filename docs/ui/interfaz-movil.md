# Interfaz móvil (Jetpack Compose)

**Fuentes:** `Diseño de Interfaz móvil.pdf` (mockups del equipo de diseño), la guía de interfaces
(sección 12), el **Manual de identidad corporativa V.I.S.O.R.** ([ADR-008](../decisions/ADR-008-identidad-visor.md)) y `Historias_de_Usuario_CDA_Corregido.xlsx`.

La interfaz vive en `app/src/main/kotlin/com/robotcentinela/app/ui/` y solo habla con los casos de
uso de `:core` a través de `AppContainer`. No calcula alertas, no convierte ADC a ppm y no inventa
posiciones ni datos: lo que el backend todavía no expone se muestra como **pendiente de backend**.

## Cómo correrla

1. Copia `local.properties.example` como `local.properties` y completa `supabase.url`,
   `supabase.publishableKey` (clave **pública**; nunca `service_role` ni `sb_secret_`) y la cuenta de
   prueba `centinela.devEmail` y `centinela.devPassword` (ver `docs/backend/README.md`).
2. Abre el proyecto en Android Studio (AGP 9.4, SDK de Android 37) y ejecuta `app`.
3. La app abre en el inicio de sesión (correo y contraseña, recuperación de acceso). En debug, un botón
   autocompleta la cuenta de prueba. RLS exige una sesión con membresía activa para entregar datos.

## Pantallas y conexión con el backend

| Pantalla | Qué muestra | Origen de los datos |
|---|---|---|
| Iniciar sesión | Entrada con correo y contraseña, recuperación de acceso y soporte de cuenta de prueba | Supabase Auth (`SignIn`, `RecoverAccess`) |
| Elegir domicilio | Domicilios visibles y su zona horaria | Supabase: `memberships`, `homes`, `robots` |
| Resumen | Gas LP, metano, temperatura y humedad (valor, unidad, "Última lectura", calidad); Operación (batería y estaciones); mapa asistido 2D (**No disponible / Implementación futura**); Eventos y Avisos | Telemetría en vivo (`telemetry_frames` y sus tablas hijas, deltas por `stream_version` + Realtime), unidades de `channels` y alertas |
| Mapa | Leyenda "No disponible / Implementación futura": el mapa asistido 2D (plano, pose, ruta y habitaciones) no se incluye en esta versión (lo implementará el equipo de IoT, [ADR-009](../decisions/ADR-009-mapa-2d-fuera-de-esta-version.md)) | — |
| Misiones | Detalles de misión, tarjeta de mapa ("No disponible / Implementación futura"), tarjetas de misiones, "Nueva misión" | `missions` (inicio, fin, estado). Nombre y estaciones: **pendiente** (B16). "Guardar" no envía nada (ver R9) |
| Historial | Alertas registradas por severidad; "Lectura general" del canal elegido con máxima, mínima y promedio | Alertas y frames históricos de Supabase. Misiones, horas de operación y calibraciones: **pendiente** |
| Eventos (campana) | Alertas con filtros de periodo, severidad y estado; detalle de cada evento | Supabase: alertas |
| Ajustes (engrane) | Tema claro, oscuro o del sistema; cambio de domicilio | Local |

## Diferencias con los mockups (y por qué)

- **Sin "Hola, nombre"**: depende del perfil extendido en la BD; el cierre de sesión ya está integrado en Ajustes.
- **Logo e ícono:** el isotipo V.I.S.O.R. se vectorizó desde el manual; el ícono del launcher es adaptativo (con capa monocromática) y la bienvenida replica la pág. 27 del manual.
- **Gráficas de un canal a la vez**: el mockup apila ADC, °C y % en un mismo eje, lo que mezcla
  unidades (§12.5, RNF12). El selector de canal del mockup elige qué se grafica.
- **Estados como insignias**: el verde "Dato válido" sobre blanco no alcanza el contraste 4.5:1 que
  exige la guía, así que los estados se dibujan como insignias con texto oscuro.
- **Lecturas por habitación**: dependen del mapa 2D, así que muestran "No disponible / Implementación futura".

## Tokens, tipografía y accesibilidad

- **Tokens:** los de la tabla 12.2, sobre la paleta oficial V.I.S.O.R. (`VisorPalette`: Black Blue `#041839`, Off White `#EEEEEE`, Navy `#2D4059`, Bright Orange `#FF5722`, validado `#B5D0A2`, alerta `#F7B32D`, crítico `#C00000`), están en `ui/theme/CentinelaColors.kt`, con tokens derivados para
  cumplir el contraste que exige la propia guía.
- **Poppins con dígitos tabulares:** Poppins no trae `tnum`; se incluye una variante con dígitos de
  ancho fijo (OFL 1.1, `app/OFL-Poppins.txt`) para que lecturas y horas no "bailen".
- **Íconos:** los de la marca (pág. 22 del manual, vectorizados) donde existen; para el resto (campana, ajustes, flechas) Material Symbols Rounded (Apache 2.0).
- **Titulares en Questrial** (OFL 1.1, `app/OFL-Questrial.txt`) y cuerpo en Poppins, como pide el manual (§4.2).
- **Lectores de pantalla:** cada tarjeta es un solo elemento con descripción completa; gráfica y
  dona de alertas tienen texto alternativo.

## Entrada a la app en la demostración beta

El build `demo` no tiene pantalla de inicio de sesión ni cuenta de prueba: la sesión es fija
(`PublicReadAuthAdapter`) y el selector de domicilio muestra lo que el rol `anon` puede leer. Con los datos
actuales (2 frames, el último del 23 de octubre de 2026) se ven los estados "Atrasado", "Sin alertas" y
"pendiente de backend" en misiones y "No disponible / Implementación futura" en el mapa. Detalle en
[ADR-007](../decisions/ADR-007-demo-lectura-publica.md).
