package com.robotcentinela.app.identity.adapters.supabase

import com.robotcentinela.app.identity.domain.SessionState
import com.robotcentinela.app.shared.AccessDeniedException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** La demostración de lectura pública no tiene sesión real: no inicia sesión ni la recupera. */
class PublicReadAuthAdapterTest {

    private val auth = PublicReadAuthAdapter()

    @Test
    fun `reporta una sesion fija para que la interfaz no espere un inicio de sesion`() {
        assertIs<SessionState.Authenticated>(auth.sessionState.value)
    }

    @Test
    fun `no permite iniciar sesion ni recuperar acceso`() = runTest {
        assertFailsWith<AccessDeniedException> { auth.signInWithEmail("a@b.c", "x") }
        assertFailsWith<AccessDeniedException> { auth.requestAccessRecovery("a@b.c") }
    }

    @Test
    fun `cerrar sesion no tiene efecto`() = runTest {
        auth.signOut()
        assertIs<SessionState.Authenticated>(auth.sessionState.value)
    }
}
