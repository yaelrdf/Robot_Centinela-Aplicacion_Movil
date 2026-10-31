package com.robotcentinela.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robotcentinela.app.environmentalmonitoring.application.ChannelStatus
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelCapability
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelKey
import com.robotcentinela.app.environmentalmonitoring.domain.ReadingInterpretation
import com.robotcentinela.app.identity.domain.HomeScope
import com.robotcentinela.app.identity.domain.Robot
import com.robotcentinela.app.incidents.domain.Alert
import com.robotcentinela.app.incidents.domain.AlertId
import com.robotcentinela.app.incidents.domain.AlertState
import com.robotcentinela.app.missions.domain.MissionId
import com.robotcentinela.app.missions.domain.MissionSummary
import com.robotcentinela.app.shared.PageRequest
import com.robotcentinela.app.shell.AppContainer
import com.robotcentinela.app.telemetry.application.SyncStatus
import com.robotcentinela.app.telemetry.application.TelemetrySyncState
import com.robotcentinela.app.telemetry.application.TelemetrySynchronizer
import com.robotcentinela.app.ui.model.BatteryModel
import com.robotcentinela.app.ui.model.ChannelPresentation
import com.robotcentinela.app.ui.model.ConnectivityItem
import com.robotcentinela.app.ui.model.DateRange
import com.robotcentinela.app.ui.model.Formats
import com.robotcentinela.app.ui.model.MetricCardModel
import com.robotcentinela.app.ui.model.OperationPresentation
import com.robotcentinela.app.ui.model.RangePreset
import com.robotcentinela.app.ui.model.StatusLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Panel "Eventos y avisos" del resumen. */
data class AlertsPanelState(
    val loading: Boolean = true,
    val alerts: List<Alert> = emptyList(),
    val error: String? = null,
)

/** Estado en vivo que comparten Resumen y el aviso persistente de alertas. */
data class LiveUiState(
    val robot: Robot?,
    val robots: List<Robot>,
    val zone: ZoneId,
    val today: LocalDate,
    val initialLoading: Boolean = true,
    val refreshing: Boolean = false,
    val syncLabel: StatusLabel? = null,
    /** Aviso de captura previa sin conexión, con su fecha (§12.5). */
    val offlineNotice: String? = null,
    val historyLimited: Boolean = false,
    val rejectedFrames: Long = 0,
    val primaryCards: List<MetricCardModel> = emptyList(),
    val otherCards: List<MetricCardModel> = emptyList(),
    val channelStatuses: Map<ChannelKey, ChannelStatus> = emptyMap(),
    val battery: BatteryModel = BatteryModel(null, "Sin dato de batería", null),
    val connectivity: List<ConnectivityItem> = emptyList(),
    val robotStatus: StatusLabel? = null,
    val lastUpdate: String? = null,
    val missionId: MissionId? = null,
    val mission: MissionSummary? = null,
    val missionsSupported: Boolean = false,
    val alertRange: DateRange,
    val alerts: AlertsPanelState = AlertsPanelState(),
    val bannerAlert: Alert? = null,
)

private data class LiveCore(
    val sync: TelemetrySyncState,
    val capabilities: List<ChannelCapability>,
    val now: Instant,
)

private data class Extras(
    val mission: MissionSummary?,
)

private data class AlertExtras(
    val range: DateRange,
    val panel: AlertsPanelState,
    val open: List<Alert>,
    val dismissed: Set<AlertId>,
)

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Modelo de vista principal que orquesta la sesión en vivo y el monitoreo del robot centinela.
 *
 * [ESTADO DE FLUJOS OPERATIVOS]:
 * - Telemetría en tiempo real: Conectada al backend (Supabase Realtime y deltas).
 * - Historial de incidentes y alertas: Conectado a la tabla de alertas del backend.
 * - Mapa asistido 2D: no se incluye en esta versión (implementación futura, equipo de IoT).
 * - [PENDIENTE DE CONTRATO DEL ROBOT]: Missions (misiones de patrullaje),
 *   cuyos puertos aún no cuentan con tablas ni endpoints desplegados en el backend/robot.
 */
