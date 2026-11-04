# ADR-011: Resolver alertas desde la app (función `resolve_alert`) y clave de escritura temporal en el demo

- **Estado:** Aceptada
- **Fecha:** 2026-11-03

## Contexto

Hasta la 0.6.0-beta la app solo consultaba alertas (RB25): una alerta abierta se cerraba únicamente cuando la ingesta
del servidor lo decidía por histéresis. Con las notificaciones críticas (ADR-010) hace falta poder **cerrar** una alerta
desde el panel de eventos, desde su detalle y desde la propia notificación.

Estado real del proyecto Supabase al decidir:

- `alerts` solo tiene políticas `SELECT`; un `UPDATE` directo de la app lo rechaza RLS.
- `ack_alert(p_alert_id)` existe (técnico del domicilio; `open → acknowledged`), pero no hay función para `resolved`.
- La ingesta ya cierra alertas con `state = 'resolved', resolved_at = now()` y abre un incidente nuevo si la condición
  persiste (el índice único solo cubre `open` y `acknowledged`).

## Decisión

1. **Una función SQL nueva, `public.resolve_alert(p_alert_id uuid) returns boolean`** (`docs/backend/migracion-resolver-alerta.sql`),
   `SECURITY DEFINER`, que solo ejecuta un técnico activo del domicilio del robot (`is_home_technician`). Deja
   `state = 'resolved'` y `resolved_at = now()`. Sin permiso lanza SQLSTATE `42501` (HTTP 403 en PostgREST), que la app
   traduce a `AccessDeniedException`. `anon` no puede ejecutarla.
2. **Una política `UPDATE` sobre `alerts` se descarta**: abriría todas las columnas a escritura. La función limita la
   escritura a dos columnas y a una transición concreta.
3. **Puerto y caso de uso en `:core`**: `AlertCommandPort.resolve(alertId)` y `ResolveAlert` (valida el ámbito del domicilio
   igual que `QueryAlertHistory`). Adaptador: `SupabaseAlertCommandAdapter` (RPC).
4. **Un solo camino en la interfaz**: todos los botones («Resolver» en el panel «Eventos y Avisos» y en Eventos, en el aviso
   crítico, en el detalle y la acción «Resolver alerta» de la notificación) piden confirmación y terminan en
   `ResolveAlert`. Al resolver se emite `AlertResolvedBus`; el aviso crítico, el sonido, el flash y la notificación se retiran y
   las listas se recargan.
5. **La notificación resuelve sin abrir la app** (`CriticalAlertActionReceiver`, `goAsync()`). Si el proceso se reinició y no hay
   sesión, o no hay red o rol técnico, la notificación se conserva y se muestra un mensaje: Android no permite abrir una
   actividad desde un receptor en segundo plano.
6. **Excepción temporal para el build `demo`**: el demo no tiene sesión (`anon`) y no puede llamar a `resolve_alert`. Para la
   demostración, `local.properties` (no versionado) puede definir `demo.write.key`; Gradle la inyecta **solo** en el build
   `demo` como `BuildConfig.DEMO_WRITE_KEY` (vacía en `debug` y `release`). Un cliente de Supabase aparte, usado únicamente por
   `SupabaseDemoAlertCommandAdapter`, actualiza `alerts` con esa clave. Las lecturas del demo siguen con la clave pública.
   Sin esa propiedad, el demo no muestra el botón.

## Consecuencias

- RB25 cambia: la app sigue sin escribir telemetría ni configuración; **sí** puede cerrar alertas (y solo eso).
- RB21: un cierre manual no demuestra que el gas haya bajado. Si la condición persiste, el siguiente frame abre un incidente
  nuevo y vuelve a avisar. El diálogo de confirmación lo dice.
- Solo el rol `technician` puede resolver; con rol `reader` la app muestra el botón (no usa el rol para autorizar, RNF09) y el
  servidor responde que no tiene permiso.
- **Riesgo aceptado y temporal de la clave de escritura del demo.** Esa clave se salta RLS y cualquiera que tenga el APK `demo`
  que la lleva puede extraerla. Por eso: no se escribe en archivos versionados, no existe en `release` ni `debug`, el APK `demo`
  con clave **no se adjunta a la release pública `v0.7.1-beta`** (se entrega por un canal privado; el equipo quiso adjuntarlo, pero la subida quedó bloqueada
  y se optó por no exponerlo; el demo público de la 0.7.0-beta no tiene botón) y **debe revocarse en Supabase al terminar la demostración**. La validación
  `SupabaseConfig` sigue rechazando claves privilegiadas como clave de lectura.
- Reversión del backend: `drop function public.resolve_alert(uuid);`.
