# Solicitud: usuario de pruebas con membresía para la app móvil

**Para:** encargado de la base de datos de Supabase (equipo de backend/IoT).
**De:** equipo de la aplicación móvil. **Fecha:** 30 de octubre de 2026.

La app ya tiene inicio de sesión real con Supabase Auth, pero con el RLS del esquema vigente
(`Centinela Basse de Datos.sql`, bloque `05_rls`) una cuenta **sin membresía activa no ve nada**. El
esquema no crea usuarios ni membresías, así que hoy no existe una cuenta con la que la app pueda mostrar
datos (B21 de [pendientes](../pendientes.md)). Sin ella solo funciona el build `demo`, que depende de que
el rol `anon` siga leyendo (B19).

## Qué se pide

1. **Crear un usuario** en *Authentication → Users* (correo y contraseña, con "Auto Confirm User"). El
   equipo móvil no necesita ver la contraseña en ningún documento: basta con entregarla por un canal privado.
2. **Insertar su membresía** en el domicilio que tiene el robot y la telemetría (hoy "Casa Demo
   Centinela"), con rol `reader` (solo lectura) y estado `active`:

   ```sql
   insert into public.memberships (home_id, user_id, role, status)
   select h.id, u.id, 'reader', 'active'
   from public.homes h, auth.users u
   where h.name = 'Casa Demo Centinela'
     and u.email = '<correo del usuario de pruebas>'
   on conflict (home_id, user_id) do update set role = excluded.role, status = 'active';
   ```

3. **Confirmar que `05_rls` está aplicado tal cual.** El 30 de octubre de 2026 el proyecto vivo no coincidía
   con el script: el rol `anon` (sin sesión) leía `homes`, `robots`, `devices`, `channels`, `channel_catalog`,
   `gas_calibrations`, `telemetry_frames` y sus tablas hijas, y el script las limita `to authenticated` con
   `is_home_member`. Al corregirlo, el APK `demo` dejará de mostrar datos; es el comportamiento esperado.
4. **Verificar que existen filas en `device_sessions`** para el dispositivo del robot (las crea
   `ingest_telemetry`). La app las necesita para `boot_id` y `device_id`; sin ellas rechaza los frames.

## Cómo comprobarlo

Con el usuario creado, en el SQL Editor:

```sql
select m.role, m.status, h.name
from public.memberships m join public.homes h on h.id = m.home_id
join auth.users u on u.id = m.user_id
where u.email = '<correo del usuario de pruebas>';
```

Debe devolver una fila `reader | active | Casa Demo Centinela`. En la app, al iniciar sesión debe aparecer
ese domicilio en el selector.

## Lo que la app no hace

No escribe nada en la base (no llama a `ingest_telemetry` ni a `ack_alert`) y rechaza las claves
`service_role` y `sb_secret_…`. Con el rol `reader` es suficiente.
