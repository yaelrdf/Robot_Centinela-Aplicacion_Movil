package com.robotcentinela.app.ui.model

import com.robotcentinela.app.environmentalmonitoring.domain.DataFreshness
import com.robotcentinela.app.incidents.domain.Alert
import com.robotcentinela.app.incidents.domain.AlertOrigin
import com.robotcentinela.app.incidents.domain.AlertState
import com.robotcentinela.app.missions.domain.MissionStatus
import com.robotcentinela.app.missions.domain.MissionSummary
import com.robotcentinela.app.missions.domain.StationOutcome
import com.robotcentinela.app.robothealth.application.RobotHealthReport
import com.robotcentinela.app.robothealth.domain.BatteryInterpretation
import com.robotcentinela.app.robothealth.domain.IngestStatus
import com.robotcentinela.app.robothealth.domain.RobotReportedState
import com.robotcentinela.app.shared.AccessDeniedException
import com.robotcentinela.app.shared.ContractViolationException
import com.robotcentinela.app.shared.DataAccessException
import com.robotcentinela.app.shared.NotInScopeException
import com.robotcentinela.app.telemetry.application.SyncStatus
import java.time.Instant
import java.time.ZoneId
import kotlin.time.toKotlinDuration

/** Batería para el panel de operación (HU08). */
data class BatteryModel(
    /** "72 %" o "11.4 V"; `null` si no hay dato. */
    val primary: String?,
    /** "Porcentaje no disponible", "11.4 V"... */
    val secondary: String,
    /** Método declarado ("Solo voltaje", "Medición", "Estimación"). */
    val method: String?,
)

/** Uno de los tres eslabones de la barra de conectividad (§12.5: robot, servidor de ingesta y cliente). */
data class ConnectivityItem(val title: String, val status: StatusLabel, val detail: String?)

/** Reglas de presentación del panel operativo, eventos y misiones. */
object OperationPresentation {

    fun methodLabel(raw: String): String = when (raw) {
        "voltage_only" -> "Solo voltaje"
        "measured" -> "Medición"
        "estimated" -> "Estimación"
        "coulomb_counter" -> "Contador de carga"
        else -> raw
    }

    /** HU08 CDA 1: sin SOC se muestra "Porcentaje no disponible"; el voltaje nunca se convierte a %. */
    fun battery(battery: BatteryInterpretation): BatteryModel = when (battery) {
        is BatteryInterpretation.WithPercentage -> BatteryModel(
            primary = "${Formats.number(battery.socPct, 0)} %",
            secondary = battery.voltageV?.let { "${Formats.number(it, 1)} V" } ?: "Voltaje sin dato",
            method = methodLabel(battery.method),
        )
        is BatteryInterpretation.PercentageUnavailable -> BatteryModel(
            primary = battery.voltageV?.let { "${Formats.number(it, 1)} V" },
            secondary = "Porcentaje no disponible",
            method = methodLabel(battery.method),
        )
        BatteryInterpretation.NoData -> BatteryModel(null, "Sin dato de batería", null)
    }

    fun robotStateLabel(state: RobotReportedState): String = when (state.raw) {
        "moving" -> "En movimiento"
        "sampling" -> "Muestreando"
        "docked" -> "En base"
        "idle" -> "En espera"
        "paused" -> "En pausa"
        "stopped" -> "Detenido"
        else -> state.raw
    }

