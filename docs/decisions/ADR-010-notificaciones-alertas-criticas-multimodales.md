# ADR-010: Sistema de notificación multimodal (sonido, vibración y flash) y persistencia en segundo plano para alertas críticas

- **Estado:** Aceptada
- **Fecha:** 2026-11-03

## Contexto

Cuando el servidor registra una alerta abierta de severidad **crítica** (por ejemplo, `gas_signal_high` o `sensor_fault`), el banner visual dentro de la aplicación no es suficiente si la persona usuaria no está mirando la pantalla o si la aplicación pasó a segundo plano. Se requiere un sistema de alerta multimodal que combine **sonido, vibración y flash LED**, acompañado de una **notificación persistente en la barra de estado con nivel de alerta `HIGH`**, compatible desde el SDK mínimo del proyecto (`minSdk = 29`, Android 10) hasta **Android 17 (`compileSdk = 37`)**.

## Decisión

1. **Separación entre coordinación y APIs de plataforma**:
   - `CriticalAlertCoordinator` encapsula la lógica pura de detección de alertas críticas abiertas (`AlertState.Open` y `OperationPresentation.isHighSeverity`), deduplicación por `AlertId` y `lastSeenAt`, reafirmación al pasar a segundo plano y cancelación al silenciar o resolver el incidente.
   - `CriticalAlertGateway` define el puerto de salida implementado por `AndroidCriticalAlertNotifier`.
2. **Notificación en barra de estado de nivel `HIGH` con persistencia (`ongoing`)**:
   - Canal `visor_critical_alerts_high_v1` configurado con `NotificationManager.IMPORTANCE_HIGH`, `Notification.VISIBILITY_PUBLIC`, luz LED roja (`#C00000`), patrón de vibración y sonido de alarma (`AudioAttributes.USAGE_ALARM`).
   - Notificación construida con `NotificationCompat.PRIORITY_HIGH`, `NotificationCompat.CATEGORY_ALARM` y `setOngoing(true)` para permanecer fija en la barra de estado mientras la alerta crítica continúe activa, incluyendo la acción **"Silenciar aviso"** atendida por `CriticalAlertActionReceiver` (`android:exported="false"`).
3. **Señalización multimodal ("sonido, vibración y flash")**:
   - **Sonido**: Reproducción con `RingtoneManager` (`TYPE_ALARM` / `TYPE_NOTIFICATION`) bajo `AudioAttributes.USAGE_ALARM` y respaldo mediante `ToneGenerator(AudioManager.STREAM_ALARM)`.
   - **Vibración**: Patrón de emergencia (`0, 500, 250, 500, 250, 700 ms`) mediante `VibrationEffect.createWaveform`.
   - **Flash LED**: Secuencia estroboscópica de 4 destellos controlada con `CameraManager.setTorchMode` sobre la cámara con `FLASH_INFO_AVAILABLE`, garantizando el apagado en bloque `finally`.
4. **Persistencia en segundo plano respetando RB28**:
   - Al pasar la app a segundo plano (`MainViewModel.onStop`), se detiene el sincronizador de telemetría de alta frecuencia (cumpliendo RB28: segundo plano no se declara "En vivo"), pero se mantiene activo el trabajo de monitoreo de alertas (`alertPollingJob`) para detectar nuevas alertas críticas o mantener la notificación `HIGH` persistente en la barra de estado.
5. **Matriz de compatibilidad Android 10 (API 29) a Android 17 (API 37)**:
   - **API 29–30 (Android 10–11)**: `Vibrator` + `VibrationEffect` + `AudioAttributes`, canal `IMPORTANCE_HIGH` sin permiso en tiempo de ejecución.
   - **API 31–32 (Android 12–12L)**: `VibratorManager.defaultVibrator` y `PendingIntent.FLAG_IMMUTABLE`.
   - **API 33–37 (Android 13–17)**: Solicitud en tiempo de ejecución de `Manifest.permission.POST_NOTIFICATIONS` y uso de `VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)`.

## Consecuencias

- Las alertas críticas se perciben de forma auditiva, háptica y lumínica tanto en primer como en segundo plano.
- El usuario puede configurar independientemente sonido, vibración y flash, así como ejecutar una prueba local desde **Ajustes**.
- La lógica cuenta con pruebas unitarias deterministas en `CriticalAlertCoordinatorTest`.

