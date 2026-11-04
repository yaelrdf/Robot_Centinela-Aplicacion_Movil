-- =====================================================================================================
-- Migración: función resolve_alert (ADR-011, versión 0.7.0-beta de la app móvil).
--
-- Cierra una alerta (state = 'resolved', resolved_at = now()). Es la contraparte de ack_alert, que solo
-- la pasa a 'acknowledged'. La app NO actualiza `alerts` directamente: RLS solo tiene políticas SELECT.
--
-- Permisos: solo un técnico ACTIVO del domicilio del robot (public.is_home_technician). Una persona sin
-- ese rol recibe SQLSTATE 42501, que PostgREST devuelve como HTTP 403 (la app lo muestra como "Solo un
-- técnico del domicilio puede resolver alertas"). El rol anon no puede ejecutarla.
--
-- Efecto: si la condición persiste, la ingesta (ingest_telemetry) abrirá un incidente nuevo en el siguiente
-- frame, porque el índice único de alertas solo cubre los estados open y acknowledged.
--
-- Reversión: drop function public.resolve_alert(uuid);
-- =====================================================================================================

create or replace function public.resolve_alert(p_alert_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_home uuid;
begin
    select rb.home_id into v_home
    from public.alerts a
    join public.robots rb on rb.id = a.robot_id
    where a.id = p_alert_id;

    if v_home is null or not public.is_home_technician(v_home) then
        raise exception 'sin permiso para resolver esta alerta' using errcode = '42501';
    end if;

    update public.alerts
       set state = 'resolved', resolved_at = now()
     where id = p_alert_id and state in ('open', 'acknowledged');

    return found;
end;
$$;

revoke execute on function public.resolve_alert(uuid) from public, anon;
grant execute on function public.resolve_alert(uuid) to authenticated;
