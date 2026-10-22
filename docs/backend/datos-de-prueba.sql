-- =====================================================================================================
-- Datos de PRUEBA para ver la app con información real de la base (no es parte del esquema).
--
-- Antes de correrlo:
--   1. Aplica esquema-supabase-equipo.sql y propuesta-politicas-lectura.sql.
--   2. Crea un usuario de prueba en Supabase (Authentication > Users > Add user) con correo y
--      contraseña. Esos mismos valores van en local.properties de la app:
--      centinela.devEmail y centinela.devPassword.
--   3. Cambia el correo de la línea "v_email" de abajo por el de ese usuario.
--
-- Crea un domicilio, un robot con su dispositivo, una misión en curso, el catálogo de canales, 30
-- frames de los últimos 15 minutos (el último es el de JSON_estructura.txt, con la hora actual) y dos
-- alertas. Se corre en el SQL Editor (rol postgres), que no pasa por RLS.
-- =====================================================================================================
do $$
declare
    v_email   text := 'prueba@ejemplo.com';  -- <-- correo del usuario de prueba
    v_user    uuid;
    v_home    uuid := '11111111-1111-4111-8111-111111111111';
    v_robot   uuid := '22222222-2222-4222-8222-222222222222';
    v_device  uuid := 'bf8d0f7d-f739-4529-9a8a-5975f5b26960';  -- device_id de JSON_estructura.txt
    v_session uuid := '33333333-3333-4333-8333-333333333333';
    v_boot    uuid := 'b503cb0b-3866-491b-aebc-e164ea73d9aa';  -- boot_id de JSON_estructura.txt
    v_map     uuid := 'ecb3df81-477f-4e72-92c6-8b38f856cdb5';  -- map_version_id de JSON_estructura.txt
    v_mission uuid := '0354909f-cc35-4ebc-822d-bfffeaa80374';  -- mission_id de JSON_estructura.txt
    v_frame   uuid;
    v_at      timestamptz;
    i         int;
    n         int := 30;
begin
    select id into v_user from auth.users where email = v_email;
    if v_user is null then
        raise exception 'No existe el usuario %: créalo primero en Authentication > Users', v_email;
    end if;

    insert into public.homes (id, name, timezone) values (v_home, 'Casa de pruebas', 'America/Mexico_City');
    insert into public.memberships (home_id, user_id, role, status) values (v_home, v_user, 'reader', 'active');
    insert into public.robots (id, home_id, public_code) values (v_robot, v_home, 'CENTINELA-01');
    insert into public.devices (id, robot_id, public_id, firmware_version)
        values (v_device, v_robot, v_device::text, '0.2.0');
    insert into public.device_sessions (id, device_id, boot_id, started_at, clock_quality)
        values (v_session, v_device, v_boot, now() - interval '16 minutes', 'synced');
    insert into public.map_versions (id, home_id, version_no, status, width, height, asset_path, checksum)
        values (v_map, v_home, 1, 'validated', 120, 90, 'maps/prueba/v1.png', 'pendiente');
    insert into public.missions (id, robot_id, map_version_id, started_at, status)
        values (v_mission, v_robot, v_map, now() - interval '15 minutes', 'running');

    -- Catálogo de canales (unidad registrada de cada channel_key)
    insert into public.channels (robot_id, channel_key, metric_type, unit_code) values
        (v_robot, 'mq6_raw', 'gas_raw', 'adc_count'),
        (v_robot, 'mq4_raw', 'gas_raw', 'adc_count'),
        (v_robot, 'mq7_raw', 'gas_raw', 'adc_count'),
        (v_robot, 'temperature', 'temperature', 'celsius'),
        (v_robot, 'humidity', 'relative_humidity', 'percent'),
        (v_robot, 'front_distance_m', 'distance', 'm'),
        (v_robot, 'ir_floor_front_left', 'floor_presence', 'boolean'),
        (v_robot, 'ir_floor_front_right', 'floor_presence', 'boolean'),
        (v_robot, 'ir_floor_rear_left', 'floor_presence', 'boolean'),
        (v_robot, 'ir_floor_rear_right', 'floor_presence', 'boolean');

    -- Frames: cada 30 s durante los últimos 15 minutos. El último reproduce JSON_estructura.txt.
    for i in 1..n loop
        v_at := now() - make_interval(secs => (n - i) * 30);
        v_frame := case when i = n then '9f225860-42f2-4cd0-8c3a-e4e970395307'::uuid else gen_random_uuid() end;

        insert into public.telemetry_frames (id, robot_id, device_session_id, mission_id, sequence, captured_at,
                                             received_at, monotonic_ms, clock_quality, stream_version, payload_hash)
        values (v_frame, v_robot, v_session, v_mission, 1813 + i, v_at, v_at + interval '1 second',
                929450 - (n - i) * 30000, 'synced', i, md5(v_frame::text));

        insert into public.sensor_readings (frame_id, channel_id, value, boolean_value, quality, calibration_id)
        select v_frame, c.id, r.value, r.boolean_value, r.quality, null
        from public.channels c
        join (values
            ('mq6_raw',              case when i = n then 1860 else 1700 + (i * 37) % 200 end::double precision, null::boolean, 'uncalibrated'),
            ('mq4_raw',              case when i = n then 1420 else 1350 + (i * 23) % 120 end::double precision, null, 'uncalibrated'),
            ('mq7_raw',              null, null, 'not_installed'),
            ('temperature',          case when i = n then 24.6 else 23.8 + (i % 7) * 0.15 end, null, 'valid'),
            ('humidity',             case when i = n then 48.1 else 46.5 + (i % 5) * 0.4 end, null, 'valid'),
            ('front_distance_m',     case when i = n then 0.45 else 0.40 + (i % 6) * 0.1 end, null, 'valid'),
            ('ir_floor_front_left',  null, true, 'valid'),
            ('ir_floor_front_right', null, true, 'valid'),
            ('ir_floor_rear_left',   null, true, 'valid'),
            ('ir_floor_rear_right',  null, true, 'valid')
        ) as r(channel_key, value, boolean_value, quality) on r.channel_key = c.channel_key
        where c.robot_id = v_robot;

        insert into public.frame_poses (frame_id, map_version_id, x_m, y_m, theta_rad, sigma_position_m, quality)
        values (v_frame, v_map,
                case when i = n then 2.15 else 0.3 + i * 0.065 end,
                case when i = n then 1.40 else 0.5 + sin(i / 4.0) * 0.6 end,
                0.28, 0.08, 'estimated');

        insert into public.robot_states (frame_id, navigation_state, reported_state, motors_active)
        values (v_frame, 'moving', 'moving', true);

        insert into public.battery_readings (frame_id, voltage_v, current_a, soc_pct, method_code, quality)
        values (v_frame, round((11.8 - i * 0.013)::numeric, 2), null, null, 'voltage_only', 'valid');
    end loop;

    update public.robots set last_stream_version = n where id = v_robot;

    insert into public.alerts (robot_id, type, severity, state, origin, opened_at, last_seen_at, resolved_at) values
        (v_robot, 'GAS_CRITICO', 'warning', 'resolved', 'backend_rule',
         now() - interval '2 days', now() - interval '2 days' + interval '6 minutes', now() - interval '2 days' + interval '10 minutes'),
        (v_robot, 'ABISMO_DETECTADO', 'critical', 'open', 'device_reported',
         now() - interval '10 minutes', now() - interval '8 minutes', null);
end $$;
