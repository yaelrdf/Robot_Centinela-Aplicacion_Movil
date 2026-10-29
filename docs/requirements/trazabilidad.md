# Trazabilidad: historias, requerimientos, código y pruebas

**Fuentes:** `Historias_de_Usuario_CDA_Corregido.xlsx` (hoja "HU Actualizadas") y expediente técnico
§8 (RF), §9 (RNF), §10 (trazabilidad) y §11 (RB).

**Estados:**
- ✅ **Implementado**: la lógica de la app está hecha y probada.
- 🟡 **Parcial**: hay una parte implementada y el resto depende de un contrato pendiente.
- ⏳ **Pendiente**: depende de otro equipo o de un contrato no publicado.
- ➖ **Fuera de la app**: lo implementa firmware, backend o la base de datos.

## Historias de usuario

| HU | Nombre | Prioridad | RF / RB | Código (paquete o clase) | Pruebas | Estado |
|---|---|---|---|---|---|---|
| HU01 | Acceso y consulta | Máxima | RF01, RF02, RF32 | `identity` (`SignIn`, `SignOut`, `SelectHomeScope`, `CurrentHomeScope`); `SupabaseAuthAdapter`, `SupabaseHomeScopeAdapter` | `HomeScopeUseCasesTest` | ✅ La autorización real es RLS del servidor (RNF09) |
| HU02 | Señales de gas y calidad | Máxima | RF05, RF06, RF20; RB04 a RB06 | `ReadingInterpreter`, `ReadingQuality`, `DescribeChannelStatus` | `ReadingInterpretationTest`, `DescribeChannelStatusTest`, `TelemetryFrameMapperTest` | 🟡 Semántica completa. La unidad depende del catálogo de capacidades (pendiente) |
| HU03 | Consistencia web y móvil | Máxima | RF07, RF08, RF09; RB26 a RB28 | `TelemetrySynchronizer`, `SupabaseTelemetryQueryAdapter`, `SupabaseTelemetryChangeNotifier` | `TelemetrySynchronizerTest` | ✅ Los SLO de RNF02 se miden con la UI |
| HU04 | Edad del dato | Máxima | RF09, RF23; RB19 | `FreshnessPolicy`, `LatestTelemetryProjection`, `DescribeChannelStatus` | `FreshnessPolicyTest`, `LatestTelemetryProjectionTest` | ✅ |
| HU05 | Mapa de la misión | Máxima | RF11, RF12; RB13 | `MapVersionId`, `TelemetryFrame.mapVersionId` | `TelemetryFrameMapperTest` | 🟡 Se conserva la versión de mapa del frame. La geometría y el listado de estaciones están pendientes |
| HU06 | Incertidumbre y estaciones | Máxima | RF12, RF13, RF33; RB11, RB16 | `Pose` (incertidumbre; no asigna habitación) | `TelemetryFrameMapperTest` | 🟡 Faltan las estaciones omitidas y su causa (contrato de misiones pendiente) |
| HU07 | Historial por fechas | Máxima | RF10, RF16 | `UtcInterval`, `QueryTelemetryHistory`, `QueryAlertHistory`, `QueryMetricAggregates` | `UtcIntervalTest`, `QueryAlertHistoryTest` | 🟡 Local a UTC y ámbito implementados. Los agregados (mín., máx., prom.) esperan su adaptador |
| HU08 | Estado de batería | Máxima | RF18, RF14; RB17 | `BatteryInterpretation`, `EvaluateRobotHealth` | `RobotHealthDomainTest` | 🟡 Batería completa. El consumo por misión depende del resumen de misión (pendiente) |
| HU09 | Validación de calibraciones | Máxima | RF20, RF24, RF34; RB07 | `CalibrationId` en cada lectura | `TelemetryFrameMapperTest` | ⏳ Los detalles de calibración (fecha, responsable, vigencia) esperan su contrato |
| HU10 | Confirmación MQTT / IoT | Máxima | RF03, RF04, RF30; RB02, RB03 | — | — | ➖ Firmware y puente de ingesta |
| HU11 | Correlación de reportes | Máxima | RF15, RF29; RB21 a RB24 | `Alert` (solo lectura), `SupabaseAlertQueryAdapter` | `QueryAlertHistoryTest` | ➖ La correlación, la histéresis y los umbrales viven en la base de datos. La app solo lee incidentes |
| HU12 | Preferencias visuales UI | Máxima | RF22, RF23 | `ui/theme` (tokens claro/oscuro, `ThemePreference`), `SettingsSheet` | — | 🟡 Propuesta implementada; falta validación con el equipo de diseño |
| HU13 | Exportación de reportes | Media | RF21 | — | — | ⏳ Prioridad Should; el formato del CSV y su origen no están definidos |
| HU14 | Auditoría técnica | Máxima | RF24, RF25 | — | — | ➖ Herramientas técnicas separadas (RF24); los dashboards de consulta no editan |
| HU15 | Simulación de datos QA | Máxima | RF35; RB29 | Fixtures sintéticos en `app/src/test/resources/fixtures/telemetry/`; `TestFrames` | `TelemetryFrameMapperTest`, `TelemetrySynchronizerTest` | 🟡 Cubre nulls, calidades, duplicados y desconexiones. El emisor MQTT de prueba es de QA y backend |
| HU16 | Notificaciones Push | Media | RF26 | — | — | ⏳ Extensión opcional (Should) |
| HU17 | Video en LAN | Baja | RF27; RB33 | — | — | ⏳ Extensión opcional (Could) |
| HU18 | Comandos remotos | Baja | RF28; RB32 | — | — | ⏳ Extensión opcional (Could), detrás de feature flag |
| HU19 | Reutilización de mapa | Máxima | RF11, RF14, RF33 | — | — | ➖ La validación y el vínculo de mapas los hacen backend e IoT; la consulta espera el contrato de mapas |
| HU20 | Sensores fallidos | Máxima | RF05, RF09, RF23; RB04, RB09 | `ReadingInterpreter` (`Fault`, `Warming`, `OutOfRange`, `NotInstalled`, `NoData`) | `ReadingInterpretationTest` | ✅ Los textos e íconos son de la UI |

