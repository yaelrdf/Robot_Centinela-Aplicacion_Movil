package com.robotcentinela.app.telemetry.application

import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.missions.domain.MissionId
import com.robotcentinela.app.shared.PageRequest
import com.robotcentinela.app.shared.UtcInterval
import com.robotcentinela.app.testing.TestFrames
import com.robotcentinela.app.testing.TestFrames.frame
import com.robotcentinela.app.telemetry.domain.FrameRecord
import com.robotcentinela.app.telemetry.domain.StreamVersion
import com.robotcentinela.app.telemetry.domain.TelemetryFrame
import com.robotcentinela.app.telemetry.ports.TelemetryChangeNotifier
import com.robotcentinela.app.telemetry.ports.TelemetryNotice
import com.robotcentinela.app.telemetry.ports.TelemetryQueryPort
import com.robotcentinela.app.telemetry.ports.TelemetrySnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** HU03, RF07, RF08, RB19, RB26 a RB28 y casos T02, T07 y T08 del expediente. */
class TelemetrySynchronizerTest {

    /** Flujo persistido de un robot, en memoria. */
    private class FakeTelemetryQuery : TelemetryQueryPort {
        val stream = mutableListOf<FrameRecord>()
        var snapshotCalls = 0
        var failure: Exception? = null
        var stalePageOverride: List<FrameRecord>? = null
        var latency = 0.seconds

        fun persist(vararg versions: Long) {
            versions.forEach { stream += FrameRecord.Interpreted(frame(it)) }
        }

        override suspend fun snapshot(robotId: RobotId): TelemetrySnapshot {
            failure?.let { throw it }
            snapshotCalls++
            val latest = stream.maxByOrNull { it.streamVersion }
            return TelemetrySnapshot(listOfNotNull(latest), latest?.streamVersion)
        }

        override suspend fun framesAfter(robotId: RobotId, after: StreamVersion, limit: Int): List<FrameRecord> {
            failure?.let { throw it }
            if (latency > 0.seconds) delay(latency)
            stalePageOverride?.let { return it }
            return stream.filter { it.streamVersion > after }.sortedBy { it.streamVersion }.take(limit)
        }

        override suspend fun framesBetween(
            robotId: RobotId,
            interval: UtcInterval,
            missionId: MissionId?,
            page: PageRequest,
        ): List<FrameRecord> = emptyList()
    }

    private class FakeNotifier : TelemetryChangeNotifier {
        val notices = MutableSharedFlow<TelemetryNotice>(extraBufferCapacity = 16)
        override fun notices(robotId: RobotId): Flow<TelemetryNotice> = notices
    }

    private val query = FakeTelemetryQuery()
    private val notifier = FakeNotifier()

    private fun TestScope.startedSynchronizer(config: SyncConfig = SyncConfig()): TelemetrySynchronizer {
        val synchronizer = TelemetrySynchronizer(TestFrames.ROBOT_ID, query, notifier, config)
        synchronizer.start(backgroundScope)
        runCurrent()
        return synchronizer
    }

    private fun TestScope.notice(notice: TelemetryNotice) {
        notifier.notices.tryEmit(notice)
        runCurrent()
    }

    @Test
    fun `espera la suscripcion, aplica snapshot y queda en vivo`() = runTest {
        query.persist(1, 2, 3)
        val sync = startedSynchronizer()
        assertEquals(SyncStatus.Connecting, sync.state.value.status)
        assertEquals(0, query.snapshotCalls, "No se pide snapshot antes de confirmar la suscripción")

        notice(TelemetryNotice.Subscribed)

        val state = sync.state.value
        assertEquals(SyncStatus.Live, state.status)
        assertEquals(StreamVersion(3), state.lastAppliedVersion)
        assertEquals(StreamVersion(3), state.watermark)
        assertEquals(StreamVersion(3), state.projection.latestFrame?.streamVersion)
    }

    @Test
    fun `un aviso dispara la consulta de deltas y aplica en orden`() = runTest {
        query.persist(1)
        val sync = startedSynchronizer()
        notice(TelemetryNotice.Subscribed)
        val applied = mutableListOf<TelemetryFrame>()
        backgroundScope.launch { sync.appliedFrames.collect { applied += it } }
        runCurrent()

        query.persist(2, 3, 4)
        notice(TelemetryNotice.FrameCommitted)

        assertEquals(listOf(2L, 3L, 4L), applied.map { it.streamVersion.value })
        assertEquals(StreamVersion(4), sync.state.value.lastAppliedVersion)
    }

    @Test
    fun `los deltas se paginan hasta alcanzar el ultimo frame`() = runTest {
        query.persist(1)
        val sync = startedSynchronizer(SyncConfig(deltaPageSize = 2))
        notice(TelemetryNotice.Subscribed)

        query.persist(2, 3, 4, 5, 6)
        notice(TelemetryNotice.FrameCommitted)

        assertEquals(StreamVersion(6), sync.state.value.lastAppliedVersion)
    }

    @Test
    fun `una rafaga de avisos que desborda el buffer no pierde frames`() = runTest {
        query.persist(1)
        val sync = startedSynchronizer()
        notice(TelemetryNotice.Subscribed)

        // Mientras la consulta está en curso llegan muchos más avisos que la capacidad del buffer (64).
        query.latency = 5.seconds
        query.persist(2)
        notice(TelemetryNotice.FrameCommitted)
        repeat(200) {
            query.persist(3L + it)
            notice(TelemetryNotice.FrameCommitted)
        }
        advanceTimeBy(30.seconds)
        runCurrent()

        // Los avisos descartados eran redundantes: el cursor por stream_version recupera todo.
        assertEquals(StreamVersion(202), sync.state.value.lastAppliedVersion)
        assertEquals(SyncStatus.Live, sync.state.value.status)
    }

