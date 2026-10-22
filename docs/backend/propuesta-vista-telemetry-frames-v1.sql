-- =====================================================================================================
-- PROPUESTA de corrección a la vista telemetry_frames_v1 que compartió el equipo (21 de octubre de 2026).
--
-- Cambios respecto a la versión del equipo:
--   1. with (security_invoker = true): sin esto la vista se ejecuta con los permisos de su dueño
--      (postgres), se salta el RLS de las tablas y CUALQUIERA, incluso sin sesión (rol anon), ve la
--      telemetría de todos los domicilios. Probado: con la vista original un usuario sin membresía y el
--      rol anon ven las 30 filas de prueba; con esta, 0.
--   2. Agrega device_id, boot_id y firmware_version (vía device_sessions y devices). device_id y
--      boot_id son obligatorios en el contrato v1 (JSON_estructura.txt): sin ellos la app rechaza
--      todos los frames.
--   3. Agrega schema_version. El esquema no lo guarda; se expone '1.1' porque la ingesta solo persiste
--      frames que validó contra ese contrato. Si se agrega la columna a telemetry_frames, usarla aquí.
--   4. battery.quality toma method_code (p. ej. 'voltage_only', el método de estimación del JSON) y
--      agrega current_a.
--
-- Requiere PostgreSQL 15 o superior (Supabase lo es). Se aplica después de las políticas de lectura.
-- =====================================================================================================
drop view if exists public.telemetry_frames_v1;

create view public.telemetry_frames_v1
with (security_invoker = true)
as
select
    '1.1'::text as schema_version,
    f.id as event_id,
    f.id,
    f.robot_id,
    d.id as device_id,
    ds.boot_id,
    f.sequence,
    f.captured_at,
    f.received_at,
    f.monotonic_ms,
    f.clock_quality,
    f.stream_version,
    f.mission_id,
    p.map_version_id,
    case when p.frame_id is not null then
        jsonb_build_object(
            'x_m', p.x_m,
            'y_m', p.y_m,
            'theta_rad', p.theta_rad,
            'sigma_position_m', p.sigma_position_m,
            'quality', p.quality
        )
    end as pose,
    case when b.frame_id is not null then
        jsonb_build_object(
            'voltage_v', b.voltage_v,
            'soc_pct', b.soc_pct,
            'current_a', b.current_a,
            'quality', coalesce(nullif(b.method_code, ''), b.quality)
        )
    end as battery,
    s.reported_state as robot_state,
    s.motors_active,
    d.firmware_version,
    coalesce(
        (
            select jsonb_agg(
                jsonb_build_object(
                    'channel_key', c.channel_key,
                    'value', r.value,
                    'boolean_value', r.boolean_value,
                    'quality', r.quality,
                    'calibration_id', r.calibration_id
                )
            )
            from public.sensor_readings r
            join public.channels c on c.id = r.channel_id
            where r.frame_id = f.id
        ),
        '[]'::jsonb
    ) as readings
from public.telemetry_frames f
join public.device_sessions ds on ds.id = f.device_session_id
join public.devices d on d.id = ds.device_id
left join public.frame_poses p on p.frame_id = f.id
left join public.battery_readings b on b.frame_id = f.id
left join public.robot_states s on s.frame_id = f.id;

-- Solo lectura para usuarios con sesión; el RLS de las tablas decide qué filas ve cada quien.
revoke all on public.telemetry_frames_v1 from anon;
grant select on public.telemetry_frames_v1 to authenticated;