class MainViewModel(private val container: AppContainer, private val scope: HomeScope) : ViewModel() {

    private val zone: ZoneId = scope.home.timezone
    private fun today(): LocalDate = LocalDate.now(container.clock.withZone(zone))

    private val selectedRobot = MutableStateFlow(scope.robots.firstOrNull())
    private val syncState = MutableStateFlow(TelemetrySyncState())
    private val capabilities = MutableStateFlow<List<ChannelCapability>>(emptyList())
    private val mission = MutableStateFlow<MissionSummary?>(null)
    private val alertRange = MutableStateFlow(DateRange.of(RangePreset.ThisMonth, today()))
    private val alertsPanel = MutableStateFlow(AlertsPanelState())
    private val openAlerts = MutableStateFlow<List<Alert>>(emptyList())
    private val dismissedAlerts = MutableStateFlow<Set<AlertId>>(emptySet())

    private var synchronizer: TelemetrySynchronizer? = null
    private var sessionJobs: List<Job> = emptyList()
    private var started = false

    private val ticker = flow {
        while (true) {
            emit(container.clock.instant())
            delay(1_000)
        }
    }

    private val core = combine(syncState, capabilities, ticker) { sync, caps, now -> LiveCore(sync, caps, now) }
    private val extras = mission.map { Extras(it) }
    private val alertExtras = combine(alertRange, alertsPanel, openAlerts, dismissedAlerts) { range, panel, open, dismissed ->
        AlertExtras(range, panel, open, dismissed)
    }

    val state: StateFlow<LiveUiState> = combine(core, extras, alertExtras, selectedRobot) { c, e, a, robot ->
        buildState(robot, c, e, a)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LiveUiState(robot = selectedRobot.value, robots = scope.robots, zone = zone, today = today(), alertRange = alertRange.value),
    )

    // --- Ciclo de vida -------------------------------------------------------------------------

    fun onStart() {
        if (started) return
        started = true
        val robot = selectedRobot.value ?: return
        startSession(robot)
    }

    fun onStop() {
        if (!started) return
        started = false
        stopSession()
    }

    fun selectRobot(robot: Robot) {
        if (robot.id == selectedRobot.value?.id) return
        stopSession()
        synchronizer = null
        syncState.value = TelemetrySyncState()
        mission.value = null
        selectedRobot.value = robot
        if (started) startSession(robot)
    }

    override fun onCleared() {
        stopSession()
    }

