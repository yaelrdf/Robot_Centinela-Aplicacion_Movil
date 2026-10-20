package com.robotcentinela.app.identity.ports

import com.robotcentinela.app.identity.domain.SessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Puerto de salida para las operaciones de autenticación y gestión de sesión de usuarios.
 */
interface AuthPort {

    /** Estado actual de la sesión, observable. */
    val sessionState: StateFlow<SessionState>

    /** Inicia sesión. Lanza [com.robotcentinela.app.shared.AccessDeniedException] si se rechaza. */
    suspend fun signInWithEmail(email: String, password: String)

    /** Cierra la sesión actual. */
    suspend fun signOut()

    /** Solicita al servidor el envío de instrucciones para recuperar el acceso. */
    suspend fun requestAccessRecovery(email: String)
}
