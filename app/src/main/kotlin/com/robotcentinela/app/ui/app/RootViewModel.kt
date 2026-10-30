package com.robotcentinela.app.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robotcentinela.app.identity.domain.HomeScope
import com.robotcentinela.app.identity.domain.SessionState
import com.robotcentinela.app.shell.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Qué parte de la app se muestra, según la sesión y el domicilio seleccionado (RF01, RF02).
 */
sealed interface RootState {

    /** Verificando si existe una sesión activa persistida en el dispositivo. */
    data object Connecting : RootState

    /** No hay sesión activa o se cerró la sesión: se muestra la pantalla de inicio de sesión (HU01). */
    data object NeedsLogin : RootState

    /** Falta elegir domicilio de forma explícita (RF02). */
    data object ChoosingHome : RootState

    /** Domicilio seleccionado y sesión activa. */
    data class Ready(val scope: HomeScope) : RootState
}

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Modelo de vista raíz que determina la pantalla activa según el estado de autenticación
 * y el ámbito de domicilio seleccionado.
 */
class RootViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val choosingHome = MutableStateFlow(false)

    val state: StateFlow<RootState> = combine(
        container.authPort.sessionState,
        container.currentHomeScope.scope,
        choosingHome,
    ) { session, scope, choosing ->
        when (session) {
            SessionState.Initializing -> RootState.Connecting
            is SessionState.NotAuthenticated -> RootState.NeedsLogin
            // Con la renovación fallida (p. ej. sin red) se sigue mostrando la app: cada pantalla informa
            // su error y el SDK reintenta la renovación.
            is SessionState.Authenticated, SessionState.RefreshFailed ->
                if (scope == null || choosing) RootState.ChoosingHome else RootState.Ready(scope)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Connecting)

    /** Vuelve al selector de domicilio (§12.5: salida ante errores de permisos). */
    fun changeHome() {
        choosingHome.value = true
    }

    fun homeChosen() {
        choosingHome.value = false
    }
}