    private fun startSession(robot: Robot) {
        val sync = synchronizer ?: runCatching { container.telemetrySynchronizer(robot.id) }.getOrNull()?.also { synchronizer = it }
        if (sync == null) {
            syncState.value = TelemetrySyncState(status = SyncStatus.Error("El robot no pertenece al domicilio seleccionado"))
            return
        }
        val jobs = mutableListOf<Job>()
        // Se suscribe al estado antes de arrancar para no perder el snapshot inicial.
        jobs += viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            sync.state.collect { syncState.value = it }
        }
        jobs += viewModelScope.launch {
            syncState.map { it.projection.latestFrame?.missionId }.distinctUntilChanged().collectLatest { missionId ->
                while (true) {
                    refreshMission(robot, missionId)
                    delay(10_000)
                }
            }
        }
        jobs += viewModelScope.launch {
            alertRange.collectLatest {
                while (true) {
                    refreshAlerts(robot)
                    delay(30_000)
                }
            }
        }
        if (capabilities.value.isEmpty()) {
            jobs += viewModelScope.launch {
                container.queryChannelCapabilities?.let { query ->
                    capabilities.value = runCatching { query(robot.id) }.getOrDefault(emptyList())
                }
            }
        }
        sessionJobs = jobs
        sync.start(viewModelScope)
    }

    private fun stopSession() {
        sessionJobs.forEach { it.cancel() }
        sessionJobs = emptyList()
        val sync = synchronizer ?: return
        viewModelScope.launch { sync.stop() }
    }

    // --- Acciones de la interfaz -----------------------------------------------------------------

    fun setAlertRange(range: DateRange) {
        alertRange.value = range
    }

    fun dismissBanner(id: AlertId) = dismissedAlerts.update { it + id }

    fun retryAlerts() {
        val robot = selectedRobot.value ?: return
        viewModelScope.launch { refreshAlerts(robot) }
    }

    // --- Carga de datos --------------------------------------------------------------------------

    private suspend fun refreshMission(robot: Robot, missionId: MissionId?) {
        val query = container.getMissionSummary ?: return
        if (missionId == null) {
            mission.value = null
            return
        }
        try {
            mission.value = query(robot.id, missionId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Se conserva el último resumen conocido; la barra de conectividad ya muestra el fallo.
        }
    }

    private suspend fun refreshAlerts(robot: Robot) {
        val range = alertRange.value
        alertsPanel.update { it.copy(loading = it.alerts.isEmpty(), error = null) }
        try {
            val recent = container.queryAlertHistory(robot.id, PageRequest(5), range.from, range.to)
            val open = container.queryAlertHistory(robot.id, PageRequest(20), states = setOf(AlertState.Open))
            alertsPanel.value = AlertsPanelState(loading = false, alerts = recent)
            openAlerts.value = open
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            alertsPanel.update { it.copy(loading = false, error = OperationPresentation.errorMessage(e)) }
        }
    }

    // --- Composición del estado ------------------------------------------------------------------

    private fun buildState(robot: Robot?, c: LiveCore, e: Extras, a: AlertExtras): LiveUiState {
        val projection = c.sync.projection
        val statuses = container.describeChannelStatus(projection, c.capabilities).associateBy { it.channelKey }
        val primary = ChannelPresentation.PRIMARY.map { key ->
            statuses[key] ?: ChannelStatus(key, ReadingInterpretation.NoData, null, null, c.capabilities.firstOrNull { it.channelKey == key })
        }
        val others = statuses.values.filter { it.channelKey !in ChannelPresentation.PRIMARY }
        val health = container.evaluateRobotHealth(projection)
        val connectivity = OperationPresentation.connectivity(health, c.sync.status, zone)
        val latest = projection.latestFrame
        val status = c.sync.status
        val offline = status is SyncStatus.Offline || status is SyncStatus.Error

        return LiveUiState(
            robot = robot,
            robots = scope.robots,
            zone = zone,
            today = today(),
            initialLoading = latest == null && (status == SyncStatus.Idle || status == SyncStatus.Connecting),
            refreshing = status == SyncStatus.Reconciling,
            syncLabel = OperationPresentation.syncStatus(status),
            offlineNotice = if (offline && latest != null) {
                "Sin conexión: se muestra la captura previa del " +
                    Formats.dateTime(latest.capturedAt ?: latest.receivedAt, zone) + "."
            } else if (offline) {
                "Sin conexión y sin capturas previas para mostrar."
            } else {
                null
            },
            historyLimited = c.sync.historyLimited,
            rejectedFrames = c.sync.rejectedFrames,
            primaryCards = primary.map { ChannelPresentation.card(it, zone) },
            otherCards = others.map { ChannelPresentation.card(it, zone) },
            channelStatuses = statuses,
            battery = OperationPresentation.battery(health.battery),
            connectivity = connectivity,
            robotStatus = connectivity.firstOrNull()?.status,
            lastUpdate = health.lastFrameFreshness?.age?.let(Formats::age),
            missionId = latest?.missionId,
            mission = e.mission,
            missionsSupported = container.getMissionSummary != null,
            alertRange = a.range,
            alerts = a.panel,
            bannerAlert = a.open.firstOrNull { OperationPresentation.isHighSeverity(it) && it.id !in a.dismissed },
        )
    }
}
