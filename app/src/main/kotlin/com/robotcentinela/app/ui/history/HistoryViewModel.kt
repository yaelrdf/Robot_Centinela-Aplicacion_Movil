package com.robotcentinela.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelCapability
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelKey
import com.robotcentinela.app.environmentalmonitoring.domain.MetricAggregate
import com.robotcentinela.app.environmentalmonitoring.domain.ReadingInterpretation
import com.robotcentinela.app.environmentalmonitoring.domain.ReadingInterpreter
import com.robotcentinela.app.environmentalmonitoring.domain.ReadingValue
import com.robotcentinela.app.identity.domain.Robot
import com.robotcentinela.app.shared.PageRequest
import com.robotcentinela.app.shell.AppContainer
import com.robotcentinela.app.telemetry.domain.FrameRecord
import com.robotcentinela.app.telemetry.domain.TelemetryFrame
import com.robotcentinela.app.ui.model.ChannelPresentation
import com.robotcentinela.app.ui.model.ChartPoint
import com.robotcentinela.app.ui.model.DateRange
import com.robotcentinela.app.ui.model.Formats
import com.robotcentinela.app.ui.model.OperationPresentation
import com.robotcentinela.app.ui.model.RangePreset
import com.robotcentinela.app.ui.model.StatusLabel
import com.robotcentinela.app.ui.model.StatusTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

data class ChannelOption(val key: ChannelKey, val label: String, val capability: ChannelCapability?)

/** Fila de la tabla de historial: hora, valor con su calidad y posición estimada (§12.5). */
data class HistoryRow(val time: String, val value: String, val quality: StatusLabel, val position: String)

/** Estadísticas del intervalo (HU07 CDA 3). */
data class HistoryStats(val min: String, val max: String, val avg: String, val valid: Long, val invalid: Long, val source: String)

/** Alertas registradas en el periodo, por severidad (mockup: dona "Alertas registradas"). */
data class AlertBreakdown(val critical: Int, val warning: Int, val other: Int) {
    val total: Int get() = critical + warning + other
}

data class HistoryUiState(
    val range: DateRange,
    val today: LocalDate,
    val channels: List<ChannelOption>,
    val channel: ChannelOption,
    val unit: String? = null,
    val decimals: Int = 1,
    val chartLoading: Boolean = true,
    val chart: List<ChartPoint> = emptyList(),
    val chartNote: String? = null,
    val stats: HistoryStats? = null,
    val selectedIndex: Int? = null,
    val selection: List<String> = emptyList(),
    val chartError: Throwable? = null,
    val alertsLoading: Boolean = true,
    val alerts: AlertBreakdown? = null,
    val alertsError: Throwable? = null,
)

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Modelo de vista para la visualización gráfica y tabular del historial de telemetría.
 *
 * [ESTADO DE FUENTES DE DATOS]:
 * - Frames de telemetría: Conectados a la persistencia real de frames en backend.
 * - [PENDIENTE DE CONTRATO REAL]: Agregados estadísticos del servidor (MetricAggregatePort) y
 *   catálogo formal de capacidades (CapabilitiesPort). Si no están presentes, la aplicación calcula
 *   la interpretación básica de las lecturas registradas.
 */
