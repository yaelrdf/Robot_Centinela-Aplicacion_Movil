package com.robotcentinela.app.missions.ports

import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.missions.domain.MissionId
import com.robotcentinela.app.missions.domain.MissionSummary
import com.robotcentinela.app.shared.PageRequest

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Puerto de salida para la consulta y obtención de información resumida sobre las misiones de patrullaje del robot.
 */
interface MissionQueryPort {

    /** Misiones del robot, de la más reciente a la más antigua por inicio. */
    suspend fun missions(robotId: RobotId, page: PageRequest): List<MissionSummary>

    /** Resumen de una misión, o `null` si no existe o no es visible. */
    suspend fun mission(robotId: RobotId, missionId: MissionId): MissionSummary?
}
