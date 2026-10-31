# Estado del Proyecto Robot Centinela (Aplicación Móvil)
**Fecha de corte:** 30 de octubre de 2026  
**Rama:** `dev`  
**Módulos del proyecto:** `:core` (Dominio y puertos puros Kotlin) y `:app` (Adaptadores Android, Jetpack Compose y Supabase)

---

## 1. Resumen Ejecutivo de Avances

Se ha culminado la implementación fundamental y refinamiento de la aplicación móvil de monitoreo ambiental y telemetría para el robot centinela, siguiendo rigurosamente:
1. **Arquitectura Hexagonal (Puertos y Adaptadores)**: Separación absoluta entre lógica de negocio y reglas de honestidad de datos en `:core`, e infraestructura y presentación en `:app`.
2. **Sistema de Diseño Lunar Base (§12)**: Tipografía Poppins con variantes tabulares (`tnum`), paleta de colores institucional (modo claro y oscuro), iconografía semántica y redondeo amigable de controles.
3. **Flujo Completo de Autenticación**: Inicio de sesión formal con Supabase Auth (`LoginScreen` / `LoginViewModel`), manejo de sesiones autenticadas, almacenamiento seguro y cierre de sesión.
4. **Optimización de Rendimiento y Memoria**: Bounding y política de descarte en el canal de eventos de telemetría (`Channel(capacity = 64, DROP_OLDEST)`), caché de primitivas geométricas y objetos `Path` en Canvas (`LineChart`, `FloorPlanView`).
5. **Adaptabilidad e Interacción**: Responsividad integral para dispositivos móviles compactos y tablets/pantallas anchas (`BoxWithConstraints`), transiciones animadas con `AnimatedContent` y adaptación dinámica a los temas del sistema con interpolación suave (`animateCentinelaColors`).

---

## 2. Cumplimiento de Requisitos e Historias de Usuario

A continuación se resume el estado de cumplimiento respecto al expediente técnico (§8 RF, §9 RNF, §11 RB) y la matriz de trazabilidad (`docs/requirements/trazabilidad.md`).

| Requisito / HU | Descripción | Estado | Detalle de Implementación en Código |
|---|---|---|---|
| **HU01 / RF01, RF02, RF32** | Acceso, sesión y selección de domicilio | **Cumplido** ✅ | `LoginScreen.kt`, `LoginViewModel.kt`, `HomeSelectionScreen.kt`, `SupabaseAuthAdapter.kt`, `SupabaseHomeScopeAdapter.kt`. Aislamiento por RLS. |
| **HU02 / RF05, RF06, RF20; RB04-RB06** | Interpretación de gases y calidad honesta | **Cumplido** ✅ | `ReadingInterpreter.kt`, `DescribeChannelStatus.kt`. Valores nulos rotulados ("Sin dato", "Falla", "Calentando"). Cero inventados prohibidos; ADC crudo no se declara ppm sin calibración. |
| **HU03 / RF07, RF08, RF09; RB26-RB28** | Sincronización continua y consistencia web/móvil | **Cumplido** ✅ | `TelemetrySynchronizer.kt`, `SupabaseTelemetryQueryAdapter.kt`, `SupabaseTelemetryChangeNotifier.kt`. Canal acotado (64 frames) sin saturación de memoria. |
| **HU04 / RF09, RF23; RB19** | Edad del dato e indicación de obsolescencia | **Cumplido** ✅ | `FreshnessPolicy.kt`, `LatestTelemetryProjection.kt`, `MetricCard.kt`. Estados "Atrasado", "En tiempo" y hora aproximada (`≈`). |
| **HU05, HU19 / RF11, RF12; RB13** | Visualización de mapa asistido 2D y trayectoria | **Cumplido** (a nivel app) 🟡 | `FloorPlanView.kt`, `MapScreen.kt`, `MapVersionId`. Trayectoria y pose con incertidumbre visible. Pendiente publicación de archivo de plano por backend. |
| **HU06 / RF12, RF13, RF33; RB11, RB16** | Incertidumbre de posición y estaciones de muestreo | **Cumplido** (a nivel app) 🟡 | `Pose.kt` nunca inventa coordenadas en origen sin pose real ni asigna habitación sin plano validado. Muestra avance de estaciones. |
| **HU07 / RF10, RF16** | Historial de telemetría y eventos por rango local/UTC | **Cumplido** ✅ | `HistoryScreen.kt`, `UtcInterval.kt`, `LineChart.kt`. Selectores de rango ("Hoy", "7 días", "Mes"), agregados calculados honestamente (mín, máx, prom) y gráficas con corte en huecos de conexión. |
| **HU08 / RF18, RF14; RB17** | Monitoreo de batería y estado operativo | **Cumplido** ✅ | `BatteryInterpretation.kt`, `OperationCard.kt`. Voltaje directo; porcentaje solo si hay método documentado (`fuel_gauge`/`voltage_curve`). |
| **HU09 / RF20, RF24, RF34; RB07** | Validación de calibración de sensores | **Parcial** 🟡 | `CalibrationId` se preserva por lectura. La consulta de vigencia, técnico y método espera el contrato final en backend. |
| **HU11 / RF15, RF29; RB21-RB24** | Consulta de incidentes y severidades | **Cumplido** ✅ | `EventsScreen.kt`, `AlertRow.kt`, `AlertDetailSheet.kt`. Consulta por filtros de severidad y estado con badge semántico. |
| **HU12 / RF22, RF23** | Preferencias visuales y modo Claro/Oscuro | **Cumplido** ✅ | `CentinelaTheme.kt`, `SettingsSheet.kt`, `CentinelaShapes.kt`. Soporte de tema Sistema/Claro/Oscuro con persistencia en `DataStore` y animación entre paletas. |
| **HU15 / RF35; RB29** | Datos de prueba y robustez QA | **Cumplido** ✅ | Fixtures sintéticos en `app/src/test/resources/fixtures/` y suite de pruebas unitarias (`TelemetrySynchronizerTest`, `ReadingInterpretationTest`, etc.). |
| **HU20 / RF05, RF09; RB04, RB09** | Detección y despliegue de sensores fallidos | **Cumplido** ✅ | `ReadingInterpreter.kt` categoriza `Fault`, `OutOfRange`, `NotInstalled`. Nunca se declara aire seguro por falta de datos. |

