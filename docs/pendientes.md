# Pendientes y preguntas abiertas

Este borrador implementa solo lo que la documentación deja explícito. Estos puntos dependen de otros
equipos. Cuando se resuelvan, se actualizan los archivos indicados.

## Para el equipo de backend / base de datos

| # | Pregunta | Supuesto actual | Archivos afectados |
|---|---|---|---|
| B1 | ¿Cuál es el recurso (vista, tabla o RPC) que expone los frames con la estructura de `JSON_estructura.txt`? | `telemetry_frames_v1`, con los campos del JSON más `robot_id`, `received_at` y `stream_version` al nivel superior | `SupabaseResources.kt`, `TelemetryFrameDto.kt` |
| B2 | ¿`stream_version` es contiguo por robot (+1 por frame aceptado), como propone §7.4? | Sí. Se usa para detectar huecos por retención | `TelemetrySynchronizer.kt` |
| B3 | ¿Habrá una RPC de snapshot consistente con watermark (§7.3, §13.6)? | El snapshot es el frame más reciente | `SupabaseTelemetryQueryAdapter.kt` |
| B4 | ¿Qué tabla recibe Realtime para avisar de frames nuevos y con qué filtro? | `telemetry_frames`, filtrada por `robot_id` | `SupabaseResources.kt`, `SupabaseTelemetryChangeNotifier.kt` |
| B5 | Nombres y columnas definitivos de `memberships`, `homes`, `robots` y `alerts` | Los de §13.2 | `SupabaseHomeScopeAdapter.kt`, `SupabaseAlertQueryAdapter.kt` |
| B6 | Catálogo de `alerts.type` y `alerts.severity` | Se conservan como texto | `Alert.kt` |
| B7 | ¿Cómo se consultan las ocurrencias y la evidencia de una alerta (`alert_occurrences`, RF16)? | No implementado | `incidents/` |
| B8 | Contrato del catálogo de capacidades (`GET /v1/robots/{id}/capabilities`, RF05): unidad, gas objetivo, periodo y estado de calibración | Solo existe el puerto `CapabilitiesPort` | `environmentalmonitoring/ports` |
| B9 | Contrato de agregados (`metric_aggregates`, HU07 CDA 3) | Solo existe el puerto `MetricAggregatePort` | `environmentalmonitoring/ports` |
| B10 | Filtros de historial por canal y calidad (RF10) | Solo robot, intervalo y misión | `TelemetryQueryPort` |
| B11 | Política de retención efectiva (§7.5: 7 días de crudos) | Afecta a `historyLimited` | — |

## Para Ingeniería de Software / Requerimientos

| # | Pregunta | Supuesto actual |
|---|---|---|
| R1 | Método de autenticación de Supabase Auth | Correo y contraseña, porque RF01 pide "recuperar acceso" |
| R2 | Valores de `memberships.role` | Se conserva el texto; la app no autoriza por rol |
| R3 | Dispositivos objetivo (versión mínima de Android) | `minSdk` 26 |
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
| I3 | Unidad de `temperature` y de los canales `*_raw` | Debe venir del catálogo de capacidades (B8) |

## Contratos de dominio pendientes

- **Mapas** (HU05, HU19, RF11, RF32): formato de `map_versions`, habitaciones, estaciones, rutas y
  Storage privado con URL firmada. **Propuesta provisional:** `FloorPlan` y `FloorPlanPort` (ADR-005).
- **Misiones** (HU06, HU08, RF14, RF33): resumen, estaciones previstas, completadas y omitidas con
  causa, y consumo. **Propuesta provisional:** `MissionSummary` y `MissionQueryPort` (ADR-005).
- **Calibraciones** (HU09, RF20): fecha, método, responsable y vigencia.
