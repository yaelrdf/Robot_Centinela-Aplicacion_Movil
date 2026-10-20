package com.robotcentinela.app.environmentalmonitoring.ports

import com.robotcentinela.app.environmentalmonitoring.domain.ChannelCapability
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelKey
import com.robotcentinela.app.environmentalmonitoring.domain.MetricAggregate
import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.shared.UtcInterval
import kotlin.time.Duration

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Puerto de salida para la consulta del catálogo de capacidades y especificaciones de sensores del robot.
 */
interface CapabilitiesPort {
    suspend fun capabilitiesOf(robotId: RobotId): List<ChannelCapability>
}

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Puerto de salida para la recuperación de agregados estadísticos y métricas consolidadas calculadas por el servidor.
 */
interface MetricAggregatePort {
    suspend fun aggregates(
        robotId: RobotId,
        channelKey: ChannelKey?,
        interval: UtcInterval,
        bucketSize: Duration,
    ): List<MetricAggregate>
}