    /** Barra de conectividad: robot, ingesta y esta app, cada uno con su propio estado (§12.5, RF29). */
    fun connectivity(health: RobotHealthReport, sync: SyncStatus, zone: ZoneId): List<ConnectivityItem> {
        val robot = when (val freshness = health.lastFrameFreshness) {
            is DataFreshness.Fresh -> ConnectivityItem(
                title = "Robot",
                status = StatusLabel(
                    health.reportedState?.let(::robotStateLabel) ?: "Reportando",
                    StatusTone.Operational,
                    StatusIcon.Valid,
                ),
                detail = "Último reporte ${Formats.age(freshness.age)}",
            )
            is DataFreshness.Stale -> ConnectivityItem(
                title = "Robot",
                status = StatusLabel("Estado desconocido", StatusTone.Neutral, StatusIcon.Unknown),
                detail = "Último reporte ${Formats.age(freshness.age)}" +
                    (health.reportedState?.let { " (${robotStateLabel(it)})" } ?: ""),
            )
            null -> ConnectivityItem(
                title = "Robot",
                status = StatusLabel("Estado desconocido", StatusTone.Neutral, StatusIcon.Unknown),
                detail = "Sin reportes",
            )
        }
        val ingest = when (val status = health.ingest) {
            IngestStatus.NoDataYet -> ConnectivityItem(
                "Ingesta",
                StatusLabel("Sin datos aún", StatusTone.Neutral, StatusIcon.NoData),
                null,
            )
            is IngestStatus.Receiving -> ConnectivityItem(
                "Ingesta",
                StatusLabel("Recibiendo", StatusTone.Operational, StatusIcon.Valid),
                "Último frame ${Formats.age(status.sinceLast)}",
            )
            is IngestStatus.Interrupted -> ConnectivityItem(
                "Ingesta",
                StatusLabel("Interrumpida", StatusTone.Warning, StatusIcon.Stale),
                // RF29: una ingesta interrumpida no se atribuye a robot apagado.
                "Sin frames desde ${Formats.time(status.lastReceivedAt, zone)}; no implica robot apagado",
            )
        }
        return listOf(robot, ingest, ConnectivityItem("Esta app", syncStatus(sync), syncDetail(sync)))
    }

    fun syncStatus(status: SyncStatus): StatusLabel = when (status) {
        SyncStatus.Live -> StatusLabel("En vivo", StatusTone.Operational, StatusIcon.Live)
        SyncStatus.Connecting -> StatusLabel("Conectando", StatusTone.Neutral, StatusIcon.Syncing)
        SyncStatus.Reconciling -> StatusLabel("Actualizando", StatusTone.Neutral, StatusIcon.Syncing)
        is SyncStatus.Offline -> StatusLabel("Sin conexión", StatusTone.Warning, StatusIcon.Offline)
        is SyncStatus.Error -> StatusLabel("Error de consulta", StatusTone.Critical, StatusIcon.Error)
        SyncStatus.Stopped -> StatusLabel("En pausa", StatusTone.Neutral, StatusIcon.Paused)
        SyncStatus.Idle -> StatusLabel("Inactiva", StatusTone.Neutral, StatusIcon.Paused)
    }

    private fun syncDetail(status: SyncStatus): String? = when (status) {
        is SyncStatus.Offline -> "Se muestra la última captura recibida"
        is SyncStatus.Error -> "Se reintentará automáticamente"
        SyncStatus.Reconciling -> "Recuperando datos pendientes"
        SyncStatus.Stopped -> "La app está en segundo plano"
        else -> null
    }

    // --- Eventos ----------------------------------------------------------------------------

    fun severity(raw: String): StatusLabel = when (raw.lowercase()) {
        "critical", "high", "alta", "critica", "crítica" -> StatusLabel("Crítica", StatusTone.Critical, StatusIcon.Fault)
        "warning", "medium", "media", "advertencia" -> StatusLabel("Advertencia", StatusTone.Warning, StatusIcon.Warming)
        "info", "low", "baja", "informativa" -> StatusLabel("Informativa", StatusTone.Neutral, StatusIcon.Info)
        else -> StatusLabel(raw, StatusTone.Neutral, StatusIcon.Unknown)
    }

    fun isHighSeverity(alert: Alert): Boolean = severity(alert.severity).tone == StatusTone.Critical

    /** Origen traducido (§12.5): dispositivo o regla del backend (RB24). */
    fun origin(origin: AlertOrigin): String = when (origin) {
        AlertOrigin.DeviceReported -> "Reportada por el robot"
        AlertOrigin.BackendRule -> "Regla del servidor"
        is AlertOrigin.Unknown -> origin.raw
    }

