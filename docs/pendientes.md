# Pendientes y preguntas abiertas

Este borrador implementa solo lo que la documentación deja explícito. Estos puntos dependen de otros
equipos. Cuando se resuelvan, se actualizan los archivos indicados.

## Para el equipo de backend / base de datos

| # | Pregunta | Supuesto actual | Archivos afectados |
|---|---|---|---|
| B1 | ~~¿Qué recurso expone los frames?~~ **Resuelto** con el esquema del equipo (`docs/backend/esquema-supabase-equipo.sql`): `telemetry_frames` con sus tablas hijas, que la app reconstruye en el frame v1 | — | `NormalizedFrameAssembler.kt` |
| B2 | ¿`stream_version` es contiguo por robot (+1 por frame aceptado), como propone §7.4? | Sí. Se usa para detectar huecos por retención | `TelemetrySynchronizer.kt` |
| B3 | ¿Habrá una RPC de snapshot consistente con watermark (§7.3, §13.6)? | El snapshot es el frame más reciente | `SupabaseTelemetryQueryAdapter.kt` |
| B4 | Agregar `telemetry_frames` a la publicación `supabase_realtime` | Incluido en `docs/backend/propuesta-politicas-lectura.sql` | `SupabaseTelemetryChangeNotifier.kt` |
| B5 | ~~Nombres y columnas de `memberships`, `homes`, `robots` y `alerts`~~ **Resuelto** con el esquema del equipo. `alerts` no tiene `rule_id` | — | `SupabaseHomeScopeAdapter.kt`, `SupabaseAlertQueryAdapter.kt` |
| B6 | Catálogo de `alerts.type` y `alerts.severity` | Se conservan como texto | `Alert.kt` |
| B7 | ¿Cómo se consultan las ocurrencias y la evidencia de una alerta (`alert_occurrences`, RF16)? | No implementado | `incidents/` |
| B8 | Catálogo de capacidades (RF05) | Se lee de `channels` (`channel_key`, `metric_type`, `unit_code`). Falta gas objetivo, periodo esperado y estado de calibración | `SupabaseCapabilitiesAdapter.kt` |
| B9 | Contrato de agregados (`metric_aggregates`, HU07 CDA 3) | Solo existe el puerto `MetricAggregatePort` | `environmentalmonitoring/ports` |
| B10 | Filtros de historial por canal y calidad (RF10) | Solo robot, intervalo y misión | `TelemetryQueryPort` |
| B11 | Política de retención efectiva (§7.5: 7 días de crudos) | Afecta a `historyLimited` | — |
| B12 | El esquema habilita RLS pero no crea políticas: sin ellas la app no recibe ninguna fila | Propuesta probada en `docs/backend/propuesta-politicas-lectura.sql` (lectura por membresía activa) | — |
| B13 | `telemetry_frames` no guarda `schema_version` | Se asume 1.1 (la ingesta solo guarda frames válidos). Si se agrega la columna, la app la usa sin cambios | `NormalizedFrameAssembler.kt` |
| B14 | ¿El `device_id` del JSON es `devices.id` o `devices.public_id`? | `devices.id` (UUID) | `NormalizedFrameAssembler.kt` |
| B15 | `battery.quality` del JSON (`voltage_only`): ¿va en `method_code` o en `quality` de `battery_readings`? | Se muestra `method_code` como método | `NormalizedFrameAssembler.kt` |
| B16 | `missions` no tiene nombre, propósito, estaciones ni energía (mockups y HU06, HU08) | Se muestran como "pendiente", nunca como cero | `SupabaseMissionQueryAdapter.kt` |
| B17 | `firmware_version` está en `devices`, no por frame | Se muestra el firmware actual del dispositivo | `NormalizedFrameAssembler.kt` |
| B18 | La vista `telemetry_frames_v1` del equipo se salta el RLS (falta `security_invoker = true`) y no trae `schema_version`, `device_id` ni `boot_id` | Corrección probada en `docs/backend/propuesta-vista-telemetry-frames-v1.sql`. La app no usa la vista: lee las tablas | `SupabaseTelemetryQueryAdapter.kt` |
| B19 | En el proyecto de Supabase (revisado el 23 de octubre de 2026), **sin iniciar sesión** y solo con la clave pública se leen `homes`, `robots`, `devices`, `channels`, `map_versions`, `telemetry_frames`, `sensor_readings`, `frame_poses`, `robot_states` y `battery_readings`. Cualquiera con la app (la clave pública va dentro del APK) puede leer la telemetría | Aplicar `docs/backend/propuesta-politicas-lectura.sql` y quitar las políticas o permisos que dejan leer al rol `anon` | — |
| B20 | `device_sessions` no se puede leer, así que la app no obtiene `boot_id` y rechaza los frames | Las políticas propuestas incluyen lectura de `device_sessions` por membresía | `NormalizedFrameAssembler.kt` |
| B21 | Falta un usuario de prueba con membresía activa en "Casa Demo Centinela" (`memberships` está vacía para la app) | Sin él la app se queda en "Sin sesión" | `DevAccount.kt` |

