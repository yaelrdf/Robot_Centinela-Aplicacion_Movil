package com.robotcentinela.app.ui.homes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robotcentinela.app.identity.application.ListAuthorizedHomes
import com.robotcentinela.app.identity.application.SelectHomeScope
import com.robotcentinela.app.identity.domain.HomeId
import com.robotcentinela.app.identity.domain.HomeMembership
import com.robotcentinela.app.ui.model.OperationPresentation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado de la interfaz de usuario para la selección del domicilio. */
data class HomeSelectionState(
    val loading: Boolean = true,
    val homes: List<HomeMembership> = emptyList(),
    val error: String? = null,
    val selecting: HomeId? = null,
)

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Modelo de vista que gestiona la consulta y selección del domicilio activo.
 * Consume exclusivamente los casos de uso requeridos para mantener la fidelidad a la arquitectura hexagonal.
 */
class HomeSelectionViewModel(
    private val listAuthorizedHomes: ListAuthorizedHomes,
    private val selectHomeScope: SelectHomeScope,
) : ViewModel() {

    private val mutable = MutableStateFlow(HomeSelectionState())
    val state: StateFlow<HomeSelectionState> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.value = mutable.value.copy(loading = true, error = null)
        viewModelScope.launch {
            mutable.value = try {
                HomeSelectionState(loading = false, homes = listAuthorizedHomes())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HomeSelectionState(loading = false, error = OperationPresentation.errorMessage(e))
            }
        }
    }

    fun select(homeId: HomeId, onChosen: () -> Unit) {
        if (mutable.value.selecting != null) return
        mutable.value = mutable.value.copy(selecting = homeId, error = null)
        viewModelScope.launch {
            try {
                selectHomeScope(homeId)
                mutable.value = mutable.value.copy(selecting = null)
                onChosen()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(selecting = null, error = OperationPresentation.errorMessage(e))
            }
        }
    }
}
