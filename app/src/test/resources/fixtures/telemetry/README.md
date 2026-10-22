# Fixtures de telemetría (datos sintéticos)

Estos archivos son datos **sintéticos de prueba** (HU15, RF35, RB29). No representan
ninguna vivienda, persona ni dispositivo real, y no deben usarse fuera de las pruebas.

| Archivo | Contenido | Uso |
|---|---|---|
| `frame_v1_1_device_payload.json` | Copia exacta de `JSON_estructura.txt` (`schema_version` 1.1), tal como lo publica el dispositivo, sin `received_at` ni `stream_version`. | Verifica que el DTO replica el contrato. |
| `frame_qa_sensor_states.json` | Valores `null`, estados `warming`, `fault`, `out_of_range` y `not_installed`, una calidad fuera de catálogo, un booleano `null`, batería sin datos y reloj `unsynced`. | Semántica de calidad (HU02, HU20, RB04). |
| `frame_qa_invalid_both_values.json` | Una lectura con `value` y `boolean_value` a la vez. | Rechazo del frame (§13.2). |
| `frame_qa_schema_v2.json` | `schema_version` 2.0. | Rechazo de una versión mayor no soportada (§14.2). |
| `frame_supabase_embebido.json` | El frame de `JSON_estructura.txt` como lo devuelve PostgREST desde el esquema normalizado del equipo (`telemetry_frames` con sus tablas hijas embebidas). Generado con `docs/backend/datos-de-prueba.sql` sobre el esquema real. | Verifica `NormalizedFrameAssembler`. |

Los casos de duplicados y desconexiones del flujo (HU15 CDA 1) se prueban en `:core`, en
`TelemetrySynchronizerTest`.
