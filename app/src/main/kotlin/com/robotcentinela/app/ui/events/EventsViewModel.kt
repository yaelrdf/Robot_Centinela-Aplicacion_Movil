package com.robotcentinela.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robotcentinela.app.identity.domain.Robot
import com.robotcentinela.app.incidents.application.QueryAlertHistory
import com.robotcentinela.app.incidents.domain.Alert
import com.robotcentinela.app.incidents.domain.AlertState
import com.robotcentinela.app.shared.PageRequest
import com.robotcentinela.app.ui.model.DateRange
import com.robotcentinela.app.ui.model.RangePreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Severidades que ofrece el filtro de eventos e incidentes. */
internal enum class SeverityFilter(val raw: String, val label: String) {
    Critical("critical", "Crítica"),
    Warning("warning", "Advertencia"),
    Info("info", "Informativa"),
}

/** Estados disponibles para el filtrado del ciclo de vida de alertas. */
internal enum class StateFilter(val state: AlertState, val label: String) {
    Open(AlertState.Open, "Abiertas"),
    Acknowledged(AlertState.Acknowledged, "Atendidas"),
    Resolved(AlertState.Resolved, "Resueltas"),
}

/** Estado de la interfaz de usuario para la pantalla de eventos y alertas. */
data class EventsUiState(
    val range: DateRange,
    val severities: Set<String> = emptySet(),
    val states: Set<AlertState> = emptySet(),
    val page: Int = 0,
    val loading: Boolean = true,
    val alerts: List<Alert> = emptyList(),
    val hasNext: Boolean = false,
    val error: Throwable? = null,
)

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Modelo de vista para el historial de eventos e incidentes del robot.
 * Consume directamente el caso de uso QueryAlertHistory.
 */
class EventsViewModel(
    private val queryAlertHistory: QueryAlertHistory,
    clock: Clock,
    private val robot: Robot,
    zone: ZoneId,
) : ViewModel() {

    private val today = LocalDate.now(clock.withZone(zone))
    private val mutable = MutableStateFlow(EventsUiState(range = DateRange.of(RangePreset.Last30Days, today)))
    val state: StateFlow<EventsUiState> = mutable.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun setRange(range: DateRange) = refilter { it.copy(range = range) }

    fun toggleSeverity(raw: String) = refilter { s -> s.copy(severities = if (raw in s.severities) s.severities - raw else s.severities + raw) }

    fun toggleState(value: AlertState) = refilter { s -> s.copy(states = if (value in s.states) s.states - value else s.states + value) }

    fun nextPage() = goTo(mutable.value.page + 1)

    fun previousPage() = goTo((mutable.value.page - 1).coerceAtLeast(0))

    fun retry() = load()

    private fun refilter(change: (EventsUiState) -> EventsUiState) {
        mutable.update { change(it).copy(page = 0) }
        load()
    }

    private fun goTo(page: Int) {
        mutable.update { it.copy(page = page) }
        load()
    }

    /** Cada cambio de filtro cancela la consulta anterior (§12.5: no proyectar respuestas obsoletas). */
    private fun load() {
        job?.cancel()
        mutable.update { it.copy(loading = true, error = null) }
        val filters = mutable.value
        job = viewModelScope.launch {
            try {
                val result = queryAlertHistory(
                    robotId = robot.id,
                    page = PageRequest(PAGE_SIZE + 1, filters.page.toLong() * PAGE_SIZE),
                    from = filters.range.from,
                    toInclusive = filters.range.to,
                    severities = filters.severities,
                    states = filters.states,
                )
                mutable.update { it.copy(loading = false, alerts = result.take(PAGE_SIZE), hasNext = result.size > PAGE_SIZE) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, error = e) }
            }
        }
    }

    val todayDate: LocalDate get() = today

    private companion object {
        const val PAGE_SIZE = 20
    }
}
