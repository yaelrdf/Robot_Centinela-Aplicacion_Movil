# Interfaz móvil (Jetpack Compose)

**Fuentes:** `Diseño de Interfaz móvil.pdf` (mockups del equipo de diseño), la guía de interfaces
(sección 12, identidad *Lunar Base*) y `Historias_de_Usuario_CDA_Corregido.xlsx`.

La interfaz vive en `app/src/main/kotlin/com/robotcentinela/app/ui/` y solo habla con los casos de
uso de `:core` a través de `AppContainer`. No calcula alertas, no convierte ADC a ppm y no inventa
posiciones ni datos: lo que el backend todavía no expone se muestra como **pendiente de backend**.

## Cómo correrla

1. Copia `local.properties.example` como `local.properties` y completa `supabase.url`,
   `supabase.publishableKey` (clave **pública**; nunca `service_role` ni `sb_secret_`) y la cuenta de
   prueba `centinela.devEmail` y `centinela.devPassword` (ver `docs/backend/README.md`).
2. Abre el proyecto en Android Studio (AGP 9.4, SDK de Android 37) y ejecuta `app`.
3. La app entra sola con la cuenta de prueba (solo debug) y muestra los domicilios. La pantalla de
   inicio de sesión se integra al final del proyecto (indicación del equipo); RLS exige una sesión
   para entregar datos, por eso existe la cuenta de prueba.

## Pantallas y conexión con el backend

| Pantalla | Qué muestra | Origen de los datos |
|---|---|---|
| Iniciar sesión | Entrada con correo y contraseña, recuperación de acceso y soporte de cuenta de prueba | Supabase Auth (`SignIn`, `RecoverAccess`) |
| Elegir domicilio | Domicilios visibles y su zona horaria | Supabase: `memberships`, `homes`, `robots` |
| Resumen | Gas LP, metano, temperatura y humedad (valor, unidad, "Última lectura", calidad); Operación (batería y estaciones); mapa; Eventos y Avisos | Telemetría en vivo (`telemetry_frames` y sus tablas hijas, deltas por `stream_version` + Realtime), unidades de `channels` y alertas |
| Mapa | Misión en curso, estaciones, sensores, batería, mapa con pose e incertidumbre, habitaciones | Pose y batería: telemetría. Misión: `missions`. Plano (archivo del mapa) y habitaciones: **pendiente de backend** |
| Misiones | Detalles de misión, mapa de la misión, tarjetas de misiones, "Nueva misión" | `missions` (inicio, fin, estado) y trayectoria de la telemetría. Nombre y estaciones: **pendiente** (B16). "Guardar" no envía nada (ver R9) |
| Historial | Alertas registradas por severidad; "Lectura general" del canal elegido con máxima, mínima y promedio | Alertas y frames históricos de Supabase. Misiones, horas de operación y calibraciones: **pendiente** |
| Eventos (campana) | Alertas con filtros de periodo, severidad y estado; detalle de cada evento | Supabase: alertas |
| Ajustes (engrane) | Tema claro, oscuro o del sistema; cambio de domicilio | Local |

## Diferencias con los mockups (y por qué)

- **Sin "Hola, nombre"**: depende del perfil extendido en la BD; el cierre de sesión ya está integrado en Ajustes.
- **Sin logo**: el equipo todavía no lo define; la app usa el ícono de robot centinela por defecto del sistema.
- **Gráficas de un canal a la vez**: el mockup apila ADC, °C y % en un mismo eje, lo que mezcla
  unidades (§12.5, RNF12). El selector de canal del mockup elige qué se grafica.
- **Estados como insignias**: el verde "Dato válido" sobre blanco no alcanza el contraste 4.5:1 que
  exige la guía, así que los estados se dibujan como insignias con texto oscuro.
- **Lecturas por habitación**: las debe calcular el backend con el plano validado (RB11).

## Tokens, tipografía y accesibilidad

- **Tokens:** los de la tabla 12.2 están en `ui/theme/CentinelaColors.kt`, con tokens derivados para
  cumplir el contraste que exige la propia guía.
- **Poppins con dígitos tabulares:** Poppins no trae `tnum`; se incluye una variante con dígitos de
  ancho fijo (OFL 1.1, `app/OFL-Poppins.txt`) para que lecturas y horas no "bailen".
- **Íconos:** una sola familia (Material Symbols Rounded, Apache 2.0) como vectores.
- **Lectores de pantalla:** cada tarjeta es un solo elemento con descripción completa; mapa, gráfica y
  dona de alertas tienen texto alternativo.
