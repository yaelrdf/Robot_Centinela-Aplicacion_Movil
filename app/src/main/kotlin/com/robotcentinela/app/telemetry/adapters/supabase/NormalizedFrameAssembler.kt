package com.robotcentinela.app.telemetry.adapters.supabase

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reconstruye el frame v1 (`JSON_estructura.txt`) a partir del esquema NORMALIZADO de Supabase.
 *
 * La base guarda cada frame repartido en varias tablas (`telemetry_frames` y sus hijas
 * `sensor_readings`, `frame_poses`, `robot_states`, `battery_readings`, más `device_sessions` y
 * `devices`). PostgREST las devuelve embebidas en una sola consulta ([SELECT]). Este ensamblador las
 * vuelve a la forma del contrato para que [TelemetryFrameMapper] aplique las mismas reglas de siempre
 * (ADC nunca es ppm, `null` nunca es 0, etc.).
 *
 * Correspondencia (ver docs/data_dictionary/frame-telemetria-v1.md, "Esquema de Supabase"):
 * - `event_id` = `telemetry_frames.id`.
 * - `device_id` = `devices.id`, `boot_id` = `device_sessions.boot_id`.
 * - `map_version_id` y `pose` salen de `frame_poses`.
 * - `readings[].channel_key` sale de `channels.channel_key`.
 * - `battery.quality` es el método declarado (`battery_readings.method_code`, p. ej. `voltage_only`).
 * - `robot_state` = `robot_states.reported_state`.
 * - `firmware_version` = `devices.firmware_version` (firmware actual del dispositivo, no por frame).
 * - `schema_version`: el esquema no lo guarda; si la fila no lo trae se usa [STORED_SCHEMA_VERSION],
 *   porque la ingesta solo persiste frames que ya validó contra el contrato v1.
 */
object NormalizedFrameAssembler {

    /** Versión del contrato que representa una fila persistida sin columna `schema_version`. */
    const val STORED_SCHEMA_VERSION = "1.1"

    /** Columnas y relaciones embebidas que pide el adaptador (sintaxis de PostgREST). */
    const val SELECT =
        "id, robot_id, mission_id, sequence, captured_at, received_at, monotonic_ms, clock_quality, stream_version, " +
            "device_sessions(boot_id, devices(id, firmware_version)), " +
            "sensor_readings(value, boolean_value, quality, calibration_id, channels(channel_key)), " +
            "frame_poses(map_version_id, x_m, y_m, theta_rad, sigma_position_m, quality), " +
            "robot_states(reported_state, motors_active), " +
            "battery_readings(voltage_v, current_a, soc_pct, method_code, quality)"

    /**
     * Columnas adicionales del proyecto real de Supabase (no están en el esquema compartido del 21 de
     * octubre): `schema_version` y `raw_payload`, el frame original tal como lo publicó el dispositivo.
     * Solo las pide el modo de lectura pública, donde RLS oculta `device_sessions` y `devices`.
     */
    const val SELECT_PUBLIC_READ = "$SELECT, schema_version, raw_payload"

    /** Convierte una fila embebida de PostgREST en una fila con la forma del contrato v1. */
    fun toContractRow(row: JsonObject): JsonObject = buildJsonObject {
        // Respaldo cuando RLS oculta las tablas de sesión y dispositivo: el payload original conserva
        // los mismos identificadores. Con sesión de arranque visible, esta ruta no se usa.
        val payload = row["raw_payload"] as? JsonObject
        put(
            "schema_version",
            row["schema_version"].orNull() ?: payload?.get("schema_version").orNull() ?: JsonPrimitive(STORED_SCHEMA_VERSION),
        )
        copy(row, "id", "event_id")
        copy(row, "robot_id")
        copy(row, "mission_id")
        copy(row, "sequence")
        copy(row, "captured_at")
        copy(row, "received_at")
        copy(row, "monotonic_ms")
        copy(row, "clock_quality")
        copy(row, "stream_version")

        val session = row.one("device_sessions")
        val device = session?.one("devices")
        (session?.get("boot_id").orNull() ?: payload?.get("boot_id").orNull())?.let { put("boot_id", it) }
        (device?.get("id").orNull() ?: payload?.get("device_id").orNull())?.let { put("device_id", it) }
        (device?.get("firmware_version").orNull() ?: payload?.get("firmware_version").orNull())
            ?.let { put("firmware_version", it) }

        val pose = row.one("frame_poses")
        if (pose != null) {
            copy(pose, "map_version_id")
            put(
                "pose",
                buildJsonObject {
                    copy(pose, "x_m")
                    copy(pose, "y_m")
                    copy(pose, "theta_rad")
                    copy(pose, "sigma_position_m")
                    copy(pose, "quality")
                },
            )
        }

        put(
            "readings",
            buildJsonArray {
                row.many("sensor_readings").forEach { reading ->
                    val channelKey = reading.one("channels")?.get("channel_key").orNull()
                    add(
                        buildJsonObject {
                            channelKey?.let { put("channel_key", it) }
                            copy(reading, "value")
                            copy(reading, "boolean_value")
                            copy(reading, "quality")
                            copy(reading, "calibration_id")
                        },
                    )
                }
            },
        )

        row.one("battery_readings")?.let { battery ->
            put(
                "battery",
                buildJsonObject {
                    copy(battery, "voltage_v")
                    copy(battery, "soc_pct")
                    copy(battery, "current_a")
                    val method = battery["method_code"].orNull()?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    put("quality", method?.let { JsonPrimitive(it) } ?: battery["quality"].orNull() ?: JsonNull)
                },
            )
        }

        row.one("robot_states")?.let { state ->
            copy(state, "reported_state", "robot_state")
            copy(state, "motors_active")
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.copy(from: JsonObject, key: String, to: String = key) {
        from[key]?.let { put(to, it) }
    }

    private fun JsonElement?.orNull(): JsonElement? = if (this == null || this is JsonNull) null else this

    /**
     * Relación a uno. PostgREST devuelve un objeto (o `null`) cuando detecta la relación uno a uno, y
     * un arreglo en versiones que no la detectan; se aceptan ambas formas.
     */
    private fun JsonObject.one(key: String): JsonObject? = when (val value = this[key]) {
        is JsonObject -> value
        is JsonArray -> value.firstOrNull() as? JsonObject
        else -> null
    }

    /** Relación a muchos (lecturas del frame). */
    private fun JsonObject.many(key: String): List<JsonObject> = when (val value = this[key]) {
        is JsonArray -> value.filterIsInstance<JsonObject>()
        is JsonObject -> listOf(value)
        else -> emptyList()
    }
}
