package com.robotcentinela.app.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.robotcentinela.app.incidents.domain.AlertId
import java.util.UUID

/**
 * Receptor para las acciones de la notificación persistente de alerta crítica en la barra de estado
 * (por ejemplo, el botón "Silenciar aviso").
 *
 * Declarado con `android:exported="false"` para cumplir con las políticas de seguridad de
 * Android 12 (API 31) hasta Android 17 (API 37).
 */
class CriticalAlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_DISMISS_CRITICAL_ALERT) return

        val rawId = intent.getStringExtra(EXTRA_ALERT_ID)
        val alertId = rawId?.let { runCatching { AlertId(UUID.fromString(it)) }.getOrNull() }

        // Cancela la notificación visualmente en la barra de estado y detiene estímulos activos.
        AndroidCriticalAlertNotifier.stopActiveSensorySignals(context)
        NotificationManagerCompat.from(context).cancel(AndroidCriticalAlertNotifier.NOTIFICATION_ID)

        if (alertId != null) {
            CriticalAlertDismissBus.emitDismiss(alertId)
        }
    }

    companion object {
        const val ACTION_DISMISS_CRITICAL_ALERT = "com.robotcentinela.app.action.DISMISS_CRITICAL_ALERT"
        const val EXTRA_ALERT_ID = "extra_alert_id"
    }
}

