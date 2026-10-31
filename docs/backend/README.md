# Supabase: esquema, políticas y datos de prueba

| Archivo | Qué es | Quién lo aplica |
|---|---|---|
| `esquema-supabase-equipo.sql` | Copia de referencia del esquema que compartió el equipo (21 de octubre de 2026), sin cambios | Backend |
| `propuesta-politicas-lectura.sql` | **Propuesta**: políticas RLS de solo lectura por membresía activa, índices y Realtime | Backend (revisar y aplicar) |
| `propuesta-vista-telemetry-frames-v1.sql` | **Propuesta**: corrección de la vista `telemetry_frames_v1` que compartió el equipo (seguridad y campos faltantes) | Backend (revisar y aplicar) |
| `datos-de-prueba.sql` | Un domicilio, un robot, una misión, el catálogo de canales, 30 frames y 2 alertas para probar la app | Quien prueba, en un proyecto de pruebas |

## Por qué hacen falta las políticas

El esquema habilita RLS en las 14 tablas, pero no crea políticas. Con RLS activo y sin políticas,
Supabase no entrega ninguna fila a web ni a móvil: la app se conecta pero todo sale vacío. La
propuesta da acceso de **lectura** solo a usuarios autenticados con membresía `active` en el domicilio.
Nadie del lado cliente puede escribir: la ingesta escribe con su propia credencial de servidor.

## La vista `telemetry_frames_v1`

El equipo compartió una vista que arma cada frame con la forma de `JSON_estructura.txt`. Se probó con
el esquema y los datos de prueba, y tiene dos problemas:

1. **Se salta el RLS.** Una vista de PostgreSQL se ejecuta con los permisos de su dueño, salvo que se
   cree con `security_invoker = true`. Con la vista original, un usuario sin membresía **y el rol anon
   (sin sesión)** ven las 30 filas de prueba: cualquiera con la clave pública leería la telemetría de
   todos los domicilios.
2. **Le faltan campos obligatorios del contrato:** `schema_version`, `device_id` y `boot_id`. La app
   rechaza todas sus filas ("el frame no cumple la estructura del contrato v1").

`propuesta-vista-telemetry-frames-v1.sql` corrige ambos y además expone `firmware_version`,
`current_a` y el método de batería (`method_code`). Probada: el usuario con membresía ve sus 30 filas,
el usuario sin membresía 0, el rol anon recibe "permiso denegado", y la app acepta las filas (10
lecturas, pose, batería y sesión de arranque).

La app **no depende** de la vista: hoy lee `telemetry_frames` y sus tablas hijas directamente (con el
RLS de cada tabla). Si el equipo prefiere que web y móvil lean la misma vista corregida, cambiar la app
es un cambio pequeño en `SupabaseTelemetryQueryAdapter` (leer la vista en lugar de las tablas).

## Cómo probar la app con datos

1. En el SQL Editor de Supabase, corre en orden `esquema-supabase-equipo.sql` (si no está aplicado) y
   `propuesta-politicas-lectura.sql`.
2. En **Authentication > Users > Add user**, crea un usuario de prueba con correo y contraseña.
3. Cambia `v_email` en `datos-de-prueba.sql` por ese correo y córrelo (una sola vez).
4. En `local.properties` de la app:
   ```properties
   supabase.url=https://<proyecto>.supabase.co
   supabase.publishableKey=<clave pública: anon o sb_publishable_...>
   centinela.devEmail=<correo del usuario de prueba>
   centinela.devPassword=<contraseña del usuario de prueba>
   ```
5. Ejecuta la app (build debug). En el inicio de sesión, el botón de cuenta de prueba autocompleta el formulario; al entrar muestra "Casa de pruebas".

Nunca uses la clave `service_role` ni una `sb_secret_...` en la app: la app las rechaza al arrancar.

## Cómo se probó

Los tres archivos se aplicaron en PostgreSQL 17 (PGlite) con un esquema `auth` mínimo, como el de
Supabase. Resultados:

- El esquema del equipo se aplica sin errores.
- El usuario con membresía activa ve su domicilio, su robot, los 10 canales, la misión, las 2 alertas y
  el frame más reciente completo (10 lecturas, pose, batería, estado y sesión de arranque).
- Un usuario sin membresía no ve ninguna fila en las 14 tablas; el rol `anon` (sin sesión) tampoco.
- Al marcar la membresía como `inactive`, el usuario deja de ver la telemetría.
- `telemetry_frames` queda en la publicación de Realtime.

El frame que devolvió esa prueba se guardó como fixture
(`app/src/test/resources/fixtures/telemetry/frame_supabase_embebido.json`) y las pruebas de
`NormalizedFrameAssemblerTest` verifican que la app lo interpreta igual que `JSON_estructura.txt`.

## Estado del proyecto de Supabase (revisado el 29 de octubre de 2026)

Revisión de **solo lectura**, con la clave pública y **sin iniciar sesión** (rol `anon`). No se escribió
nada. El proyecto es el de pruebas del equipo; la URL y la clave no se versionan.

| Recurso | Con la clave pública, sin sesión |
|---|---|
| `homes`, `robots`, `devices`, `map_versions` | 1 fila cada una, legibles |
| `channels` | 12 filas, legibles (`front_distance_m`, `humidity`, `ir_floor_*`, `ir_lateral_*`, `mq4_raw`, `mq6_raw`, `mq7_raw`, `temperature`) |
| `telemetry_frames` | 2 filas (`stream_version` 1 y 2), legibles. El último, del 23 de octubre de 2026 |
| `sensor_readings` | 24 filas, legibles |
| `frame_poses`, `robot_states`, `battery_readings` | 2 filas cada una, legibles |
| `memberships`, `device_sessions`, `missions`, `alerts` | 0 filas (RLS o tablas vacías: no se distingue sin sesión) |
| `alert_occurrences`, vista `telemetry_frames_v1` | No existen (404) |
| Auth | Registro con correo abierto; confirmación por correo activa |

Conclusiones:

- **B19 sigue abierto:** la telemetría es legible con la clave pública que va dentro de cualquier APK.
- `telemetry_frames` del proyecto real tiene las columnas `schema_version` y `raw_payload` (el frame
  original), que el esquema compartido del 21 de octubre no traía.
- Sin `device_sessions` visible no se obtiene `boot_id`; la demo lo toma de `raw_payload`
  ([ADR-007](../decisions/ADR-007-demo-lectura-publica.md)).
- Pendiente de comprobar con un usuario real: que la membresía y las políticas de lectura (B12, B20,
  B21) funcionan. Hoy no existe ese usuario.