## Interfaz (propuesta Compose)

| HU | Pantalla o componente | Qué cubre |
|---|---|---|
| HU01 | `HomeSelectionScreen` | Selección explícita de domicilio. El inicio de sesión se integra al final (indicación del equipo) |
| HU02, HU20 | `MetricCard`, `ChannelDetailSheet`, `ChannelPresentation` | Valor, unidad, calidad visible; ADC sin ppm; raya con "Sin dato", "No instalado", "Calentando", "Falla", "Fuera de rango" |
| HU03 | `MainViewModel` | Sincronizador iniciado y detenido con el ciclo de vida; sin duplicados en la ruta |
| HU04 | `MetricCard` | Edad del dato, "Atrasado" con hora exacta, hora aproximada marcada con "≈" |
| HU05, HU06 | `FloorPlanView`, `MapScreen`, `MissionsScreen` | Trayectoria sobre la versión exacta de mapa; incertidumbre distinta de la huella; estaciones omitidas con causa |
| HU07 | `HistoryScreen`, `EventsScreen` | Rango local a UTC; alertas por severidad; mín, máx y promedio; gráfica con huecos |
| HU08 | `OperationCard`, `MissionsScreen` | Voltaje con "Porcentaje no disponible" y método; consumo con método |
| HU12 | `CentinelaTheme`, `SettingsSheet` | Tema elegido explícitamente, contraste, Poppins con dígitos tabulares |

Pruebas de la interfaz: `PresentationTest` (reglas de presentación) y `ProvisionalContractsTest`.

## Reglas de negocio aplicadas en código

| Regla | Dónde |
|---|---|
| RB01: el domicilio se resuelve en el servidor | Los adaptadores no envían `user_id` ni `home_id` del cliente como autorización; RLS en el servidor |
| RB02: identidad inmutable del frame | `FrameId`; deduplicación en `TelemetrySynchronizer` |
| RB04: null con calidad, nunca cero | `ReadingValue.Absent`, `ReadingInterpretation.NoData`, `TelemetryFrameMapper` |
| RB05: ADC sin calibrar no es ppm | `ReadingInterpreter.CONCENTRATION_UNITS`, `RawSignal` |
| RB09: no declarar aire seguro | No existe ningún estado "seguro" en el dominio |
| RB11: ubicación con posición válida | `Pose` no asigna habitación |
| RB17: porcentaje solo con método | `BatteryInterpretation` |
| RB19: el dashboard no retrocede | `CaptureStamp.isNewerThan`, `LatestTelemetryProjection` |
| RB20: misma semántica en web y móvil | Reglas en `:core`, independientes de la plataforma |
| RB21: estados open, acknowledged y resolved | `AlertState` |
| RB24: origen device_reported | `AlertOrigin` |
| RB25: el lector no modifica | Solo hay puertos de lectura |
| RB26: Realtime es aviso, no historial | `TelemetryChangeNotifier` + `TelemetryQueryPort` |
| RB27: cursor de commit por robot | `StreamVersion` |
| RB28: segundo plano no es "en vivo" | `SyncStatus.Stopped`, `Reconciling` |
| RB29: simulación rotulada | Fixtures en `test/` con README de datos sintéticos |

## Requerimientos no funcionales atendidos

| RNF | Cómo |
|---|---|
| RNF04: disponibilidad visible | `IngestConnectivityPolicy` (10 s), `FreshnessPolicy` (3 periodos) |
| RNF08: autenticación y secretos | `SupabaseConfig` (solo HTTPS y clave pública); `local.properties` ignorado |
| RNF09: aislamiento | `CurrentHomeScope` en el cliente; RLS en el servidor |
| RNF12: precisión semántica | `ReadingInterpreter`, `Pose` (estimada), `BatteryInterpretation` |
| RNF13: robustez temporal | `UtcInterval` (America/Mexico_City por defecto), `ClockQuality`, `monotonic_ms` |
| RNF16: mantenibilidad | Hexagonal por dominio, una prueba por regla |
| RNF18: portabilidad | Supabase aislado en adaptadores de `:app` |
| RNF19: privacidad | Las causas de rechazo y los errores no incluyen payloads |

## Integración con Supabase y demostración beta (29 de octubre de 2026)

| Aspecto | Estado |
|---|---|
| Lectura de telemetría, canales, alertas y misiones con el esquema del equipo | ✅ Adaptadores implementados y probados (`NormalizedFrameAssemblerTest`) |
| Contra el proyecto real con la clave pública | 🟡 Verificada la lectura de `homes`, `robots`, `channels` y `telemetry_frames`. Alertas y misiones llegan vacías |
| Contra el proyecto real con un usuario con membresía (HU01, RF02) | ⏳ No verificada: no existe el usuario de pruebas (B21) |
| Demostración sin inicio de sesión (build `demo`) | ✅ Implementada, **temporal** y atada a B19 ([ADR-006](../decisions/ADR-006-demo-lectura-publica.md)). Pruebas: `PublicReadAuthAdapterTest`, `NormalizedFrameAssemblerTest` |
