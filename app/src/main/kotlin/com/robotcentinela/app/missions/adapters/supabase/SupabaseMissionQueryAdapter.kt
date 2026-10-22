package com.robotcentinela.app.missions.adapters.supabase

import com.robotcentinela.app.homemapping.domain.MapVersionId
import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.missions.domain.MissionId
import com.robotcentinela.app.missions.domain.MissionStatus
import com.robotcentinela.app.missions.domain.MissionSummary
import com.robotcentinela.app.missions.ports.MissionQueryPort
import com.robotcentinela.app.shared.PageRequest
import com.robotcentinela.app.shell.supabase.SupabaseResources
import com.robotcentinela.app.shell.supabase.SupabaseResources.Columns
import com.robotcentinela.app.shell.supabase.supabaseCall
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.Columns as PostgrestColumns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Misiones (RF14) sobre la tabla `missions`, de solo lectura.
 *
 * El esquema actual guarda inicio, fin, estado, motivo de paro y versión de mapa. No guarda nombre,
 * propósito, estaciones ni energía: esos campos quedan en `null` y la interfaz los muestra como
 * pendientes, nunca como cero (docs/pendientes.md, B9).
 */
class SupabaseMissionQueryAdapter(private val supabase: SupabaseClient) : MissionQueryPort {

    override suspend fun missions(robotId: RobotId, page: PageRequest): List<MissionSummary> = supabaseCall("misiones") {
        supabase.from(SupabaseResources.MISSIONS)
            .select(COLUMNS) {
                filter { eq(Columns.ROBOT_ID, robotId.value.toString()) }
                order(Columns.STARTED_AT, Order.DESCENDING)
                range(page.offset, page.offset + page.limit - 1)
            }
            .decodeList<MissionRowDto>()
            .map { it.toDomain() }
    }

    override suspend fun mission(robotId: RobotId, missionId: MissionId): MissionSummary? = supabaseCall("resumen de misión") {
        supabase.from(SupabaseResources.MISSIONS)
            .select(COLUMNS) {
                filter {
                    eq(Columns.ROBOT_ID, robotId.value.toString())
                    eq(Columns.ID, missionId.value.toString())
                }
                limit(1)
            }
            .decodeList<MissionRowDto>()
            .firstOrNull()
            ?.toDomain()
    }

    private companion object {
        val COLUMNS = PostgrestColumns.raw("id, robot_id, map_version_id, started_at, ended_at, status, stop_reason")
    }
}

/** Fila de `missions`. */
@Serializable
internal data class MissionRowDto(
    val id: String,
    @SerialName("robot_id") val robotId: String,
    @SerialName("map_version_id") val mapVersionId: String? = null,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    val status: String,
    @SerialName("stop_reason") val stopReason: String? = null,
) {
    fun toDomain() = MissionSummary(
        id = MissionId(UUID.fromString(id)),
        robotId = RobotId(UUID.fromString(robotId)),
        name = null,
        purpose = null,
        mapVersionId = mapVersionId?.let { MapVersionId(UUID.fromString(it)) },
        startedAt = OffsetDateTime.parse(startedAt).toInstant(),
        endedAt = endedAt?.let { OffsetDateTime.parse(it).toInstant() },
        status = MissionStatus.fromRaw(status),
        stopReason = stopReason?.takeIf { it.isNotBlank() },
        stations = null,
        energy = null,
    )
}
