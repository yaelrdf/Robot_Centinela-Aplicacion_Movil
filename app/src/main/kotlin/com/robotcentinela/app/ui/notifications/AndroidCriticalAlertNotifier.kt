package com.robotcentinela.app.ui.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.robotcentinela.app.R
import com.robotcentinela.app.incidents.domain.AlertId
import com.robotcentinela.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Implementación Android del sistema de notificación multimodal (sonido, vibración y flash) y
 * notificación persistente en la barra de estado con nivel de alerta `HIGH`.
 *
 * Compatible desde `minSdk = 29` (Android 10) hasta `compileSdk = 37` (Android 17):
 * - **API 29..30 (Android 10-11)**: `Vibrator` + `VibrationEffect` + `AudioAttributes`, `NotificationChannel` (`IMPORTANCE_HIGH`).
 * - **API 31..32 (Android 12-12L)**: `VibratorManager.defaultVibrator` + `PendingIntent.FLAG_IMMUTABLE`.
 * - **API 33..37 (Android 13-17)**: Verificación explícita de `POST_NOTIFICATIONS` y uso de `VibrationAttributes.USAGE_ALARM`.
 */
class AndroidCriticalAlertNotifier(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : CriticalAlertGateway {

    private val appContext = context.applicationContext
    private var flashJob: Job? = null
    private var soundJob: Job? = null

    init {
        ensureHighPriorityChannel()
    }

    /**
     * Crea o actualiza el canal de notificaciones con nivel de importancia `IMPORTANCE_HIGH`
     * para que el sistema muestre banner emergente (heads-up), ícono en barra de estado,
     * sonido de alarma, vibración y luz de notificación.
     */
    private fun ensureHighPriorityChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notificaciones persistentes de nivel alto para alertas críticas de gas y seguridad del robot."
            enableVibration(true)
            vibrationPattern = CRITICAL_VIBRATION_PATTERN
            enableLights(true)
            lightColor = CRITICAL_COLOR_ARGB
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            if (alarmUri != null) {
                setSound(alarmUri, audioAttributes)
            }
        }
        manager.createNotificationChannel(channel)
    }

    override fun showHighPriorityNotification(payload: CriticalAlertNotificationPayload) {
        if (!canPostNotifications(appContext)) return
        ensureHighPriorityChannel()

        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(CriticalAlertActionReceiver.EXTRA_ALERT_ID, payload.alertId.value.toString())
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = Intent(appContext, CriticalAlertActionReceiver::class.java).apply {
            action = CriticalAlertActionReceiver.ACTION_DISMISS_CRITICAL_ALERT
            putExtra(CriticalAlertActionReceiver.EXTRA_ALERT_ID, payload.alertId.value.toString())
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE_DISMISS_ALERT,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val expandedText = buildString {
            append(payload.body)
            append("\nLa alerta permanece activa en el servidor. ")
            append("Toca para abrir V.I.S.O.R. o usa «Silenciar aviso» para descartar la señal.")
        }

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_error)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(CRITICAL_COLOR_ARGB)
            .setLights(CRITICAL_COLOR_ARGB, 500, 500)
            .setOngoing(payload.persistent)
            .setAutoCancel(!payload.persistent)
            .setOnlyAlertOnce(payload.shouldAlertOnce)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_close,
                "Silenciar aviso",
                dismissPendingIntent,
            )

        if (payload.preferences.vibrationEnabled) {
            builder.setVibrate(CRITICAL_VIBRATION_PATTERN)
        } else {
            builder.setVibrate(longArrayOf(0L))
        }

        if (!payload.preferences.soundEnabled) {
            builder.setSilent(true)
        }

        runCatching {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, builder.build())
        }
    }

    override fun cancelNotification(alertId: AlertId?) {
        runCatching {
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
        }
    }

    override fun triggerSensoryAlert(preferences: CriticalAlertPreferences) {
        if (preferences.soundEnabled) {
            triggerSound()
        }
        if (preferences.vibrationEnabled) {
            triggerVibration(appContext)
        }
        if (preferences.flashEnabled) {
            triggerFlash()
        }
    }

    override fun stopSensoryAlert() {
        soundJob?.cancel()
        soundJob = null
        flashJob?.cancel()
        flashJob = null
        stopActiveSensorySignals(appContext)
    }

    private fun triggerSound() {
        soundJob?.cancel()
        soundJob = scope.launch {
            runCatching {
                stopRingtoneOnly()
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = uri?.let { RingtoneManager.getRingtone(appContext, it) }
                if (ringtone != null) {
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    activeRingtone = ringtone
                    ringtone.play()
                    delay(SOUND_DURATION_MS)
                    if (ringtone.isPlaying) {
                        ringtone.stop()
                    }
                } else {
                    val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                    try {
                        toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1_500)
                        delay(1_600)
                    } finally {
                        toneGen.release()
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun triggerVibration(context: Context) {
        runCatching {
            val vibrator = resolveVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            val effect = VibrationEffect.createWaveform(CRITICAL_VIBRATION_PATTERN, -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
                vibrator.vibrate(effect, attrs)
            } else {
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                vibrator.vibrate(effect, audioAttrs)
            }
        }
    }

    private fun triggerFlash() {
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) return
        val cameraManager = appContext.getSystemService(CameraManager::class.java) ?: return
        val cameraId = findFlashCameraId(cameraManager) ?: return

        flashJob?.cancel()
        flashJob = scope.launch {
            try {
                repeat(FLASH_PULSES) {
                    runCatching { cameraManager.setTorchMode(cameraId, true) }
                    delay(FLASH_ON_MS)
                    runCatching { cameraManager.setTorchMode(cameraId, false) }
                    delay(FLASH_OFF_MS)
                }
            } finally {
                runCatching { cameraManager.setTorchMode(cameraId, false) }
            }
        }
    }

    private fun findFlashCameraId(cameraManager: CameraManager): String? = runCatching {
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    companion object {
        const val CHANNEL_ID = "visor_critical_alerts_high_v1"
        const val CHANNEL_NAME = "Alertas Críticas del Robot"
        const val NOTIFICATION_ID = 9001

        private const val REQUEST_CODE_OPEN_APP = 101
        private const val REQUEST_CODE_DISMISS_ALERT = 102
        private const val CRITICAL_COLOR_ARGB = 0xFFC00000.toInt()
        private const val SOUND_DURATION_MS = 3_500L
        private const val FLASH_PULSES = 4
        private const val FLASH_ON_MS = 220L
        private const val FLASH_OFF_MS = 180L

        val CRITICAL_VIBRATION_PATTERN = longArrayOf(0L, 500L, 250L, 500L, 250L, 700L)

        @Volatile
        private var activeRingtone: Ringtone? = null

        /** Verifica si la app cuenta con el permiso de notificaciones requerido según el nivel de SDK (API 29..37). */
        fun canPostNotifications(context: Context): Boolean {
            if (CriticalAlertSdkPolicy.requiresRuntimeNotificationPermission(Build.VERSION.SDK_INT)) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return false
            }
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        private fun resolveVibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                context.getSystemService(Vibrator::class.java)
            }

        private fun stopRingtoneOnly() {
            runCatching {
                activeRingtone?.let {
                    if (it.isPlaying) it.stop()
                }
                activeRingtone = null
            }
        }

        fun stopActiveSensorySignals(context: Context) {
            stopRingtoneOnly()
            runCatching {
                resolveVibrator(context)?.cancel()
            }
            runCatching {
                if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                    val cameraManager = context.getSystemService(CameraManager::class.java)
                    val id = cameraManager?.cameraIdList?.firstOrNull { camId ->
                        cameraManager.getCameraCharacteristics(camId)
                            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                    if (id != null) {
                        cameraManager.setTorchMode(id, false)
                    }
                }
            }
        }
    }
}