    /** Estado de procesamiento (RB21). Un acuse no demuestra que la condición física desapareció. */
    fun state(state: AlertState): StatusLabel = when (state) {
        AlertState.Open -> StatusLabel("Abierta", StatusTone.Warning, StatusIcon.Warming)
        AlertState.Acknowledged -> StatusLabel("Atendida", StatusTone.Neutral, StatusIcon.Info)
        AlertState.Resolved -> StatusLabel("Resuelta", StatusTone.Operational, StatusIcon.Valid)
        is AlertState.Unknown -> StatusLabel(state.raw, StatusTone.Neutral, StatusIcon.Unknown)
    }

    fun alertTitle(type: String): String = when (type.lowercase()) {
        "calibration_pending" -> "Calibración pendiente"
        "gas_signal_high" -> "Señal de gas elevada"
        "sensor_fault" -> "Falla de sensor"
        "ingest_interrupted" -> "Ingesta interrumpida"
        "battery_low", "low_battery" -> "Batería baja"
        "battery_critical" -> "Batería crítica"
        "station_skipped" -> "Estación omitida"
        "mission_stopped" -> "Misión detenida localmente"
        "edge_risk" -> "Riesgo de borde"
        "connectivity_lost", "offline" -> "Conexión perdida"
        "temperature_high" -> "Temperatura elevada"
        "humidity_high" -> "Humedad elevada"
        "system_fault" -> "Falla del sistema"
        "fall_detected" -> "Caída detectada"
        "flame_detected" -> "Flama detectada"
        "smoke_detected" -> "Humo detectado"
        "obstacle_blocked" -> "Obstáculo bloqueante"
        // Tipos que reporta el firmware del robot y el simulador del equipo (database-test.py).
        "gas_mq6" -> "Gas LP detectado (MQ-6)"
        "gas_mq4" -> "Gas metano detectado (MQ-4)"
        "gas_mq7" -> "Monóxido de carbono detectado (MQ-7)"
        "gas_emergency_hold" -> "Robot detenido por emergencia de gas"
        "robot_flipped" -> "Robot volcado"
        "robot_trapped" -> "Robot atascado"
        "cliff_front" -> "Desnivel al frente"
        "cliff_rear" -> "Desnivel detrás"
        "cliff_front_escape" -> "Maniobra de escape de desnivel frontal"
        "cliff_rear_escape" -> "Maniobra de escape de desnivel trasero"
        "sensor_dht_fault", "dht_fault" -> "Falla del sensor de temperatura y humedad (DHT22)"
        "sensor_error" -> "Error de sensor"
        "ultrasonic_evasion" -> "Evasión de obstáculo"
        else -> translateUnknownType(type)
    }

