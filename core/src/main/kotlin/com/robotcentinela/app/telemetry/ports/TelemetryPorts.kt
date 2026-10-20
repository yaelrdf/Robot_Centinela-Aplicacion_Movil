package com.robotcentinela.app.telemetry.ports

import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.missions.domain.MissionId
import com.robotcentinela.app.shared.PageRequest
import com.robotcentinela.app.shared.UtcInterval
import com.robotcentinela.app.telemetry.domain.FrameRecord
import com.robotcentinela.app.telemetry.domain.StreamVersion
import kotlinx.coroutines.flow.Flow

/**
 * Resultado de una instantánea de telemetría consistente con marca de agua (watermark).
 *
 * @property records Registros que conforman el estado del robot.
 * @property watermark Versión del flujo hasta la cual la instantánea asegura consistencia; es nulo si el robot no tiene registros previos.
 */
data class TelemetrySnapshot(val records: List<FrameRecord>, val watermark: StreamVersion?)

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Puerto de salida para la consulta y recuperación de tramas de telemetría persistidas.
 */
interface TelemetryQueryPort {

    /** Último estado del robot con su watermark. */
    suspend fun snapshot(robotId: RobotId): TelemetrySnapshot

    /**
     * Frames con `stream_version` estrictamente mayor que [after], en orden ascendente, como máximo
     * [limit] (RF08: deltas persistidos).
     */
    suspend fun framesAfter(robotId: RobotId, after: StreamVersion, limit: Int): List<FrameRecord>

    /**
     * Historial por intervalo de captura en UTC, opcionalmente de una misión, paginado (RF10).
     * Orden ascendente por captura.
     */
    suspend fun framesBetween(
        robotId: RobotId,
        interval: UtcInterval,
        missionId: MissionId?,
        page: PageRequest,
    ): List<FrameRecord>
}

/** Notificaciones de cambio en el flujo de telemetría del robot en tiempo real. */
sealed interface TelemetryNotice {

    /** Confirma el establecimiento de la suscripción en tiempo real. */
    data object Subscribed : TelemetryNotice

    /** Indica la persistencia de una o más tramas nuevas en el backend. */
    data object FrameCommitted : TelemetryNotice

    /** Reporta una interrupción temporal en el canal de suscripción. */
    data class Interrupted(val reason: String) : TelemetryNotice
}

/** Puerto de salida para la suscripción a notificaciones de eventos en tiempo real. */
interface TelemetryChangeNotifier {
    fun notices(robotId: RobotId): Flow<TelemetryNotice>
}
