package com.robotcentinela.app.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robotcentinela.app.identity.domain.HomeScope
import com.robotcentinela.app.shell.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Qué parte de la app se muestra, según el domicilio seleccionado (RF02).
 *
 * El inicio de sesión (HU01) se integra al final del proyecto, por indicación del equipo: mientras
 * tanto la app arranca directamente en la selección de domicilio. Los casos de uso de sesión
 * (`SignIn`, `SignOut`, `RecoverAccess`) siguen en `:core` y en `AppContainer`, listos para conectarse.
 */
sealed interface RootState {

    /** Falta elegir domicilio de forma explícita (RF02). */
    data object ChoosingHome : RootState

    /** Domicilio seleccionado. */
    data class Ready(val scope: HomeScope) : RootState
}

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Modelo de vista raíz que determina la pantalla inicial o activa según el domicilio seleccionado.
 */
class RootViewModel(container: AppContainer) : ViewModel() {

    private val choosingHome = MutableStateFlow(false)

    val state: StateFlow<RootState> = combine(container.currentHomeScope.scope, choosingHome) { scope, choosing ->
        if (scope == null || choosing) RootState.ChoosingHome else RootState.Ready(scope)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState.ChoosingHome)

    /** Vuelve al selector de domicilio (§12.5: salida ante errores de permisos). */
    fun changeHome() {
        choosingHome.value = true
    }

    fun homeChosen() {
        choosingHome.value = false
    }
}
