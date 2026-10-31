package com.robotcentinela.app.ui.missions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robotcentinela.app.identity.domain.Robot
import com.robotcentinela.app.missions.domain.MissionSummary
import com.robotcentinela.app.shared.PageRequest
import com.robotcentinela.app.shell.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MissionsUiState(
    val supported: Boolean,
    val loading: Boolean = true,
    val missions: List<MissionSummary> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val error: Throwable? = null,
)

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Modelo de vista para la consulta y paginación de misiones del robot.
 *
 * [PENDIENTE DE CONTRATO REAL]:
 * El backend y el robot aún no tienen publicado el contrato de misiones (listMissions == null).
 * La interfaz muestra el estado explícito de "soporte pendiente" en lugar de simular ejecuciones.
 */
class MissionsViewModel(private val container: AppContainer, private val robot: Robot) : ViewModel() {

    private val mutable = MutableStateFlow(MissionsUiState(supported = container.listMissions != null))
    val state: StateFlow<MissionsUiState> = mutable.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun next() = goTo(mutable.value.page + 1)

    fun previous() = goTo((mutable.value.page - 1).coerceAtLeast(0))

    fun retry() = load()

    private fun goTo(page: Int) {
        mutable.update { it.copy(page = page) }
        load()
    }

    private fun load() {
        val query = container.listMissions ?: run {
            mutable.update { it.copy(loading = false) }
            return
        }
        job?.cancel()
        mutable.update { it.copy(loading = true, error = null) }
        val page = mutable.value.page
        job = viewModelScope.launch {
            try {
                val result = query(robot.id, PageRequest(PAGE_SIZE + 1, page.toLong() * PAGE_SIZE))
                mutable.update { it.copy(loading = false, missions = result.take(PAGE_SIZE), hasNext = result.size > PAGE_SIZE) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, error = e) }
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 15
    }
}
