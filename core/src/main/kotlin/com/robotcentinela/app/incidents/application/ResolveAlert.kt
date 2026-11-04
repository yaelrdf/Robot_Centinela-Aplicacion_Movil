package com.robotcentinela.app.incidents.application

import com.robotcentinela.app.identity.application.CurrentHomeScope
import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.incidents.domain.AlertId
import com.robotcentinela.app.incidents.ports.AlertCommandPort

/**
 * Caso de uso: resolver una alerta del domicilio seleccionado (ADR-011).
 *
 * Solo opera sobre robots del ámbito actual; el servidor lo vuelve a comprobar con sus permisos.
 */
class ResolveAlert(
    private val port: AlertCommandPort,
    private val currentScope: CurrentHomeScope,
) {

    /** @return `true` si la alerta cambió a resuelta; `false` si ya lo estaba. */
    suspend operator fun invoke(robotId: RobotId, alertId: AlertId): Boolean {
        currentScope.requireRobot(robotId)
        return port.resolve(alertId)
    }
}