    /**
     * Respaldo para tipos que todavía no tienen texto propio: traduce palabra por palabra en lugar de mostrar el
     * identificador en inglés. Lo que no está en el diccionario se conserva tal cual.
     */
    private fun translateUnknownType(type: String): String {
        val words = type.lowercase().split('_', '-', ' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return "Alerta"
        return words.joinToString(" ") { TYPE_WORDS[it] ?: it }.replaceFirstChar { it.uppercase() }
    }

    private val TYPE_WORDS = mapOf(
        "gas" to "gas", "robot" to "robot", "sensor" to "sensor", "battery" to "batería", "low" to "baja",
        "high" to "alta", "critical" to "crítica", "fault" to "falla", "error" to "error", "failure" to "falla",
        "front" to "frontal", "rear" to "trasero", "left" to "izquierdo", "right" to "derecho", "lateral" to "lateral",
        "floor" to "piso", "cliff" to "desnivel", "obstacle" to "obstáculo", "detected" to "detectado",
        "lost" to "perdida", "stopped" to "detenido", "stuck" to "atascado", "trapped" to "atascado",
        "flipped" to "volcado", "escape" to "escape", "emergency" to "emergencia", "hold" to "detención",
        "temperature" to "temperatura", "humidity" to "humedad", "motor" to "motor", "motors" to "motores",
        "wheel" to "rueda", "connection" to "conexión", "connectivity" to "conectividad", "offline" to "sin conexión",
        "timeout" to "tiempo agotado", "mission" to "misión", "station" to "estación", "skipped" to "omitida",
        "calibration" to "calibración", "pending" to "pendiente", "signal" to "señal", "smoke" to "humo",
        "flame" to "flama", "fire" to "fuego", "fall" to "caída", "edge" to "borde", "risk" to "riesgo",
        "system" to "sistema", "ultrasonic" to "ultrasónico", "evasion" to "evasión", "ir" to "infrarrojo",
        "leak" to "fuga", "warning" to "advertencia", "alert" to "alerta",
    )

    // --- Misiones ---------------------------------------------------------------------------

    fun missionStatus(status: MissionStatus): StatusLabel = when (status) {
        MissionStatus.Running -> StatusLabel("En curso", StatusTone.Operational, StatusIcon.Live)
        MissionStatus.Completed -> StatusLabel("Completada", StatusTone.Neutral, StatusIcon.Valid)
        MissionStatus.Aborted -> StatusLabel("Detenida", StatusTone.Warning, StatusIcon.Paused)
        is MissionStatus.Unknown -> StatusLabel(status.raw, StatusTone.Neutral, StatusIcon.Unknown)
    }

    fun stationOutcome(outcome: StationOutcome, zone: ZoneId): Pair<StatusLabel, String?> = when (outcome) {
        is StationOutcome.Visited -> StatusLabel("Visitada", StatusTone.Operational, StatusIcon.Valid) to
            outcome.at?.let { Formats.time(it, zone) }
        StationOutcome.Pending -> StatusLabel("Pendiente", StatusTone.Neutral, StatusIcon.Syncing) to null
        is StationOutcome.Omitted -> StatusLabel("Omitida", StatusTone.Warning, StatusIcon.Warming) to outcome.cause
    }

    /** "3 de 5" estaciones visitadas, o "Pendiente" si el backend todavía no reporta estaciones. */
    fun stationProgress(mission: MissionSummary): String {
        val planned = mission.plannedStations ?: return "Pendiente"
        return "${mission.visitedStations} de $planned"
    }

    fun missionDuration(mission: MissionSummary, now: Instant): String {
        val end = mission.endedAt ?: now
        return Formats.duration(java.time.Duration.between(mission.startedAt, end).toKotlinDuration())
    }

    // --- Errores ----------------------------------------------------------------------------

    /** Mensaje para la persona usuaria; nunca incluye payloads ni detalles internos (RNF19). */
    fun errorMessage(error: Throwable): String = when (error) {
        is AccessDeniedException -> "No tienes permiso para ver esta información o tu sesión venció."
        is NotInScopeException -> "Esta información no pertenece al domicilio seleccionado."
        is DataAccessException -> "No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo."
        is ContractViolationException -> "El servidor respondió con datos que la app no reconoce."
        is IllegalArgumentException -> error.message ?: "Revisa los datos ingresados."
        else -> "Ocurrió un error inesperado. Inténtalo de nuevo."
    }

    /** Mensaje al fallar el cierre de una alerta (ADR-011): distingue el permiso de rol técnico. */
    fun resolveErrorMessage(error: Throwable): String = when (error) {
        is AccessDeniedException -> "Solo un técnico del domicilio puede resolver alertas, o tu sesión venció."
        is NotInScopeException -> "Abre V.I.S.O.R. y elige el domicilio para resolver la alerta."
        else -> errorMessage(error)
    }

    /** `true` si el error es de permisos: la interfaz ofrece volver al selector de domicilio (§12.5). */
    fun isPermissionError(error: Throwable): Boolean = error is AccessDeniedException || error is NotInScopeException
}
