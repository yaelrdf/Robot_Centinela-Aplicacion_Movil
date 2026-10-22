-- =====================================================================================================
-- PROPUESTA para el equipo de backend: políticas RLS de LECTURA y Realtime para web y móvil.
--
-- Se aplica DESPUÉS de esquema-supabase-equipo.sql (el esquema que compartió el equipo). Ese esquema
-- habilita RLS en todas las tablas pero no crea políticas, y sin políticas Supabase no entrega ninguna
-- fila a la app. Aquí solo hay políticas SELECT: web y móvil consultan, nunca escriben telemetría ni
-- alertas (la ingesta escribe con su propia credencial de servidor, que no pasa por RLS).
--
-- Regla única: una persona autenticada ve un domicilio, y todo lo que cuelga de él, solo si tiene una
-- membresía ACTIVA en ese domicilio (punto 3 de las notas del esquema; RF02).
--
-- Probado en PostgreSQL 17 (PGlite) con el esquema del equipo: ver docs/backend/README.md.
-- =====================================================================================================

-- -----------------------------------------------------------------------------------------------------
-- 1. Funciones auxiliares
--    SECURITY DEFINER para consultar memberships sin volver a aplicar su propia política (evita
--    recursión) y "set search_path = ''" para que no se puedan secuestrar con objetos de otro esquema.
-- -----------------------------------------------------------------------------------------------------
create or replace function public.is_home_member(target_home uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.memberships m
        where m.home_id = target_home
          and m.user_id = (select auth.uid())
          and m.status = 'active'
    );
$$;

create or replace function public.can_read_robot(target_robot uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.robots r
        join public.memberships m on m.home_id = r.home_id
        where r.id = target_robot
          and m.user_id = (select auth.uid())
          and m.status = 'active'
    );
$$;

create or replace function public.can_read_frame(target_frame uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.telemetry_frames f
        join public.robots r on r.id = f.robot_id
        join public.memberships m on m.home_id = r.home_id
        where f.id = target_frame
          and m.user_id = (select auth.uid())
          and m.status = 'active'
    );
$$;

revoke all on function public.is_home_member(uuid) from public;
revoke all on function public.can_read_robot(uuid) from public;
revoke all on function public.can_read_frame(uuid) from public;
grant execute on function public.is_home_member(uuid) to authenticated;
grant execute on function public.can_read_robot(uuid) to authenticated;
grant execute on function public.can_read_frame(uuid) to authenticated;

-- -----------------------------------------------------------------------------------------------------
-- 2. Políticas de lectura (solo usuarios autenticados; el rol anon no ve nada)
-- -----------------------------------------------------------------------------------------------------

-- Domicilios y membresías
create policy "lectura: domicilios con membresía activa" on public.homes
    for select to authenticated using (public.is_home_member(id));

create policy "lectura: mis membresías" on public.memberships
    for select to authenticated using (user_id = (select auth.uid()));

-- Robots, dispositivos y sesiones de arranque
create policy "lectura: robots del domicilio" on public.robots
    for select to authenticated using (public.is_home_member(home_id));

create policy "lectura: dispositivos del robot" on public.devices
    for select to authenticated using (public.can_read_robot(robot_id));

create policy "lectura: sesiones de dispositivo" on public.device_sessions
    for select to authenticated using (
        exists (select 1 from public.devices d where d.id = device_id and public.can_read_robot(d.robot_id))
    );

-- Mapas y misiones
create policy "lectura: versiones de mapa del domicilio" on public.map_versions
    for select to authenticated using (public.is_home_member(home_id));

create policy "lectura: misiones del robot" on public.missions
    for select to authenticated using (public.can_read_robot(robot_id));

-- Telemetría
create policy "lectura: frames del robot" on public.telemetry_frames
    for select to authenticated using (public.can_read_robot(robot_id));

create policy "lectura: canales del robot" on public.channels
    for select to authenticated using (public.can_read_robot(robot_id));

create policy "lectura: lecturas del frame" on public.sensor_readings
    for select to authenticated using (public.can_read_frame(frame_id));

create policy "lectura: pose del frame" on public.frame_poses
    for select to authenticated using (public.can_read_frame(frame_id));

create policy "lectura: estado del robot en el frame" on public.robot_states
    for select to authenticated using (public.can_read_frame(frame_id));

create policy "lectura: batería del frame" on public.battery_readings
    for select to authenticated using (public.can_read_frame(frame_id));

-- Alertas
create policy "lectura: alertas del robot" on public.alerts
    for select to authenticated using (public.can_read_robot(robot_id));

-- -----------------------------------------------------------------------------------------------------
-- 3. Índices que usan las políticas y las consultas de la app
-- -----------------------------------------------------------------------------------------------------
create index if not exists memberships_user_idx on public.memberships(user_id, home_id) where status = 'active';
create index if not exists robots_home_idx on public.robots(home_id);
create index if not exists frames_stream_idx on public.telemetry_frames(robot_id, stream_version desc);
create index if not exists missions_robot_idx on public.missions(robot_id, started_at desc);
create index if not exists channels_robot_idx on public.channels(robot_id);

-- -----------------------------------------------------------------------------------------------------
-- 4. Realtime: la app se suscribe a las inserciones de telemetry_frames filtradas por robot_id.
--    El aviso solo dispara una consulta; los datos siempre se leen por PostgREST con RLS.
-- -----------------------------------------------------------------------------------------------------
alter publication supabase_realtime add table public.telemetry_frames;
