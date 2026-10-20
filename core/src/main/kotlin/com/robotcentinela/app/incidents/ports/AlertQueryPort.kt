package com.robotcentinela.app.incidents.ports

import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.incidents.domain.Alert
import com.robotcentinela.app.incidents.domain.AlertState
import com.robotcentinela.app.shared.PageRequest
import com.robotcentinela.app.shared.UtcInterval

/**
 * Filtros aplicables a la consulta del historial de alertas e incidentes.
 *
 * @property robotId Identificador del robot asociado a las alertas.
 * @property interval Intervalo temporal en UTC respecto a la fecha de apertura; es nulo para consultar sin restricción de tiempo.
 * @property types Conjunto de tipos de alerta a filtrar.
 * @property severities Conjunto de niveles de severidad a filtrar.
 * @property states Conjunto de estados de ciclo de vida de la alerta.
 */
data class AlertFilter(
    val robotId: RobotId,
    val interval: UtcInterval? = null,
    val types: Set<String> = emptySet(),
    val severities: Set<String> = emptySet(),
    val states: Set<AlertState> = emptySet(),
)

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Puerto de salida para la consulta y filtrado del historial de incidentes y alertas del robot.
 */
interface AlertQueryPort {

    /** Alertas que cumplen [filter], de la más reciente a la más antigua por `opened_at`. */
    suspend fun alerts(filter: AlertFilter, page: PageRequest): List<Alert>
}
