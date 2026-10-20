package com.robotcentinela.app.homemapping.ports

import com.robotcentinela.app.homemapping.domain.FloorPlan
import com.robotcentinela.app.homemapping.domain.MapVersionId
import com.robotcentinela.app.identity.domain.RobotId

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Puerto de salida para la recuperación de la geometría y estaciones del plano cartográfico de un robot.
 */
interface FloorPlanPort {

    /**
     * Plano de la versión [mapVersionId] del robot. `null` si no hay archivo de mapa: la interfaz
     * muestra entonces la lista de estaciones (HU05 CDA 3).
     */
    suspend fun floorPlan(robotId: RobotId, mapVersionId: MapVersionId): FloorPlan?
}
