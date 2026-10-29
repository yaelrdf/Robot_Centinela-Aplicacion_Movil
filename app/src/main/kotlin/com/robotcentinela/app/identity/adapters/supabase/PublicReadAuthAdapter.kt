package com.robotcentinela.app.identity.adapters.supabase

import com.robotcentinela.app.identity.domain.SessionState
import com.robotcentinela.app.identity.domain.UserId
import com.robotcentinela.app.identity.ports.AuthPort
import com.robotcentinela.app.shared.AccessDeniedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Adaptador de [AuthPort] para la demostración de lectura pública (build `demo`).
 *
 * No hay inicio de sesión: la app consulta Supabase solo con la clave pública, como el rol `anon`. Este
 * adaptador reporta una sesión fija para que la interfaz no se quede esperando una persona
 * autenticada. No concede permisos: lo que se puede leer lo decide RLS en el servidor.
 *
 * Es temporal: depende de que backend siga permitiendo la lectura anónima (docs/pendientes.md, B19) y
 * se retira cuando exista el inicio de sesión (docs/decisions/ADR-006-demo-lectura-publica.md).
 */
class PublicReadAuthAdapter : AuthPort {

    override val sessionState: StateFlow<SessionState> =
        MutableStateFlow<SessionState>(SessionState.Authenticated(UserId(PUBLIC_READ_USER))).asStateFlow()

    override suspend fun signInWithEmail(email: String, password: String) =
        throw AccessDeniedException("La demostración no usa inicio de sesión")

    override suspend fun signOut() = Unit

    override suspend fun requestAccessRecovery(email: String) =
        throw AccessDeniedException("La demostración no usa inicio de sesión")

    private companion object {
        /** Identificador sin significado de negocio: no corresponde a ninguna persona. */
        const val PUBLIC_READ_USER = "lectura-publica"
    }
}