class HistoryViewModel(
    private val container: AppContainer,
    private val robot: Robot,
    private val zone: ZoneId,
) : ViewModel() {

    private val today = LocalDate.now(container.clock.withZone(zone))
    private val mutable = MutableStateFlow(
        HistoryUiState(
            range = DateRange.of(RangePreset.ThisMonth, today),
            today = today,
            channels = DEFAULT_CHANNELS.map { ChannelOption(it, ChannelPresentation.title(it, null), null) },
            channel = ChannelOption(DEFAULT_CHANNELS.first(), ChannelPresentation.title(DEFAULT_CHANNELS.first(), null), null),
        ),
    )
    val state: StateFlow<HistoryUiState> = mutable.asStateFlow()

    private var chartJob: Job? = null
    private var tableJob: Job? = null
    private var chartFrames: List<TelemetryFrame> = emptyList()
    private var aggregates: List<MetricAggregate> = emptyList()

    init {
        viewModelScope.launch {
            val caps = container.queryChannelCapabilities?.let { query -> runCatching { query(robot.id) }.getOrNull() }
            if (!caps.isNullOrEmpty()) {
                val options = caps.filter { it.unitCode != "boolean" && it.metricType != "floor_presence" }
                    .map { ChannelOption(it.channelKey, ChannelPresentation.title(it.channelKey, it), it) }
                if (options.isNotEmpty()) {
                    val current = options.firstOrNull { it.key == mutable.value.channel.key } ?: options.first()
                    mutable.update { it.copy(channels = options, channel = current) }
                }
            }
            reloadAll()
        }
    }

    fun setRange(range: DateRange) {
        mutable.update { it.copy(range = range, selectedIndex = null, selection = emptyList()) }
        reloadAll()
    }

    fun setChannel(option: ChannelOption) {
        mutable.update { it.copy(channel = option, selectedIndex = null, selection = emptyList()) }
        loadChart()
    }

    fun retry() = reloadAll()

    fun select(index: Int?) = mutable.update { it.copy(selectedIndex = index, selection = describe(index)) }

    private fun reloadAll() {
        loadChart()
        loadAlerts()
    }

    private var alertsJob: Job? = null

    /** Cuenta las alertas persistidas del periodo por severidad (HU07, RF16). Las calcula el backend; aquí solo se cuentan. */
    private fun loadAlerts() {
        alertsJob?.cancel()
        val range = mutable.value.range
        mutable.update { it.copy(alertsLoading = true, alertsError = null) }
        alertsJob = viewModelScope.launch {
            try {
                val alerts = container.queryAlertHistory(robot.id, PageRequest(ALERT_LIMIT), range.from, range.to)
                val tones = alerts.map { OperationPresentation.severity(it.severity).tone }
                mutable.update {
                    it.copy(
                        alertsLoading = false,
                        alerts = AlertBreakdown(
                            critical = tones.count { t -> t == StatusTone.Critical },
                            warning = tones.count { t -> t == StatusTone.Warning },
                            other = tones.count { t -> t != StatusTone.Critical && t != StatusTone.Warning },
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(alertsLoading = false, alertsError = e) }
            }
        }
    }

    /** Intervalo de agregación según la longitud del rango. */
    private fun bucketFor(range: DateRange): Duration = when {
        range.days <= 1 -> 30.minutes
        range.days <= 7 -> 2.hours
        else -> 6.hours
    }

    private fun loadChart() {
        chartJob?.cancel()
        val s = mutable.value
        val unitCode = s.channel.capability?.unitCode
        mutable.update {
            it.copy(
                chartLoading = true,
                chartError = null,
                unit = ChannelPresentation.unitLabel(unitCode),
                decimals = DECIMALS[unitCode] ?: 1,
            )
        }
        chartJob = viewModelScope.launch {
            try {
                val queryAggregates = container.queryMetricAggregates
                if (queryAggregates != null) {
                    val bucket = bucketFor(s.range)
                    aggregates = queryAggregates(robot.id, s.range.from, s.range.to, bucket, s.channel.key)
                    chartFrames = emptyList()
                    val points = aggregates.map { ChartPoint(it.bucketStart.toEpochMilli(), it.avg, it.min, it.max) }
                    val valid = aggregates.filter { it.countValid > 0 }
                    val totalValid = valid.sumOf { it.countValid }
                    mutable.update {
                        it.copy(
                            chartLoading = false,
                            chart = points,
                            chartNote = "Promedio por intervalos de ${Formats.duration(bucket)} con banda mínimo–máximo. Los huecos son periodos sin muestras válidas.",
                            stats = if (totalValid == 0L) {
                                null
                            } else {
                                HistoryStats(
                                    min = fmt(valid.mapNotNull { a -> a.min }.minOrNull(), it.decimals, it.unit),
                                    max = fmt(valid.mapNotNull { a -> a.max }.maxOrNull(), it.decimals, it.unit),
                                    avg = fmt(valid.sumOf { a -> (a.avg ?: 0.0) * a.countValid } / totalValid, it.decimals, it.unit),
                                    valid = totalValid,
                                    invalid = aggregates.sumOf { a -> a.countInvalid },
                                    source = "Calculado por el servidor",
                                )
                            },
                        )
                    }
                } else {
                    // Sin contrato de agregados: se grafica la muestra cruda visible, marcando sus huecos.
                    val frames = container.queryTelemetryHistory(robot.id, s.range.from, s.range.to, PageRequest(RAW_LIMIT))
                        .filterIsInstance<FrameRecord.Interpreted>()
                        .map { it.frame }
                    chartFrames = frames
                    aggregates = emptyList()
                    val points = mutableListOf<ChartPoint>()
                    var previous: Instant? = null
                    for (f in frames) {
                        val at = f.capturedAt ?: f.receivedAt
                        val last = previous
                        // Un corte real (más de 3 periodos sin frames) se dibuja como hueco (§12.5).
                        if (last != null && at.toEpochMilli() - last.toEpochMilli() > GAP_MS) {
                            points += ChartPoint(last.toEpochMilli() + 1, null)
                        }
                        points += ChartPoint(at.toEpochMilli(), numericValue(f, s.channel))
                        previous = at
                    }
                    val values = points.mapNotNull { it.y }
                    mutable.update {
                        it.copy(
                            chartLoading = false,
                            chart = points,
                            chartNote = "Muestras crudas (hasta $RAW_LIMIT). Las estadísticas del servidor están pendientes de contrato.",
                            stats = if (values.isEmpty()) {
                                null
                            } else {
                                HistoryStats(
                                    fmt(values.min(), it.decimals, it.unit),
                                    fmt(values.max(), it.decimals, it.unit),
                                    fmt(values.average(), it.decimals, it.unit),
                                    values.size.toLong(),
                                    (points.size - values.size).toLong(),
                                    "Calculado en el teléfono sobre la muestra visible",
                                )
                            },
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(chartLoading = false, chartError = e) }
            }
        }
    }

    /** Valor graficable: solo lecturas medidas o señal cruda; los demás estados son huecos, nunca cero. */
    private fun numericValue(frame: TelemetryFrame, channel: ChannelOption): Double? {
        val reading = frame.readings.firstOrNull { it.channelKey == channel.key } ?: return null
        val value = when (val interpretation = ReadingInterpreter.interpret(reading, channel.capability)) {
            is ReadingInterpretation.Measured -> interpretation.value
            is ReadingInterpretation.RawSignal -> interpretation.value
            else -> null
        }
        return (value as? ReadingValue.Numeric)?.value
    }

    private fun toRow(frame: TelemetryFrame, channel: ChannelOption): HistoryRow {
        val at = frame.capturedAt?.takeIf { frame.reliableCapturedAt != null }
        val time = at?.let { Formats.dateTimeSeconds(it, zone) } ?: "≈ ${Formats.dateTimeSeconds(frame.receivedAt, zone)}"
        val reading = frame.readings.firstOrNull { it.channelKey == channel.key }
        if (reading == null) {
            return HistoryRow(time, "—", ChannelPresentation.status(ReadingInterpretation.NoData), position(frame))
        }
        val interpretation = ReadingInterpreter.interpret(reading, channel.capability)
        val status = ChannelPresentation.status(interpretation)
        val unitCode = channel.capability?.unitCode
        val value = when (interpretation) {
            is ReadingInterpretation.Measured,
            is ReadingInterpretation.RawSignal,
            is ReadingInterpretation.Uncertain,
            -> ChannelPresentation.formatValue(reading.value, unitCode, channel.key, channel.capability)
                ?.let { v -> listOfNotNull(v, ChannelPresentation.unitLabel(unitCode)).joinToString(" ") } ?: "—"
            else -> "—"
        }
        return HistoryRow(time, value, status, position(frame))
    }

    private fun position(frame: TelemetryFrame): String = frame.pose?.let {
        "${Formats.number(it.xM, 2)}, ${Formats.number(it.yM, 2)} m" +
            (it.sigmaPositionM?.let { s -> " ±${Formats.number(s, 2)}" } ?: " (incertidumbre desconocida)")
    } ?: "Sin posición"

    private fun describe(index: Int?): List<String> {
        if (index == null) return emptyList()
        val s = mutable.value
        aggregates.getOrNull(index)?.let { a ->
            return listOf(
                "${s.channel.label} · ${Formats.dateTime(a.bucketStart, zone)} (intervalo de ${Formats.duration(a.bucketSize)})",
                if (a.countValid == 0L) "Sin muestras válidas en el intervalo" else
                    "Promedio ${fmt(a.avg, s.decimals, s.unit)} · mínimo ${fmt(a.min, s.decimals, s.unit)} · máximo ${fmt(a.max, s.decimals, s.unit)}",
                "Muestras válidas ${a.countValid} · no válidas ${a.countInvalid}",
            )
        }
        val point = s.chart.getOrNull(index) ?: return emptyList()
        val frame = chartFrames.firstOrNull { (it.capturedAt ?: it.receivedAt).toEpochMilli() == point.xMs } ?: return emptyList()
        val row = toRow(frame, s.channel)
        val calibration = frame.readings.firstOrNull { it.channelKey == s.channel.key }?.calibrationId
        return listOf(
            "${s.channel.label} · ${row.time}",
            "Valor ${row.value} · ${row.quality.text}",
            "Posición estimada ${row.position}",
            if (calibration != null) "Con calibración registrada" else "Sin calibración aplicada",
        )
    }

    private fun fmt(value: Double?, decimals: Int, unit: String?): String =
        value?.let { listOfNotNull(Formats.number(it, decimals), unit).joinToString(" ") } ?: "—"

    fun errorText(error: Throwable) = OperationPresentation.errorMessage(error)

    private companion object {
        val DEFAULT_CHANNELS = listOf("mq6_raw", "mq4_raw", "temperature", "humidity").map(::ChannelKey)
        val DECIMALS = mapOf("adc_count" to 0, "celsius" to 1, "percent" to 1, "m" to 2, "ppm" to 0)
        const val ALERT_LIMIT = 500
        const val RAW_LIMIT = 500
        const val GAP_MS = 6_000L
    }
}