    @Test
    fun `entregas duplicadas se aplican una sola vez`() = runTest {
        query.persist(1)
        val sync = startedSynchronizer()
        notice(TelemetryNotice.Subscribed)
        val applied = mutableListOf<TelemetryFrame>()
        backgroundScope.launch { sync.appliedFrames.collect { applied += it } }
        runCurrent()

        val second = FrameRecord.Interpreted(frame(2))
        query.stalePageOverride = listOf(second, second)
        notice(TelemetryNotice.FrameCommitted)
        notice(TelemetryNotice.FrameCommitted)

        assertEquals(1, applied.size)
        assertEquals(StreamVersion(2), sync.state.value.lastAppliedVersion)
    }

    @Test
    fun `una respuesta con versiones ya aplicadas no hace retroceder el estado`() = runTest {
        query.persist(1, 2, 3, 4, 5)
        val sync = startedSynchronizer()
        notice(TelemetryNotice.Subscribed)
        val before = sync.state.value

        query.stalePageOverride = query.stream.filter { it.streamVersion <= StreamVersion(4) }
        notice(TelemetryNotice.FrameCommitted)

        assertEquals(before.lastAppliedVersion, sync.state.value.lastAppliedVersion)
        assertEquals(before.projection, sync.state.value.projection)
    }

    @Test
    fun `tras una interrupcion reconcilia desde la ultima version sin nuevo snapshot`() = runTest {
        query.persist(1, 2)
        val sync = startedSynchronizer()
        notice(TelemetryNotice.Subscribed)

        notice(TelemetryNotice.Interrupted("sin red"))
        assertIs<SyncStatus.Offline>(sync.state.value.status)

        query.persist(3, 4, 5)
        notice(TelemetryNotice.Subscribed)

        assertEquals(SyncStatus.Live, sync.state.value.status)
        assertEquals(StreamVersion(5), sync.state.value.lastAppliedVersion)
        assertEquals(1, query.snapshotCalls)
        assertFalse(sync.state.value.historyLimited)
    }

    @Test
    fun `un cursor fuera de la retencion aplica snapshot y marca historia limitada`() = runTest {
        query.persist(1, 2, 3)
        val sync = startedSynchronizer()
        notice(TelemetryNotice.Subscribed)

        // La retención eliminó los frames 1 a 10. Solo quedan el 11 y el 12.
        query.stream.clear()
        query.persist(11, 12)
        notice(TelemetryNotice.FrameCommitted)

        val state = sync.state.value
        assertEquals(StreamVersion(12), state.lastAppliedVersion)
        assertTrue(state.historyLimited)
        assertEquals(2, query.snapshotCalls)
    }

    @Test
    fun `la reconciliacion periodica recupera frames sin aviso`() = runTest {
        query.persist(1)
        val sync = startedSynchronizer()
        notice(TelemetryNotice.Subscribed)

        query.persist(2)
        advanceTimeBy(30.seconds + 1.seconds)
        runCurrent()

        assertEquals(StreamVersion(2), sync.state.value.lastAppliedVersion)
    }

    @Test
    fun `los frames rechazados avanzan el cursor y se cuentan`() = runTest {
        query.persist(1)
        val sync = startedSynchronizer()
        notice(TelemetryNotice.Subscribed)

        // Después del snapshot inicial llegan un frame rechazado (versión 2) y uno válido (versión 3).
        query.stream += FrameRecord.Rejected(StreamVersion(2), null, "schema_version no soportada")
        query.persist(3)
        notice(TelemetryNotice.FrameCommitted)

        assertEquals(StreamVersion(3), sync.state.value.lastAppliedVersion)
        assertEquals(1, sync.state.value.rejectedFrames)
    }

    @Test
    fun `un fallo de consulta queda en error y el siguiente aviso recupera`() = runTest {
        query.persist(1)
        val sync = startedSynchronizer()
        notice(TelemetryNotice.Subscribed)

        query.failure = IllegalStateException("503")
        query.persist(2)
        notice(TelemetryNotice.FrameCommitted)
        assertIs<SyncStatus.Error>(sync.state.value.status)

        query.failure = null
        notice(TelemetryNotice.FrameCommitted)
        assertEquals(SyncStatus.Live, sync.state.value.status)
        assertEquals(StreamVersion(2), sync.state.value.lastAppliedVersion)
    }

    @Test
    fun `detenida no se describe como en vivo y al reanudar reconcilia`() = runTest {
        query.persist(1)
        val sync = startedSynchronizer()
        notice(TelemetryNotice.Subscribed)

        sync.stop()
        assertEquals(SyncStatus.Stopped, sync.state.value.status)

        query.persist(2, 3)
        sync.start(backgroundScope)
        runCurrent()
        assertEquals(SyncStatus.Reconciling, sync.state.value.status)

        notice(TelemetryNotice.Subscribed)
        assertEquals(SyncStatus.Live, sync.state.value.status)
        assertEquals(StreamVersion(3), sync.state.value.lastAppliedVersion)
    }
}