## Para Ingeniería de Software / Requerimientos

| # | Pregunta | Supuesto actual |
|---|---|---|
| R1 | Método de autenticación de Supabase Auth | **Resuelto:** Autenticación por correo y contraseña implementada con `LoginScreen`, `SignIn`, `SignOut` y `RecoverAccess`. Las compilaciones debug conservan botón para autocompletar la cuenta de prueba de `local.properties` (`DevAccount.kt`). | `LoginScreen.kt`, `LoginViewModel.kt`, `RootViewModel.kt` |
| R2 | Valores de `memberships.role` | Se conserva el texto; la app no autoriza por rol |
| R3 | Dispositivos objetivo (versión mínima de Android) | `minSdk` 29 (Android 10), definido por el equipo |
| R4 | Validar la interfaz (docs/ui/interfaz-movil.md) contra los mockups móviles | Implementada según `Diseño de Interfaz móvil.pdf` |
| R5 | ¿Se prioriza CSV (HU13), push (HU16), video (HU17) o comandos (HU18)? | No implementados |

| R6 | La guía exige 4.5:1 de contraste, pero `operational` y `warning` no lo alcanzan como texto sobre blanco en tema claro | Estados como insignias rellenas con texto oscuro; tokens derivados en `CentinelaColors.kt` |
| R7 | Poppins no tiene números tabulares (`tnum`) | Variante con dígitos de ancho fijo incluida en `res/font` |
| R8 | Catálogo de severidades para el filtro de eventos | `critical`, `warning`, `info` (ligado a B6) |
| R9 | El mockup incluye "Generar nueva misión", pero el expediente define la app como de consulta (§1, §7.1). ¿Crear misiones desde la app es un registro de planeación o un comando al robot? ¿Con qué contrato? | La hoja existe, pero "Guardar" no envía nada |
| R10 | Las gráficas del mockup apilan Gas LP (ADC), metano (ADC), temperatura (°C) y humedad (%) en un mismo eje | Se grafica un canal a la vez, con su unidad (§12.5, RNF12) |
| R11 | Lecturas "por habitación" del mockup | Requieren que el backend asigne lecturas con el plano validado (RB11); la app no lo hace |
| R12 | Logo de la app | Sin ícono propio hasta que el equipo lo defina |

## Para IoT / firmware

| # | Pregunta | Supuesto actual |
|---|---|---|
| I1 | Catálogo canónico de `robot_state` y su correspondencia con los estados legacy de E01 (RF19) | Texto original (`moving`, `sampling`) |
| I2 | Catálogo de `pose.quality` y de `battery.quality` | Texto original (`estimated`, `voltage_only`) |
| I3 | Unidad de `temperature` y de los canales `*_raw` | Viene de `channels.unit_code` (B8) |

## Contratos de dominio pendientes

- **Mapas** (HU05, HU19, RF11, RF32): formato de `map_versions`, habitaciones, estaciones, rutas y
  Storage privado con URL firmada. **Propuesta provisional:** `FloorPlan` y `FloorPlanPort` (ADR-005).
- **Misiones** (HU06, HU08, RF14, RF33): la lista y el estado ya se leen de `missions`. Faltan
  estaciones previstas, completadas y omitidas con causa, y consumo (B16).
- **Calibraciones** (HU09, RF20): fecha, método, responsable y vigencia.
