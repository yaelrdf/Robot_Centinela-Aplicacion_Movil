# ADR-006: Demostración beta con lectura pública (sin inicio de sesión)

- **Estado:** aceptada, **temporal**
- **Fecha:** 2026-10-29

## Contexto

- Se necesita un APK instalable para mostrar la app con datos reales de Supabase.
- La pantalla de inicio de sesión va al final del proyecto (ADR-005) y el usuario de pruebas con
  membresía activa todavía no existe en el proyecto de Supabase (B21).
- El 29 de octubre de 2026 se revisó el proyecto con la clave pública y sin sesión (solo lecturas):
  el rol `anon` lee `homes`, `robots`, `devices`, `channels`, `map_versions`, `telemetry_frames` y sus
  tablas hijas. `memberships`, `device_sessions`, `missions` y `alerts` llegan vacías (RLS o sin datos).
  Es el hallazgo B19 de [pendientes](../pendientes.md).
- Sin `device_sessions` la app no obtiene `boot_id` ni `device_id` y rechazaría todos los frames (B20).

## Decisión

1. Un build type nuevo, **`demo`**, activa `BuildConfig.DEMO_PUBLIC_READ`. Los builds `debug` y
   `release` no cambian.
2. Con la bandera activa, `Adapters.supabase(..., publicRead = true)` sustituye solo dos adaptadores:
   - `PublicReadAuthAdapter` reporta una sesión fija, sin permisos propios.
   - `PublicReadHomeScopeAdapter` lista los domicilios que RLS deja ver al rol `anon`, con una
     membresía sintética `lectura_publica`.
3. `SupabaseTelemetryQueryAdapter` pide además `schema_version` y `raw_payload`, y
   `NormalizedFrameAssembler` toma de ahí `boot_id`, `device_id` y `firmware_version` **solo cuando**
   `device_sessions` no es visible. Con sesión visible, el comportamiento es el de siempre.
4. `:core` y `RootViewModel` no cambian. El APK no lleva contraseñas: solo la URL y la clave
   publishable, públicas por diseño. Se firma con la clave de depuración de Android.

## Consecuencias

- La demo muestra los datos que el proyecto expone hoy: 2 frames (del 23 de octubre de 2026), así que
  los canales aparecen como "atrasados"; sin alertas ni misiones; mapa "pendiente de backend".
- **Caduca.** Si backend aplica `propuesta-politicas-lectura.sql` o cierra la lectura anónima (B19),
  el APK deja de mostrar datos. Es el comportamiento correcto y no debe revertirse para sostener la demo.
- **No resuelve B19.** Distribuir el APK equivale a publicar la telemetría del proyecto, que ya es
  legible con la clave que va dentro de cualquier APK. Debe cerrarse antes de un uso real.
- **Retiro:** cuando exista el usuario de pruebas con membresía (B21) o el inicio de sesión, se
  elimina el build `demo`, los dos adaptadores `PublicRead*` y la rama `publicRead` del ensamblador.
