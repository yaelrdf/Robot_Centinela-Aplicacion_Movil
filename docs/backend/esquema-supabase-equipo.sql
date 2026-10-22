-- Habilitar extensión UUID si no está activa
create extension if not exists "uuid-ossp";

-----------------------------------------------------------------
-- 1. DOMICILIOS Y MEMBRESÍAS (Ámbito y Seguridad)
-----------------------------------------------------------------
create table public.homes (
    id uuid primary key default gen_random_uuid(),
    name text not null check (length(trim(name)) > 0),
    timezone text not null default 'America/Mexico_City',
    created_at timestamptz not null default now()
);

create table public.memberships (
    home_id uuid not null references public.homes(id) on delete restrict,
    user_id uuid not null references auth.users(id) on delete restrict,
    role text not null check (role in ('reader', 'technician')),
    status text not null check (status in ('active', 'inactive')),
    primary key (home_id, user_id)
);

-----------------------------------------------------------------
-- 2. ROBOTS, DISPOSITIVOS Y SESIONES
-----------------------------------------------------------------
create table public.robots (
    id uuid primary key default gen_random_uuid(),
    home_id uuid not null references public.homes(id) on delete restrict,
    public_code text not null unique,
    last_stream_version bigint not null default 0 check (last_stream_version >= 0),
    status text not null default 'active'
);

create table public.devices (
    id uuid primary key default gen_random_uuid(),
    robot_id uuid not null references public.robots(id) on delete restrict,
    public_id text not null unique,
    status text not null default 'active',
    firmware_version text
);

create table public.device_sessions (
    id uuid primary key default gen_random_uuid(),
    device_id uuid not null references public.devices(id) on delete restrict,
    boot_id uuid not null,
    started_at timestamptz not null default now(),
    clock_quality text not null default 'unknown',
    unique (device_id, boot_id)
);

-----------------------------------------------------------------
-- 3. MAPAS Y MISIONES
-----------------------------------------------------------------
create table public.map_versions (
    id uuid primary key default gen_random_uuid(),
    home_id uuid not null references public.homes(id) on delete restrict,
    version_no int not null,
    status text not null check (status in ('draft', 'validated', 'retired')),
    resolution_m numeric not null default 0.05,
    origin_x numeric not null default 0,
    origin_y numeric not null default 0,
    width int not null,
    height int not null,
    asset_path text not null,
    checksum text not null,
    unique (home_id, version_no)
);

create table public.missions (
    id uuid primary key default gen_random_uuid(),
    robot_id uuid not null references public.robots(id) on delete restrict,
    map_version_id uuid not null references public.map_versions(id) on delete restrict,
    started_at timestamptz not null default now(),
    ended_at timestamptz,
    status text not null,
    stop_reason text
);

-----------------------------------------------------------------
-- 4. TELEMETRÍA (Frames, Lecturas, Pose y Estados)
-----------------------------------------------------------------
create table public.telemetry_frames (
    id uuid primary key, -- Corresponde al event_id del JSON
    robot_id uuid not null references public.robots(id) on delete restrict,
    device_session_id uuid not null references public.device_sessions(id) on delete restrict,
    mission_id uuid references public.missions(id) on delete set null,
    sequence bigint not null check (sequence >= 0),
    captured_at timestamptz,
    received_at timestamptz not null default now(),
    monotonic_ms bigint not null check (monotonic_ms >= 0),
    clock_quality text not null,
    stream_version bigint not null,
    payload_hash text not null,
    unique (device_session_id, sequence),
    unique (robot_id, stream_version)
);

-- Catálogos y canales normalizados
create table public.channels (
    id uuid primary key default gen_random_uuid(),
    robot_id uuid not null references public.robots(id) on delete restrict,
    channel_key text not null,
    metric_type text not null,
    unit_code text not null,
    unique (robot_id, channel_key)
);

create table public.sensor_readings (
    frame_id uuid not null references public.telemetry_frames(id) on delete cascade,
    channel_id uuid not null references public.channels(id) on delete restrict,
    value double precision,
    boolean_value boolean,
    quality text not null,
    calibration_id uuid,
    primary key (frame_id, channel_id),
    check (
        (value is not null and boolean_value is null) or
        (value is null and boolean_value is not null) or
        (value is null and boolean_value is null)
    )
);

create table public.frame_poses (
    frame_id uuid primary key references public.telemetry_frames(id) on delete cascade,
    map_version_id uuid not null references public.map_versions(id) on delete restrict,
    x_m double precision not null,
    y_m double precision not null,
    theta_rad double precision not null,
    sigma_position_m double precision check (sigma_position_m >= 0),
    sigma_heading_rad double precision check (sigma_heading_rad >= 0),
    quality text not null
);

create table public.robot_states (
    frame_id uuid primary key references public.telemetry_frames(id) on delete cascade,
    navigation_state text not null,
    reported_state text not null,
    motors_active boolean,
    wifi_rssi_dbm int
);

create table public.battery_readings (
    frame_id uuid primary key references public.telemetry_frames(id) on delete cascade,
    voltage_v double precision check (voltage_v >= 0),
    current_a double precision,
    soc_pct numeric check (soc_pct >= 0 and soc_pct <= 100),
    method_code text not null,
    quality text not null
);

-----------------------------------------------------------------
-- 5. ALERTAS E INCIDENTES
-----------------------------------------------------------------
create table public.alerts (
    id uuid primary key default gen_random_uuid(),
    robot_id uuid not null references public.robots(id) on delete restrict,
    type text not null,
    severity text not null check (severity in ('info', 'warning', 'critical')),
    state text not null check (state in ('open', 'acknowledged', 'resolved')),
    origin text not null check (origin in ('device_reported', 'backend_rule')),
    opened_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    resolved_at timestamptz
);

-----------------------------------------------------------------
-- 6. ÍNDICES DE RENDIMIENTO Y CONSULTA
-----------------------------------------------------------------
create index frames_capture_idx on public.telemetry_frames(robot_id, captured_at desc);
create index readings_channel_idx on public.sensor_readings(channel_id, frame_id);
create index alerts_robot_state_idx on public.alerts(robot_id, state);

-----------------------------------------------------------------
-- 7. SEGURIDAD: HABILITAR RLS
-----------------------------------------------------------------
alter table public.homes enable row level security;
alter table public.memberships enable row level security;
alter table public.robots enable row level security;
alter table public.devices enable row level security;
alter table public.device_sessions enable row level security;
alter table public.map_versions enable row level security;
alter table public.missions enable row level security;
alter table public.telemetry_frames enable row level security;
alter table public.channels enable row level security;
alter table public.sensor_readings enable row level security;
alter table public.frame_poses enable row level security;
alter table public.robot_states enable row level security;
alter table public.battery_readings enable row level security;
alter table public.alerts enable row level security;
