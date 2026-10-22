package com.robotcentinela.app.environmentalmonitoring.adapters.supabase

import com.robotcentinela.app.environmentalmonitoring.domain.ChannelCapability
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelKey
import com.robotcentinela.app.environmentalmonitoring.ports.CapabilitiesPort
import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.shell.supabase.SupabaseResources
import com.robotcentinela.app.shell.supabase.SupabaseResources.Columns
import com.robotcentinela.app.shell.supabase.supabaseCall
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns as PostgrestColumns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Catálogo de capacidades (RF05) sobre la tabla `channels`: qué significa cada `channel_key` del robot
 * y en qué unidad se registra. Con él la app muestra °C, % o ADC sin suponer nada.
 *
 * El esquema actual no tiene gas objetivo, periodo esperado ni bandera de habilitado; esos campos
 * quedan vacíos y la interfaz no los inventa (no atribuye un gas a un sensor sin catálogo, §13).
 */
class SupabaseCapabilitiesAdapter(private val supabase: SupabaseClient) : CapabilitiesPort {

    override suspend fun capabilitiesOf(robotId: RobotId): List<ChannelCapability> = supabaseCall("catálogo de canales") {
        supabase.from(SupabaseResources.CHANNELS)
            .select(PostgrestColumns.raw("channel_key, metric_type, unit_code")) {
                filter { eq(Columns.ROBOT_ID, robotId.value.toString()) }
            }
            .decodeList<ChannelRowDto>()
            .map { it.toDomain() }
    }
}

/** Fila de `channels`. */
@Serializable
internal data class ChannelRowDto(
    @SerialName("channel_key") val channelKey: String,
    @SerialName("metric_type") val metricType: String,
    @SerialName("unit_code") val unitCode: String? = null,
) {
    fun toDomain() = ChannelCapability(
        channelKey = ChannelKey(channelKey),
        metricType = metricType,
        unitCode = unitCode?.takeIf { it.isNotBlank() },
        gasTarget = null,
        expectedPeriod = null,
        enabled = true,
    )
}
