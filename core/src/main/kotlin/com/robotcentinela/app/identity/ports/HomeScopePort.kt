package com.robotcentinela.app.identity.ports

import com.robotcentinela.app.identity.domain.HomeId
import com.robotcentinela.app.identity.domain.HomeMembership
import com.robotcentinela.app.identity.domain.Robot

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Puerto de salida para la consulta de membresías de domicilios y robots asociados al usuario autenticado.
 */
interface HomeScopePort {

    /** Membresías de la persona autenticada. */
    suspend fun memberships(): List<HomeMembership>

    /** Robots visibles de un domicilio. */
    suspend fun robotsOf(homeId: HomeId): List<Robot>
}
