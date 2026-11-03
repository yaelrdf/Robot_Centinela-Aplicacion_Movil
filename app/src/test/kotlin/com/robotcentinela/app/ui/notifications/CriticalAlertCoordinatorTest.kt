package com.robotcentinela.app.ui.notifications

import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.incidents.domain.Alert
import com.robotcentinela.app.incidents.domain.AlertId
import com.robotcentinela.app.incidents.domain.AlertOrigin
import com.robotcentinela.app.incidents.domain.AlertState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CriticalAlertCoordinatorTest {

    private class FakeCriticalAlertGateway : CriticalAlertGateway {
        val notifications = mutableListOf<CriticalAlertNotificationPayload>()
        val cancelledIds = mutableListOf<AlertId?>()
        val sensoryTriggers = mutableListOf<CriticalAlertPreferences>()
        var stopSensoryCount = 0
        var activePayload: CriticalAlertNotificationPayload? = null

        override fun showHighPriorityNotification(payload: CriticalAlertNotificationPayload) {
            notifications += payload
            activePayload = payload
        }

        override fun cancelNotification(alertId: AlertId?) {
            cancelledIds += alertId
            activePayload = null
        }

        override fun triggerSensoryAlert(preferences: CriticalAlertPreferences) {
            sensoryTriggers += preferences
        }

        override fun stopSensoryAlert() {
            stopSensoryCount++
        }
    }

    private val robotId = RobotId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val baseTime = Instant.parse("2026-10-30T12:00:00Z")

    private fun makeAlert(
        idSuffix: Int = 1,
        severity: String = "critical",
        type: String = "gas_signal_high",
        state: AlertState = AlertState.Open,
        lastSeenAt: Instant = baseTime,
    ): Alert = Alert(
        id = AlertId(UUID.fromString("00000000-0000-0000-0000-00000000000$idSuffix")),
        robotId = robotId,
        ruleId = null,
        type = type,
        severity = severity,
        state = state,
        origin = AlertOrigin.BackendRule,
        openedAt = baseTime,
        lastSeenAt = lastSeenAt,
        resolvedAt = null,
    )

    @Test
    fun `alerta critica abierta dispara sonido vibracion flash y notificacion HIGH persistente`() {
        val gateway = FakeCriticalAlertGateway()
        val coordinator = CriticalAlertCoordinator(gateway)
        val alert = makeAlert(severity = "critical", type = "gas_signal_high")

        coordinator.evaluate(
            openAlerts = listOf(alert),
            dismissedIds = emptySet(),
            homeName = "Casa Demo",
            robotCode = "RC-01",
            inBackground = false,
        )

        assertEquals(1, gateway.notifications.size)
        assertEquals(1, gateway.sensoryTriggers.size)
        val payload = gateway.activePayload
        assertNotNull(payload)
        assertEquals(alert.id, payload.alertId)
        assertTrue(payload.persistent, "La notificación debe ser persistente (ongoing)")
        assertFalse(payload.shouldAlertOnce, "El primer disparo debe alertar con prioridad HIGH")
        assertTrue(payload.title.contains("Crítica"))
        assertTrue(payload.title.contains("Señal de gas elevada"))
        assertTrue(gateway.sensoryTriggers.first().soundEnabled)
        assertTrue(gateway.sensoryTriggers.first().vibrationEnabled)
        assertTrue(gateway.sensoryTriggers.first().flashEnabled)
    }

    @Test
    fun `alertas informativas o de advertencia no disparan la alarma critica`() {
        val gateway = FakeCriticalAlertGateway()
        val coordinator = CriticalAlertCoordinator(gateway)
        val warningAlert = makeAlert(idSuffix = 2, severity = "warning", type = "battery_low")
        val infoAlert = makeAlert(idSuffix = 3, severity = "info", type = "calibration_pending")

        coordinator.evaluate(
            openAlerts = listOf(warningAlert, infoAlert),
            dismissedIds = emptySet(),
            homeName = "Casa Demo",
            robotCode = "RC-01",
            inBackground = true,
        )

        assertTrue(gateway.notifications.isEmpty())
        assertTrue(gateway.sensoryTriggers.isEmpty())
        assertNull(gateway.activePayload)
    }

    @Test
    fun `persistencia en segundo plano mantiene notificacion HIGH y detecta nuevas ocurrencias`() {
        val gateway = FakeCriticalAlertGateway()
        val coordinator = CriticalAlertCoordinator(gateway)
        val alert = makeAlert(severity = "critical")

        // 1. Se detecta en primer plano
        coordinator.evaluate(
            openAlerts = listOf(alert),
            dismissedIds = emptySet(),
            homeName = "Casa Demo",
            robotCode = "RC-01",
            inBackground = false,
        )
        assertEquals(1, gateway.notifications.size)
        assertEquals(1, gateway.sensoryTriggers.size)

        // 2. La app pasa a segundo plano: reafirma la notificación persistente en barra de estado sin repetir el estímulo inicial
        coordinator.evaluate(
            openAlerts = listOf(alert),
            dismissedIds = emptySet(),
            homeName = "Casa Demo",
            robotCode = "RC-01",
            inBackground = true,
        )
        assertEquals(2, gateway.notifications.size)
        assertTrue(gateway.notifications.last().persistent)
        assertTrue(gateway.notifications.last().shouldAlertOnce)
        assertEquals(1, gateway.sensoryTriggers.size)

        // 3. Estando en segundo plano llega una nueva ocurrencia (lastSeenAt avanza): vuelve a disparar sonido, vibración y flash
        val updatedAlert = alert.copy(lastSeenAt = baseTime.plusSeconds(45))
        coordinator.evaluate(
            openAlerts = listOf(updatedAlert),
            dismissedIds = emptySet(),
            homeName = "Casa Demo",
            robotCode = "RC-01",
            inBackground = true,
        )
        assertEquals(3, gateway.notifications.size)
        assertFalse(gateway.notifications.last().shouldAlertOnce)
        assertEquals(2, gateway.sensoryTriggers.size)
    }

    @Test
    fun `silenciar o resolver la alerta critica detiene sonido vibracion flash y cancela la notificacion`() {
        val gateway = FakeCriticalAlertGateway()
        val coordinator = CriticalAlertCoordinator(gateway)
        val alert = makeAlert(severity = "critical")

        coordinator.evaluate(
            openAlerts = listOf(alert),
            dismissedIds = emptySet(),
            homeName = "Casa Demo",
            robotCode = "RC-01",
            inBackground = true,
        )
        assertNotNull(gateway.activePayload)

        // El usuario silencia la alerta desde el banner o la acción de la notificación
        coordinator.onAlertDismissed(alert.id)

        assertEquals(1, gateway.stopSensoryCount)
        assertEquals<List<AlertId?>>(listOf(alert.id), gateway.cancelledIds)
        assertNull(gateway.activePayload)
    }

    @Test
    fun `respeta preferencias personalizadas de sonido vibracion y flash`() {
        val gateway = FakeCriticalAlertGateway()
        val coordinator = CriticalAlertCoordinator(gateway)
        val alert = makeAlert(severity = "high")
        val customPrefs = CriticalAlertPreferences(
            soundEnabled = false,
            vibrationEnabled = true,
            flashEnabled = true,
        )

        coordinator.evaluate(
            openAlerts = listOf(alert),
            dismissedIds = emptySet(),
            homeName = "Casa Demo",
            robotCode = "RC-01",
            inBackground = false,
            preferences = customPrefs,
        )

        assertEquals(1, gateway.sensoryTriggers.size)
        assertEquals(customPrefs, gateway.sensoryTriggers.first())
        assertEquals(customPrefs, gateway.activePayload?.preferences)
    }

    @Test
    fun `compatibilidad de SDK cubre desde Android 10 API 29 hasta Android 17 API 37`() {
        for (sdk in CriticalAlertSdkPolicy.MIN_SUPPORTED_SDK..CriticalAlertSdkPolicy.MAX_VERIFIED_SDK) {
            assertTrue(CriticalAlertSdkPolicy.isCompatibleSdk(sdk), "SDK $sdk debe ser compatible")
            assertEquals(sdk >= 31, CriticalAlertSdkPolicy.usesVibratorManager(sdk))
            assertEquals(sdk >= 33, CriticalAlertSdkPolicy.usesVibrationAttributes(sdk))
            assertEquals(sdk >= 33, CriticalAlertSdkPolicy.requiresRuntimeNotificationPermission(sdk))
        }
    }

    @Test
    fun `bus de descarte emite el AlertId cuando se acciona desde la notificacion del sistema`() = runTest {
        val alert = makeAlert(severity = "critical")
        var received: AlertId? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            received = CriticalAlertDismissBus.dismissEvents.first()
        }
        CriticalAlertDismissBus.emitDismiss(alert.id)
        assertEquals<AlertId?>(alert.id, received)
        job.cancel()
    }
}

