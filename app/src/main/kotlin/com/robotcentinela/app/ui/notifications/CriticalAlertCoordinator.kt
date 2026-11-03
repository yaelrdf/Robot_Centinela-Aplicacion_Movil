package com.robotcentinela.app.ui.notifications

import com.robotcentinela.app.incidents.domain.Alert
import com.robotcentinela.app.incidents.domain.AlertId
import com.robotcentinela.app.incidents.domain.AlertState
import com.robotcentinela.app.ui.model.OperationPresentation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant

/**
 * Preferencias del usuario para las señales de alerta crítica (sonido, vibración y flash).
 */
data class CriticalAlertPreferences(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val flashEnabled: Boolean = true,
)

/**
 * Datos listos para presentar en la notificación de barra de estado de nivel HIGH.
 */
data class CriticalAlertNotificationPayload(
    val alertId: AlertId,
    val title: String,
    val body: String,
    val homeName: String,
    val robotCode: String,
    val originLabel: String,
    val openedAt: Instant,
    val lastSeenAt: Instant,
    /**
     * `true` para mantener la notificación fija/persistente (`ongoing`) en la barra de estado
     * mientras la alerta crítica siga activa o la app se encuentre en segundo plano.
     */
    val persistent: Boolean,
    /**
     * `true` cuando es la primera vez que se notifica este incidente o una nueva ocurrencia,
     * activando sonido, vibración y flash.
     */
    val shouldAlertOnce: Boolean,
    val preferences: CriticalAlertPreferences,
)

/**
 * Puerto de salida para desacoplar la lógica de coordinación de las APIs de Android (SDK 29..37).
 */
interface CriticalAlertGateway {
    fun showHighPriorityNotification(payload: CriticalAlertNotificationPayload)
    fun cancelNotification(alertId: AlertId? = null)
    fun triggerSensoryAlert(preferences: CriticalAlertPreferences)
    fun stopSensoryAlert()
}

/**
 * Política de compatibilidad de SDK para Android 10 (API 29) hasta Android 17 (API 37).
 */
object CriticalAlertSdkPolicy {
    const val MIN_SUPPORTED_SDK = 29
    const val MAX_VERIFIED_SDK = 37

    /** En Android 13 (API 33, TIRAMISU) y superiores hasta Android 17 (API 37) se requiere permiso en ejecución. */
    fun requiresRuntimeNotificationPermission(sdkInt: Int): Boolean = sdkInt >= 33

    /** En Android 12 (API 31, S) y superiores hasta Android 17 (API 37) se usa `VibratorManager`. */
    fun usesVibratorManager(sdkInt: Int): Boolean = sdkInt >= 31

    /** En Android 13 (API 33, TIRAMISU) y superiores hasta Android 17 (API 37) se usa `VibrationAttributes`. */
    fun usesVibrationAttributes(sdkInt: Int): Boolean = sdkInt >= 33

    /** Verifica que el SDK se encuentre dentro del rango soportado (Android 10 a Android 17+). */
    fun isCompatibleSdk(sdkInt: Int): Boolean = sdkInt >= MIN_SUPPORTED_SDK
}

/**
 * Bus de eventos para comunicar acciones de la notificación del sistema (ej. botón "Silenciar aviso")
 * con la sesión activa del `MainViewModel`.
 */
object CriticalAlertDismissBus {
    private val _dismissEvents = MutableSharedFlow<AlertId>(extraBufferCapacity = 8)
    val dismissEvents: SharedFlow<AlertId> = _dismissEvents.asSharedFlow()

    fun emitDismiss(alertId: AlertId) {
        _dismissEvents.tryEmit(alertId)
    }
}

