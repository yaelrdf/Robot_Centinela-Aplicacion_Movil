package com.robotcentinela.app.identity.application

import com.robotcentinela.app.identity.domain.HomeId
import com.robotcentinela.app.identity.domain.HomeMembership
import com.robotcentinela.app.identity.domain.HomeScope
import com.robotcentinela.app.identity.domain.MembershipStatus
import com.robotcentinela.app.identity.ports.HomeScopePort
import com.robotcentinela.app.shared.NotInScopeException

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Caso de uso: Consulta de domicilios donde el usuario posee una membresía activa autorizada.
 */
class ListAuthorizedHomes(private val port: HomeScopePort) {

    suspend operator fun invoke(): List<HomeMembership> =
        port.memberships().filter { it.status == MembershipStatus.Active }
}

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Caso de uso: Selección explícita del domicilio operativo y carga de sus robots asociados.
 */
class SelectHomeScope(
    private val port: HomeScopePort,
    private val currentScope: CurrentHomeScope,
) {

    /** @throws NotInScopeException si no existe una membresía activa para [homeId]. */
    suspend operator fun invoke(homeId: HomeId): HomeScope {
        val membership = port.memberships().firstOrNull {
            it.home.id == homeId && it.status == MembershipStatus.Active
        } ?: throw NotInScopeException("No hay membresía activa para el domicilio solicitado")

        val robots = port.robotsOf(homeId).filter { it.homeId == homeId }
        return HomeScope(membership.home, robots).also(currentScope::set)
    }
}
