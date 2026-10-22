package com.robotcentinela.app.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robotcentinela.app.identity.domain.HomeScope
import com.robotcentinela.app.identity.domain.SessionState
import com.robotcentinela.app.shell.AppContainer
import com.robotcentinela.app.shell.DevAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Qué parte de la app se muestra, según la sesión y el domicilio seleccionado (RF01, RF02).
 *
 * El inicio de sesión (HU01) se integra al final del proyecto, por indicación del equipo. Como RLS solo
 * entrega datos a usuarios autenticados, mientras tanto la app entra sola con una cuenta de prueba
 * ([DevAccount], solo debug). Los casos de uso de sesión (`SignIn`, `SignOut`, `RecoverAccess`) siguen
 * en `:core` y en `AppContainer` para la pantalla de login.
 */
sealed interface RootState {

    /** Recuperando la sesión guardada o entrando con la cuenta de prueba. */
    data object Connecting : RootState

    /** No hay sesión y no se pudo iniciar con la cuenta de prueba. */
    data class NeedsSession(val message: String) : RootState

    /** Falta elegir domicilio de forma explícita (RF02). */
    data object ChoosingHome : RootState

    /** Domicilio seleccionado. */
    data class Ready(val scope: HomeScope) : RootState
}

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Modelo de vista raíz que determina la pantalla activa según la sesión y el domicilio seleccionado.
 * Mientras no exista la pantalla de inicio de sesión, entra con la cuenta de prueba ([DevAccount]).
 */
class RootViewModel(
    private val container: AppContainer,
    private val devAccount: DevAccount? = DevAccount.fromBuildConfig(),
) : ViewModel() {

    private val choosingHome = MutableStateFlow(false)
    private val sessionError = MutableStateFlow<String?>(null)
    private var signingIn = false

    val state: StateFlow<RootState> = combine(
        container.authPort.sessionState,
        container.currentHomeScope.scope,
        choosingHome,
        sessionError,
    ) { session, scope, choosing, error ->
        when (session) {
            SessionState.Initializing -> RootState.Connecting
            is SessionState.NotAuthenticated -> error?.let { RootState.NeedsSession(it) } ?: RootState.Connecting
            // Con la renovación fallida (p. ej. sin red) se sigue mostrando la app: cada pantalla informa
            // su error y el SDK reintenta la renovación.
            is SessionState.Authenticated, SessionState.RefreshFailed ->
                if (scope == null || choosing) RootState.ChoosingHome else RootState.Ready(scope)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Connecting)

    init {
        viewModelScope.launch {
            container.authPort.sessionState.collect { session ->
                if (session is SessionState.NotAuthenticated) signInWithDevAccount()
            }
        }
    }

    /** Reintenta entrar con la cuenta de prueba. */
    fun retrySession() {
        sessionError.value = null
        signInWithDevAccount()
    }

    /** Vuelve al selector de domicilio (§12.5: salida ante errores de permisos). */
    fun changeHome() {
        choosingHome.value = true
    }

    fun homeChosen() {
        choosingHome.value = false
    }

    private fun signInWithDevAccount() {
        if (signingIn) return
        val account = devAccount ?: run {
            sessionError.value = MISSING_ACCOUNT
            return
        }
        signingIn = true
        viewModelScope.launch {
            try {
                container.signIn(account.email, account.password)
                sessionError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sessionError.value = SIGN_IN_FAILED
            } finally {
                signingIn = false
            }
        }
    }

    private companion object {
        const val MISSING_ACCOUNT =
            "Falta la cuenta de prueba. Supabase solo entrega datos a usuarios autenticados: agrega " +
                "centinela.devEmail y centinela.devPassword en local.properties y vuelve a compilar."
        const val SIGN_IN_FAILED =
            "No se pudo entrar con la cuenta de prueba. Revisa tu conexión y los valores de " +
                "centinela.devEmail y centinela.devPassword en local.properties."
    }
}
