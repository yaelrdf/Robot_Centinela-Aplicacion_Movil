package com.robotcentinela.app.incidents.adapters.supabase

import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.incidents.domain.Alert
import com.robotcentinela.app.incidents.domain.AlertId
import com.robotcentinela.app.incidents.domain.AlertOrigin
import com.robotcentinela.app.incidents.domain.AlertRuleId
import com.robotcentinela.app.incidents.domain.AlertState
import com.robotcentinela.app.incidents.ports.AlertFilter
import com.robotcentinela.app.incidents.ports.AlertQueryPort
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
 * Adaptador de solo lectura de [AlertQueryPort] sobre la tabla `alerts` (§13.2).
 *
 * La base de datos genera las alertas, aplica los umbrales y correlaciona los incidentes
 * (requisito del proyecto, HU11). La app nunca escribe en esta tabla (RB25).
 */
class SupabaseAlertQueryAdapter(private val supabase: SupabaseClient) : AlertQueryPort {

    override suspend fun alerts(filter: AlertFilter, page: PageRequest): List<Alert> = supabaseCall("historial de alertas") {
        val criteria = filter
        supabase.from(SupabaseResources.ALERTS)
            .select(PostgrestColumns.raw(ALERT_COLUMNS)) {
                filter {
                    eq(Columns.ROBOT_ID, criteria.robotId.value.toString())
                    criteria.interval?.let {
                        gte(Columns.OPENED_AT, it.start.toString())
                        lt(Columns.OPENED_AT, it.endExclusive.toString())
                    }
                    if (criteria.types.isNotEmpty()) isIn(Columns.TYPE, criteria.types.toList())
                    if (criteria.severities.isNotEmpty()) isIn(Columns.SEVERITY, criteria.severities.toList())
                    if (criteria.states.isNotEmpty()) isIn(Columns.STATE, criteria.states.map { it.raw })
                }
                order(Columns.OPENED_AT, Order.DESCENDING)
                range(page.offset, page.offset + page.limit - 1)
            }
            .decodeList<AlertRowDto>()
            .map { it.toDomain() }
    }

    private companion object {
        const val ALERT_COLUMNS =
            "id, robot_id, type, severity, state, origin, opened_at, last_seen_at, resolved_at"
    }
}

/**
 * Fila de `alerts` según el esquema de Supabase del equipo. El esquema no tiene `rule_id`; el campo se
 * conserva opcional por si backend lo agrega (§13.2).
 */
@Serializable
internal data class AlertRowDto(
    val id: String,
    @SerialName("robot_id") val robotId: String,
    @SerialName("rule_id") val ruleId: String? = null,
    val type: String,
    val severity: String,
    val state: String,
    val origin: String,
    @SerialName("opened_at") val openedAt: String,
    @SerialName("last_seen_at") val lastSeenAt: String,
    @SerialName("resolved_at") val resolvedAt: String? = null,
) {
    fun toDomain() = Alert(
        id = AlertId(UUID.fromString(id)),
        robotId = RobotId(UUID.fromString(robotId)),
        ruleId = ruleId?.let { AlertRuleId(UUID.fromString(it)) },
        type = type,
        severity = severity,
        state = AlertState.fromRaw(state),
        origin = AlertOrigin.fromRaw(origin),
        openedAt = OffsetDateTime.parse(openedAt).toInstant(),
        lastSeenAt = OffsetDateTime.parse(lastSeenAt).toInstant(),
        resolvedAt = resolvedAt?.let { OffsetDateTime.parse(it).toInstant() },
    )
}
