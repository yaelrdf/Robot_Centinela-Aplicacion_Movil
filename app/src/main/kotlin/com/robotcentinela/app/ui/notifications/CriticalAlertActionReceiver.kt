package com.robotcentinela.app.ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.incidents.domain.AlertId
import com.robotcentinela.app.shell.CentinelaApplication
import com.robotcentinela.app.ui.model.OperationPresentation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Receptor para las acciones de la notificación persistente de alerta crítica en la barra de estado:
 * «Silenciar aviso» y «Resolver alerta» (ADR-011).
 *
 * Declarado con `android:exported="false"` para cumplir con las políticas de seguridad de
 * Android 12 (API 31) hasta Android 17 (API 37).
 */
class CriticalAlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_DISMISS_CRITICAL_ALERT -> dismiss(context, intent)
            ACTION_RESOLVE_CRITICAL_ALERT -> resolve(context, intent)
        }
    }

    private fun dismiss(context: Context, intent: Intent) {
        val alertId = parseAlertId(intent)

        // Cancela la notificación visualmente en la barra de estado y detiene estímulos activos.
        AndroidCriticalAlertNotifier.stopActiveSensorySignals(context)
        NotificationManagerCompat.from(context).cancel(AndroidCriticalAlertNotifier.NOTIFICATION_ID)

        if (alertId != null) {
            CriticalAlertDismissBus.emitDismiss(alertId)
        }
    }

    /**
     * Cierra la alerta en el servidor sin abrir la app. Si falla (sin sesión porque el proceso se reinició, sin red o
     * sin rol técnico) la notificación se conserva y se avisa con un mensaje: Android no deja abrir una actividad
     * desde un receptor en segundo plano.
     */
    private fun resolve(context: Context, intent: Intent) {
        val alertId = parseAlertId(intent) ?: return
        val robotId = intent.getStringExtra(EXTRA_ROBOT_ID)
            ?.let { runCatching { RobotId(UUID.fromString(it)) }.getOrNull() }
            ?: return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = (appContext as? CentinelaApplication)?.containerResult?.getOrNull()
                val resolveAlert = container?.resolveAlert
                if (resolveAlert == null) {
                    toast(appContext, "Abre V.I.S.O.R. para resolver la alerta.")
                    return@launch
                }
                try {
                    resolveAlert(robotId, alertId)
                    AndroidCriticalAlertNotifier.stopActiveSensorySignals(appContext)
                    NotificationManagerCompat.from(appContext).cancel(AndroidCriticalAlertNotifier.NOTIFICATION_ID)
                    AlertResolvedBus.emitResolved(alertId)
                    toast(appContext, "Alerta resuelta.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast(appContext, OperationPresentation.resolveErrorMessage(e))
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun parseAlertId(intent: Intent): AlertId? =
        intent.getStringExtra(EXTRA_ALERT_ID)?.let { runCatching { AlertId(UUID.fromString(it)) }.getOrNull() }

    private fun toast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

    companion object {
        const val ACTION_DISMISS_CRITICAL_ALERT = "com.robotcentinela.app.action.DISMISS_CRITICAL_ALERT"
        const val ACTION_RESOLVE_CRITICAL_ALERT = "com.robotcentinela.app.action.RESOLVE_CRITICAL_ALERT"
        const val EXTRA_ALERT_ID = "extra_alert_id"
        const val EXTRA_ROBOT_ID = "extra_robot_id"
    }
}
