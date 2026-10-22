package com.robotcentinela.app.shell.supabase

/**
 * ÚNICO lugar con los nombres de recursos de Supabase que consume la app.
 *
 * Los nombres siguen el esquema SQL que compartió el equipo (octubre de 2026, documentado en
 * docs/data_dictionary/frame-telemetria-v1.md). Si backend cambia un nombre, basta con actualizar este
 * archivo y el DTO del adaptador correspondiente.
 */
object SupabaseResources {

    /** Esquema expuesto por PostgREST. */
    const val SCHEMA = "public"

    /**
     * Cabecera de cada frame. Se consulta con sus tablas hijas embebidas
     * (`NormalizedFrameAssembler.SELECT`) y también se observa con Realtime: Postgres Changes funciona
     * sobre tablas y avisa al confirmarse la inserción (§7.4).
     */
    const val TELEMETRY_FRAMES_TABLE = "telemetry_frames"

    /** Membresías (§13.2). */
    const val MEMBERSHIPS = "memberships"

    /** Domicilios (§13.2). Se consultan embebidos en [MEMBERSHIPS]. */
    const val HOMES = "homes"

    /** Robots (§13.2). */
    const val ROBOTS = "robots"

    /** Alertas o incidentes (§13.2). La lógica que las genera vive en la base de datos. */
    const val ALERTS = "alerts"

    /** Catálogo de canales por robot: semántica y unidad de cada `channel_key` (RF05). */
    const val CHANNELS = "channels"

    /** Misiones (RF14). */
    const val MISSIONS = "missions"

    /** Columnas usadas en filtros y orden. */
    object Columns {
        const val ID = "id"
        const val ROBOT_ID = "robot_id"
        const val HOME_ID = "home_id"
        const val USER_ID = "user_id"
        const val STREAM_VERSION = "stream_version"
        const val CAPTURED_AT = "captured_at"
        const val MISSION_ID = "mission_id"
        const val OPENED_AT = "opened_at"
        const val STARTED_AT = "started_at"
        const val TYPE = "type"
        const val SEVERITY = "severity"
        const val STATE = "state"
    }
}