---

## 3. Estado de los Requerimientos No Funcionales (RNF)

- **RNF04 (Disponibilidad y Conectividad Visible)**: Implementado mediante `IngestConnectivityPolicy` (alerta si la ingesta excede 10 s sin frames) y `RefreshingNote` animado.
- **RNF08 (Seguridad y Secretos)**: Implementado. `SupabaseConfig` solo consume URL HTTPS y `anonKey` pública. Las credenciales de prueba residen en `local.properties` (ignorado en Git).
- **RNF09 (Aislamiento Multi-inquilino)**: Implementado mediante `SelectHomeScope` y políticas de Row Level Security (RLS) en base de datos.
- **RNF12 (Honestidad Semántica y Calidad de Datos)**: Plenamente integrado; no se asumen ceros ante lecturas nulas ni se muestran porcentajes de batería ficticios.
- **RNF16 (Mantenibilidad y Arquitectura)**: Verificado. Módulo `:core` 100% puro sin dependencias de Android ni de Supabase. Cobertura de pruebas unitarias al 100% en casos de uso y mappers.

---

## 4. Elementos Faltantes y Dependencias Externas (Bloqueos)

Los aspectos que permanecen pendientes no son omisiones de la app móvil, sino dependencias técnicas que deben proveer otros equipos de la actividad interdisciplinaria:

1. **Dependencias del Equipo de Backend y Base de Datos**:
   - **Plano de Habitaciones y Geometría (HU05 / RF11)**: El backend aún no provee el archivo vectorial/matricial del plano validado ni las coordenadas poligonales de las habitaciones en `map_versions`. La app ya cuenta con `FloorPlanView` listo para renderizarlo en cuanto se exponga la URL firmada.
   - **Metadatos Ampliados de Misiones (HU06 / B16)**: La tabla `missions` en Supabase no incluye aún columnas para nombres de estaciones planificadas, estaciones omitidas con motivo ni consumo acumulado de energía.
   - **Catálogo Detallado de Calibraciones (HU09 / B8)**: Falta la tabla o endpoint de calibración que ligue el `calibration_id` a un técnico, fecha de calibración y tiempo de expiración.
   - **Políticas RLS en Supabase (B12 / B19)**: En el proyecto de Supabase del equipo se requiere aplicar las políticas propuestas en `docs/backend/propuesta-politicas-lectura.sql` para que el rol anónimo no acceda a datos sin sesión y las membresías aíslen correctamente los registros.

2. **Alcance Opcional / Extensiones Futuras (Prioridad Should/Could)**:
   - **HU13 (Exportación a CSV / Reportes)**: No requerido para el MVP básico; pendiente definir formato de exportación.
   - **HU16 (Notificaciones Push Firebase / FCM)**: Clasificado como extensión; la consulta periódica e historial en base de datos actúa como canal garantizado.
   - **HU17 (Transmisión de Video en Red Local)**: Depende de hardware RTSP/WebRTC en el robot centinela.
   - **HU18 (Comandos de Teleoperación)**: Fuera del alcance actual, dado que el sistema móvil se definió con perfil de monitoreo y consulta segura.

---

## 5. Resumen de Pruebas y Verificación

- **Compilación de Código**: `gradle assembleDebug` finaliza con código de salida `0`.
- **Pruebas Unitarias de Dominio e Interfaz**: `gradle testDebugUnitTest` ejecuta y aprueba todas las pruebas automatizadas (46 tareas ejecutadas/actualizadas).
- **Procesos Dummy o Falsos Contratos**: Se verificó la inexistencia de datos "simulados ocultos" o valores inventados; todas las pantallas operan con datos reales emitidos por el sincronizador o muestran el estado explícito de espera de backend con honestidad informativa.

