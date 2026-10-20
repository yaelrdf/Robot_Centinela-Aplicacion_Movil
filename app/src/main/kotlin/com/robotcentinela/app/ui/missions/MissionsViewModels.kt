package com.robotcentinela.app.ui.missions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelKey
import com.robotcentinela.app.environmentalmonitoring.domain.ReadingValue
import com.robotcentinela.app.identity.domain.Robot
import com.robotcentinela.app.missions.domain.MissionId
import com.robotcentinela.app.missions.domain.MissionSummary
import com.robotcentinela.app.shared.PageRequest
import com.robotcentinela.app.shell.AppContainer
import com.robotcentinela.app.telemetry.domain.FrameRecord
import com.robotcentinela.app.ui.main.FloorPlanState
import com.robotcentinela.app.ui.model.TrajectoryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

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

data class MissionDetailUiState(
    val loading: Boolean = true,
    val mission: MissionSummary? = null,
    val notFound: Boolean = false,
    val trajectory: List<TrajectoryPoint> = emptyList(),
    val trajectoryTruncated: Boolean = false,
    val floorPlan: FloorPlanState = FloorPlanState.Loading,
    val error: Throwable? = null,
)

/**
 * Detalle de una misión: resumen, estaciones y trayectoria dibujada sobre la versión EXACTA de mapa de
 * la misión (HU05 CDA 2), nunca sobre la vigente.
 */
class MissionDetailViewModel(
    private val container: AppContainer,
    private val robot: Robot,
    private val zone: ZoneId,
    private val missionId: String,
) : ViewModel() {

    private val mutable = MutableStateFlow(MissionDetailUiState())
    val state: StateFlow<MissionDetailUiState> = mutable.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        mutable.value = MissionDetailUiState()
        viewModelScope.launch {
            try {
                val id = MissionId(UUID.fromString(missionId))
                val mission = container.getMissionSummary?.invoke(robot.id, id)
                if (mission == null) {
                    mutable.value = MissionDetailUiState(loading = false, notFound = true)
                    return@launch
                }
                mutable.update { it.copy(loading = false, mission = mission) }

                val from = mission.startedAt.atZone(zone).toLocalDate()
                val to = (mission.endedAt ?: container.clock.instant()).atZone(zone).toLocalDate()
                val frames = container.queryTelemetryHistory(robot.id, from, to, PageRequest(MAX_FRAMES), id)
                    .filterIsInstance<FrameRecord.Interpreted>()
                    .map { it.frame }
                val points = frames.mapNotNull { frame ->
                    val pose = frame.pose ?: return@mapNotNull null
                    val signal = frame.readings.firstOrNull { it.channelKey == SIGNAL }?.value as? ReadingValue.Numeric
                    TrajectoryPoint(pose.xM, pose.yM, pose.sigmaPositionM, frame.robotState?.raw == "sampling", signal?.value)
                }
                mutable.update { it.copy(trajectory = points, trajectoryTruncated = frames.size >= MAX_FRAMES) }

                val plan = container.queryFloorPlan
                val version = mission.mapVersionId
                val planState = when {
                    plan == null -> FloorPlanState.ContractPending
                    version == null -> FloorPlanState.NoMapVersion
                    else -> plan(robot.id, version)?.let { FloorPlanState.Available(it) } ?: FloorPlanState.NoFile
                }
                mutable.update { it.copy(floorPlan = planState) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                mutable.value = MissionDetailUiState(loading = false, notFound = true)
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, error = e, floorPlan = FloorPlanState.Error("sin conexión")) }
            }
        }
    }

    private companion object {
        val SIGNAL = ChannelKey("mq6_raw")
        const val MAX_FRAMES = 1_000
    }
}
