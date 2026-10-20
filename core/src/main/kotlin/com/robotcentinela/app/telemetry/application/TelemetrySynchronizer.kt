package com.robotcentinela.app.telemetry.application

import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.telemetry.domain.FrameId
import com.robotcentinela.app.telemetry.domain.FrameRecord
import com.robotcentinela.app.telemetry.domain.StreamVersion
import com.robotcentinela.app.telemetry.domain.TelemetryFrame
import com.robotcentinela.app.telemetry.ports.TelemetryChangeNotifier
import com.robotcentinela.app.telemetry.ports.TelemetryNotice
import com.robotcentinela.app.telemetry.ports.TelemetryQueryPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Caso de uso: Sincronización en tiempo real del flujo de telemetría de un robot centinela.
 *
 * Coordina la obtención de instantáneas consistentes, la suscripción a eventos en tiempo real
 * y la reconciliación continua de deltas de telemetría mediante los puertos de consulta y notificación.
 */
class TelemetrySynchronizer(
    val robotId: RobotId,
    private val query: TelemetryQueryPort,
    private val notifier: TelemetryChangeNotifier,
    private val config: SyncConfig = SyncConfig(),
) {

    private val mutableState = MutableStateFlow(TelemetrySyncState())

    /** Estado observable: proyección del último dato, cursor y estado de conexión. */
    val state: StateFlow<TelemetrySyncState> = mutableState.asStateFlow()

    private val newFrames = MutableSharedFlow<TelemetryFrame>(extraBufferCapacity = 64)

    /**
     * Frames nuevos aplicados, en orden de `stream_version` y sin duplicados. Sirven para gráficas
     * y trayectorias.
     */
    val appliedFrames: SharedFlow<TelemetryFrame> = newFrames.asSharedFlow()

    private val recentFrameIds = RecentFrameIds(config.dedupeWindow)
    private var job: Job? = null

    /** Inicia (o reanuda) la sincronización en [scope]. No hace nada si ya está en curso. */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch { run() }
    }

    /**
     * Detiene la sincronización, por ejemplo al pasar la app a segundo plano. Al reanudar con
     * [start] se reconcilia desde la última versión aplicada (RB28).
     */
    suspend fun stop() {
        job?.cancelAndJoin()
        job = null
        mutableState.update { it.copy(status = SyncStatus.Stopped) }
    }

    private suspend fun run(): Unit = coroutineScope {
        setStatus(if (state.value.lastAppliedVersion == null) SyncStatus.Connecting else SyncStatus.Reconciling)

        val notices = Channel<TelemetryNotice>(Channel.UNLIMITED)
        launch {
            while (true) {
                notifier.notices(robotId)
                    .catch { cause -> emit(TelemetryNotice.Interrupted(cause.message ?: "Suscripción interrumpida")) }
                    .collect { notices.send(it) }
                // Si el flujo de avisos termina, se reintenta la suscripción tras el intervalo.
                notices.send(TelemetryNotice.Interrupted("Suscripción finalizada"))
                delay(config.reconciliationInterval)
            }
        }

        var subscribed = false
        while (true) {
            // Sin avisos durante el intervalo: reconciliación periódica (§7.4).
            val first = withTimeoutOrNull(config.reconciliationInterval) { notices.receive() }
            var mustSync = first == null && subscribed

            // Se agrupan los avisos pendientes: varios avisos seguidos producen una sola consulta.
            var notice: TelemetryNotice? = first
            while (notice != null) {
                when (notice) {
                    TelemetryNotice.Subscribed -> {
                        subscribed = true
                        mustSync = true
                    }
                    TelemetryNotice.FrameCommitted -> if (subscribed) mustSync = true
                    is TelemetryNotice.Interrupted -> {
                        subscribed = false
                        mustSync = false
                        setStatus(SyncStatus.Offline(notice.reason))
                    }
                }
                notice = notices.tryReceive().getOrNull()
            }

            if (mustSync) synchronize()
        }
    }

    private suspend fun synchronize() {
        try {
            val status = state.value.status
            if (status != SyncStatus.Live && status != SyncStatus.Connecting) {
                setStatus(SyncStatus.Reconciling)
            }
            if (state.value.lastAppliedVersion == null) {
                applySnapshot(historyLimited = false)
            }
            catchUp()
            setStatus(SyncStatus.Live)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setStatus(SyncStatus.Error(e.message ?: e::class.simpleName ?: "Error de sincronización"))
        }
    }

    /** Pide deltas desde la última versión aplicada hasta que no queden más. */
    private suspend fun catchUp() {
        var snapshotTaken = false
        while (true) {
            val after = state.value.lastAppliedVersion ?: StreamVersion.NONE
            val page = query.framesAfter(robotId, after, config.deltaPageSize).sortedBy { it.streamVersion }
            val first = page.firstOrNull() ?: return

            if (first.streamVersion > after.next() && !snapshotTaken) {
                // Hueco en el cursor: los frames intermedios ya no existen (retención).
                applySnapshot(historyLimited = true)
                snapshotTaken = true
                continue
            }

            apply(page)
            val advanced = state.value.lastAppliedVersion != after
            if (page.size < config.deltaPageSize || !advanced) return
        }
    }

    private suspend fun applySnapshot(historyLimited: Boolean) {
        val snapshot = query.snapshot(robotId)
        apply(snapshot.records)
        val watermark = snapshot.watermark ?: snapshot.records.maxOfOrNull { it.streamVersion }
        mutableState.update { current ->
            val base = current.lastAppliedVersion ?: StreamVersion.NONE
            current.copy(
                watermark = watermark ?: current.watermark,
                lastAppliedVersion = if (watermark != null && watermark > base) watermark else base,
                historyLimited = current.historyLimited || historyLimited,
            )
        }
    }

    /** Aplica los registros en orden, sin retroceder y sin duplicar. */
    private suspend fun apply(records: List<FrameRecord>) {
        var current = state.value
        val applied = mutableListOf<TelemetryFrame>()

        for (record in records.sortedBy { it.streamVersion }) {
            val last = current.lastAppliedVersion
            if (last != null && record.streamVersion <= last) continue

            current = when (record) {
                is FrameRecord.Interpreted -> if (recentFrameIds.add(record.frame.id)) {
                    applied += record.frame
                    current.copy(projection = current.projection.apply(record.frame))
                } else {
                    current
                }
                is FrameRecord.Rejected -> current.copy(rejectedFrames = current.rejectedFrames + 1)
            }
            current = current.copy(lastAppliedVersion = record.streamVersion)
        }

        mutableState.value = current
        applied.forEach { newFrames.emit(it) }
    }

    private fun setStatus(status: SyncStatus) {
        mutableState.update { it.copy(status = status) }
    }
}

/** Conjunto acotado de `frame_id` recientes. Olvida primero los más antiguos. */
internal class RecentFrameIds(private val capacity: Int) {

    private val ids = LinkedHashSet<FrameId>()

    /** Devuelve `false` si el identificador ya estaba registrado. */
    fun add(id: FrameId): Boolean {
        if (!ids.add(id)) return false
        if (ids.size > capacity) {
            val iterator = ids.iterator()
            iterator.next()
            iterator.remove()
        }
        return true
    }
}
