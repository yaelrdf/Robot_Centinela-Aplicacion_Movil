# Arquitectura hexagonal de la app móvil

## Objetivo

Separar las reglas del proyecto Robot Centinela de los detalles técnicos (Android, Supabase, la
futura UI). Así:

- las reglas se prueban en JVM sin emulador ni red (RNF16: "cada regla tiene un responsable y una
  prueba de comportamiento");
- el acceso a Supabase, MQTT o notificaciones se puede cambiar sin tocar los casos de uso (RNF18);
- la UI del equipo de Ingeniería de Software se conecta después, consumiendo casos de uso ya probados.

## Capas

| Capa | Ubicación | Contiene | Puede depender de |
|---|---|---|---|
| **domain** | `:core` · `<dominio>/domain` | Entidades, objetos de valor y reglas puras (`ReadingInterpreter`, `FreshnessPolicy`, `BatteryInterpretation`, `LatestTelemetryProjection`…) | Solo la biblioteca estándar y `java.time` |
| **ports** | `:core` · `<dominio>/ports` | Interfaces que el dominio necesita del exterior (`TelemetryQueryPort`, `AuthPort`…) | domain |
| **application** | `:core` · `<dominio>/application` | Casos de uso que coordinan dominio y puertos (`TelemetrySynchronizer`, `SelectHomeScope`…) | domain, ports, coroutines |
| **adapters** | `:app` · `<dominio>/adapters/supabase` | Implementaciones de los puertos con supabase-kt, más los DTO | Todo lo anterior y el SDK |
| **shell** (composición) | `:app` · `shell/` | `AppContainer` (DI manual) y `CentinelaApplication` | Todo |

**Regla de dependencias:** las flechas apuntan hacia el dominio. `:core` no declara dependencias de
Android, Supabase ni Ktor, así que el compilador impide violar la regla. Se puede verificar con:

```bash
grep -rE "import (android|io\.github\.jan|io\.ktor)" core/   # no debe devolver nada
```

## Dominios (Screaming Architecture)

El expediente (§14.1) propone que la primera lectura del árbol revele el negocio. Correspondencia
con su propuesta para `mobile/src/`:

| Paquete Kotlin | Nombre en el expediente | Responsabilidad en esta versión |
|---|---|---|
| `identity` | `identity/` | Sesión, membresías y ámbito de domicilio (RF01, RF02) |
| `environmentalmonitoring` | `environmental_monitoring/` | Lecturas, calidad, frescura, capacidades y agregados (RF05, RF09, RF10) |
| `robothealth` | `robot_health/` | Batería, estado reportado e ingesta (RF18, RF19, RNF04) |
| `homemapping` | `home_mapping/` | Pose e ID de versión de mapa (RF12). La geometría está pendiente |
| `missions` | `missions/` | ID de misión. El resumen (RF14) está pendiente |
| `incidents` | `incidents/` | Alertas persistidas, de solo lectura (RF15, RF16) |
| `telemetry` | (agregado del frame) | Frame, `stream_version`, proyección del último dato, sincronización e historial (RF06 a RF08) |
| `shell` | `shell/` | Arranque y composición |

Los paquetes Kotlin van en minúsculas y sin guiones bajos, según la convención del lenguaje.
`telemetry` agrupa el frame porque es la unidad que se sincroniza (§7.4), y un frame reúne lecturas,
pose, batería y estado.

## Puertos y adaptadores

| Puerto (`:core`) | Adaptador (`:app`) | Recurso Supabase | Estado |
|---|---|---|---|
| `AuthPort` | `SupabaseAuthAdapter` | Supabase Auth (correo y contraseña) | Implementado |
| `HomeScopePort` | `SupabaseHomeScopeAdapter` | `memberships`, `homes`, `robots` | Implementado con el esquema del equipo |
| `TelemetryQueryPort` | `SupabaseTelemetryQueryAdapter` | `telemetry_frames` + `sensor_readings`, `frame_poses`, `robot_states`, `battery_readings`, `device_sessions`, `devices` | Implementado con el esquema del equipo |
| `TelemetryChangeNotifier` | `SupabaseTelemetryChangeNotifier` | Realtime sobre `telemetry_frames` | Implementado |
| `AlertQueryPort` | `SupabaseAlertQueryAdapter` | `alerts` | Implementado con el esquema del equipo |
| `CapabilitiesPort` | `SupabaseCapabilitiesAdapter` | `channels` | Implementado (sin gas objetivo ni periodo) |
| `MissionQueryPort` | `SupabaseMissionQueryAdapter` | `missions` | Implementado (sin estaciones ni energía) |
| `MetricAggregatePort` | — | `metric_aggregates` (§13.2) | Pendiente de contrato |

Todos los nombres de recursos están en un solo archivo:
`app/src/main/kotlin/com/robotcentinela/app/shell/supabase/SupabaseResources.kt`.

## Errores

Los adaptadores traducen las excepciones del SDK (`supabaseCall`) a los tipos de
`shared/CentinelaException.kt`:

| Error | Cuándo |
|---|---|
| `AccessDeniedException` | 401 o 403, o no hay sesión (RF01, RF02) |
| `NotInScopeException` | El robot no pertenece al domicilio seleccionado (comprobación del cliente) |
| `DataAccessException` | Fallo de red o del servicio. **No** significa robot apagado (RF29) |
| `ContractViolationException` | La respuesta no cumple el contrato |

Los mensajes nunca incluyen payloads ni cuerpos de respuesta (RNF19).

## Seguridad

- La autorización real la aplica el servidor (RLS). Las comprobaciones del cliente (`CurrentHomeScope`)
  solo evitan consultas fuera de ámbito; ocultar botones no protege nada (RNF09).
- La app solo acepta la clave pública de Supabase y una URL HTTPS (`SupabaseConfig`, RNF08).
- La app no escribe telemetría, configuración ni alertas (RB25).

## Modo de lectura pública (build `demo`)

La demostración beta no agrega capas: cambia el cableado de `shell/Adapters.kt`. Con
`BuildConfig.DEMO_PUBLIC_READ` activo, `Adapters.supabase(..., publicRead = true)` sustituye dos
adaptadores y deja el resto igual:

| Puerto | Flujo normal | Build `demo` |
|---|---|---|
| `AuthPort` | `SupabaseAuthAdapter` (Supabase Auth) | `PublicReadAuthAdapter` (sesión fija, sin permisos) |
| `HomeScopePort` | `SupabaseHomeScopeAdapter` (`memberships`) | `PublicReadHomeScopeAdapter` (`homes` visibles para `anon`; robots igual que el flujo normal) |
| `TelemetryQueryPort` | `SupabaseTelemetryQueryAdapter` | Mismo adaptador, con `schema_version` y `raw_payload` en la consulta |

`:core` no cambia y no sabe que existe este modo. Es una decisión temporal
([ADR-007](../decisions/ADR-007-demo-lectura-publica.md)) que se retira cuando exista el usuario de pruebas con membresía (B21).