/**
 * Coordinador de alertas críticas con soporte de sonido, vibración, flash y persistencia en
 * segundo plano mediante notificación de nivel `HIGH`.
 *
 * Reglas:
 * 1. Solo procesa alertas en estado [AlertState.Open] cuya severidad sea crítica/alta
 *    ([OperationPresentation.isHighSeverity]).
 * 2. Si existe una alerta crítica abierta y no descartada:
 *    - Publica o actualiza una notificación en la barra de estado con prioridad `HIGH` y
 *      persistencia (`persistent = true`) tanto en primer plano como cuando la app pasa a
 *      segundo plano (`inBackground = true`).
 *    - Si el incidente es nuevo o registra una ocurrencia más reciente (`lastSeenAt`), dispara
 *      la señal multimodal (sonido, vibración y flash) según las preferencias del usuario.
 * 3. Cuando la alerta se descarta/silencia o deja de estar abierta en el servidor, detiene
 *    cualquier estímulo sensorial en curso y retira la notificación de la barra de estado.
 */
class CriticalAlertCoordinator(
    private val gateway: CriticalAlertGateway,
) {
    private var lastNotifiedAlertId: AlertId? = null
    private var lastNotifiedSeenAt: Instant? = null
    private var lastBackgroundState: Boolean = false
    private var activeAlertId: AlertId? = null

    fun evaluate(
        openAlerts: List<Alert>,
        dismissedIds: Set<AlertId>,
        homeName: String,
        robotCode: String?,
        inBackground: Boolean,
        preferences: CriticalAlertPreferences = CriticalAlertPreferences(),
    ) {
        val candidate = openAlerts.firstOrNull { alert ->
            alert.state == AlertState.Open &&
                OperationPresentation.isHighSeverity(alert) &&
                alert.id !in dismissedIds
        }

        if (candidate == null) {
            if (activeAlertId != null) {
                gateway.stopSensoryAlert()
                gateway.cancelNotification(activeAlertId)
                activeAlertId = null
                lastNotifiedAlertId = null
                lastNotifiedSeenAt = null
            }
            lastBackgroundState = inBackground
            return
        }

        val isNewIncident = candidate.id != lastNotifiedAlertId
        val hasNewOccurrence = lastNotifiedSeenAt == null || candidate.lastSeenAt.isAfter(lastNotifiedSeenAt)
        val shouldTriggerSensory = isNewIncident || hasNewOccurrence
        val transitionedToBackground = inBackground && !lastBackgroundState

        // Solo re-emite la notificación si es un nuevo incidente, nueva ocurrencia o transición a segundo plano.
        if (shouldTriggerSensory || transitionedToBackground || activeAlertId != candidate.id) {
            val severityLabel = OperationPresentation.severity(candidate.severity).text
            val alertTitle = OperationPresentation.alertTitle(candidate.type)
            val originLabel = OperationPresentation.origin(candidate.origin)
            val robotLabel = robotCode ?: "Sin código"

            val payload = CriticalAlertNotificationPayload(
                alertId = candidate.id,
                title = "$severityLabel: $alertTitle",
                body = "$homeName · Robot $robotLabel · $originLabel",
                homeName = homeName,
                robotCode = robotLabel,
                originLabel = originLabel,
                openedAt = candidate.openedAt,
                lastSeenAt = candidate.lastSeenAt,
                persistent = true,
                shouldAlertOnce = !shouldTriggerSensory,
                preferences = preferences,
            )

            gateway.showHighPriorityNotification(payload)

            if (shouldTriggerSensory) {
                gateway.triggerSensoryAlert(preferences)
            }
        }

        activeAlertId = candidate.id
        lastNotifiedAlertId = candidate.id
        lastNotifiedSeenAt = candidate.lastSeenAt
        lastBackgroundState = inBackground
    }

    fun onAlertDismissed(alertId: AlertId) {
        if (activeAlertId == alertId) {
            gateway.stopSensoryAlert()
            gateway.cancelNotification(alertId)
            activeAlertId = null
        }
    }

    fun clear() {
        gateway.stopSensoryAlert()
        gateway.cancelNotification(activeAlertId)
        activeAlertId = null
        lastNotifiedAlertId = null
        lastNotifiedSeenAt = null
    }
}

