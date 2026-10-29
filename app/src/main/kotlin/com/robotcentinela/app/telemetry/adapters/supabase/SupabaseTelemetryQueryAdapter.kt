package com.robotcentinela.app.telemetry.adapters.supabase

import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.missions.domain.MissionId
import com.robotcentinela.app.shared.PageRequest
import com.robotcentinela.app.shared.UtcInterval
import com.robotcentinela.app.shell.supabase.SupabaseResources
import com.robotcentinela.app.shell.supabase.SupabaseResources.Columns
import com.robotcentinela.app.shell.supabase.supabaseCall
import com.robotcentinela.app.telemetry.domain.FrameRecord
import com.robotcentinela.app.telemetry.domain.StreamVersion
import com.robotcentinela.app.telemetry.ports.TelemetryQueryPort
import com.robotcentinela.app.telemetry.ports.TelemetrySnapshot
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns as PostgrestColumns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject

/**
 * Adaptador de [TelemetryQueryPort] sobre PostgREST de Supabase.
 *
 * Lee `telemetry_frames` con sus tablas hijas embebidas ([NormalizedFrameAssembler.SELECT]) y las
 * reconstruye con la forma de `JSON_estructura.txt` antes de interpretarlas con
 * [TelemetryFrameMapper]. El servidor aplica RLS por domicilio en cada tabla (RF02). El adaptador no
 * agrega permisos propios ni confía en IDs del cliente.
 *
 * Limitación conocida: el expediente pide un snapshot consistente con su watermark "bajo la misma
 * transacción de lectura" (§13.6). Una consulta PostgREST de un solo recurso lo cumple para una sola
 * fila. Un snapshot compuesto por varios recursos requerirá una RPC del backend (docs/pendientes.md).
 */
class SupabaseTelemetryQueryAdapter(
    private val supabase: SupabaseClient,
    /** `true` en la demo de lectura pública: pide también el payload original (ver [NormalizedFrameAssembler.SELECT_PUBLIC_READ]). */
    publicRead: Boolean = false,
) : TelemetryQueryPort {

    private val frameColumns = PostgrestColumns.raw(
        if (publicRead) NormalizedFrameAssembler.SELECT_PUBLIC_READ else NormalizedFrameAssembler.SELECT,
    )

    override suspend fun snapshot(robotId: RobotId): TelemetrySnapshot = supabaseCall("snapshot de telemetría") {
        val rows = supabase.from(SupabaseResources.TELEMETRY_FRAMES_TABLE).select(frameColumns) {
            filter { eq(Columns.ROBOT_ID, robotId.value.toString()) }
            order(Columns.STREAM_VERSION, Order.DESCENDING)
            limit(SNAPSHOT_FRAMES)
        }.decodeList<JsonObject>()
        val records = rows.map(::toRecord)
        TelemetrySnapshot(records, records.maxOfOrNull { it.streamVersion })
    }

    override suspend fun framesAfter(
        robotId: RobotId,
        after: StreamVersion,
        limit: Int,
    ): List<FrameRecord> = supabaseCall("deltas de telemetría") {
        supabase.from(SupabaseResources.TELEMETRY_FRAMES_TABLE).select(frameColumns) {
            filter {
                eq(Columns.ROBOT_ID, robotId.value.toString())
                gt(Columns.STREAM_VERSION, after.value)
            }
            order(Columns.STREAM_VERSION, Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList<JsonObject>().map(::toRecord)
    }

    override suspend fun framesBetween(
        robotId: RobotId,
        interval: UtcInterval,
        missionId: MissionId?,
        page: PageRequest,
    ): List<FrameRecord> = supabaseCall("historial de telemetría") {
        supabase.from(SupabaseResources.TELEMETRY_FRAMES_TABLE).select(frameColumns) {
            filter {
                eq(Columns.ROBOT_ID, robotId.value.toString())
                gte(Columns.CAPTURED_AT, interval.start.toString())
                lt(Columns.CAPTURED_AT, interval.endExclusive.toString())
                missionId?.let { eq(Columns.MISSION_ID, it.value.toString()) }
            }
            order(Columns.CAPTURED_AT, Order.ASCENDING)
            range(page.offset, page.offset + page.limit - 1)
        }.decodeList<JsonObject>().map(::toRecord)
    }

    private fun toRecord(row: JsonObject): FrameRecord =
        TelemetryFrameMapper.fromRow(NormalizedFrameAssembler.toContractRow(row))

    private companion object {
        /**
         * El snapshot devuelve el frame más reciente. Con la estructura actual cada frame trae todos
         * los canales del robot (`JSON_estructura.txt`).
         */
        const val SNAPSHOT_FRAMES = 1L
    }
}
