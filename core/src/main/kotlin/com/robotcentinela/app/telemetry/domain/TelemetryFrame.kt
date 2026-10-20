package com.robotcentinela.app.telemetry.domain

import com.robotcentinela.app.environmentalmonitoring.domain.SensorReading
import com.robotcentinela.app.homemapping.domain.MapVersionId
import com.robotcentinela.app.homemapping.domain.Pose
import com.robotcentinela.app.missions.domain.MissionId
import com.robotcentinela.app.robothealth.domain.BatteryStatus
import com.robotcentinela.app.robothealth.domain.RobotReportedState
import java.time.Instant

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Trama de telemetría persistida: representa el conjunto de muestras registradas en un instante,
 * junto con su identidad y contexto de captura en el robot.
 *
 * Cada trama se procesa de forma completa, preservando la coherencia temporal de todas sus lecturas
 * sin intercalar mediciones entre diferentes instantes de captura.
 *
 * @property id Identificador único de la trama.
 * @property schemaVersion Versión del esquema de telemetría.
 * @property deviceId Identificador del dispositivo emisor.
 * @property bootId Identificador de la sesión de arranque del microcontrolador.
 * @property sequence Número de secuencia incremental asignado por el firmware dentro de la sesión actual.
 * @property capturedAt Marca de tiempo de captura reportada por el dispositivo; es nula si no se dispone de reloj.
 * @property monotonicMs Tiempo monotónico relativo en milisegundos desde el inicio del microcontrolador.
 * @property clockQuality Calidad y sincronización del reloj del dispositivo en el momento de la captura.
 * @property missionId Identificador de la misión en curso durante la captura, si aplica.
 * @property mapVersionId Identificador de la versión del plano cartográfico asociado.
 * @property pose Coordenadas y orientación estimada del robot en el plano.
 * @property readings Lista de lecturas ambientales registradas por los sensores del robot.
 * @property battery Nivel y estado de carga de la batería del dispositivo.
 * @property robotState Estado operativo reportado por el robot.
 * @property motorsActive Indicador del estado de tracción o activación de motores.
 * @property firmwareVersion Versión del firmware del robot.
 * @property receivedAt Marca de tiempo en que el backend persistió la trama.
 * @property streamVersion Versión secuencial del flujo de eventos por robot.
 */
data class TelemetryFrame(
    val id: FrameId,
    val schemaVersion: String,
    val deviceId: DeviceId,
    val bootId: BootId,
    val sequence: Long,
    val capturedAt: Instant?,
    val monotonicMs: Long,
    val clockQuality: ClockQuality,
    val missionId: MissionId?,
    val mapVersionId: MapVersionId?,
    val pose: Pose?,
    val readings: List<SensorReading>,
    val battery: BatteryStatus?,
    val robotState: RobotReportedState?,
    val motorsActive: Boolean?,
    val firmwareVersion: String?,
    val receivedAt: Instant,
    val streamVersion: StreamVersion,
) {
    init {
        require(sequence >= 0) { "sequence no puede ser negativo" }
        require(monotonicMs >= 0) { "monotonic_ms no puede ser negativo" }
        require(readings.map { it.channelKey }.toSet().size == readings.size) {
            "Un frame no puede repetir channel_key (§7.2)"
        }
    }

    /**
     * Hora de captura confiable: solo existe si el reloj estaba sincronizado y el frame trae
     * `captured_at`.
     */
    val reliableCapturedAt: Instant?
        get() = capturedAt?.takeIf { clockQuality == ClockQuality.Synced }
}
