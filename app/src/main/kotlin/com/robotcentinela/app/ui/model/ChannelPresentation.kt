package com.robotcentinela.app.ui.model

import com.robotcentinela.app.environmentalmonitoring.application.ChannelStatus
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelCapability
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelKey
import com.robotcentinela.app.environmentalmonitoring.domain.DataFreshness
import com.robotcentinela.app.environmentalmonitoring.domain.ReadingInterpretation
import com.robotcentinela.app.environmentalmonitoring.domain.ReadingQuality
import com.robotcentinela.app.environmentalmonitoring.domain.ReadingValue
import java.time.ZoneId

/**
 * Datos de una tarjeta de métrica (guía §12.5): nombre, valor, unidad, calidad visible, hora de
 * captura, frescura y enlace al detalle.
 *
 * @property value texto del valor; `null` = no hay valor que mostrar y la tarjeta dibuja una raya
 * con la leyenda de [status] ("Sin dato", "No instalado", "Calentando"...). Nunca cero (RB04).
 * @property capturedAt hora de captura; con prefijo "≈" si es la hora de recepción (§7.4).
 * @property age edad del dato ("hace 2 s").
 * @property stale el dato superó el límite de frescura ("Atrasado", HU04 CDA 1).
 */
data class MetricCardModel(
    val channelKey: ChannelKey,
    val title: String,
    val subtitle: String?,
    val value: String?,
    val unit: String?,
    val status: StatusLabel,
    val capturedAt: String?,
    val age: String?,
    val stale: Boolean,
    val timeApproximate: Boolean,
) {
    /** Descripción para lectores de pantalla: todo lo que la tarjeta comunica, en una frase. */
    val accessibilityText: String
        get() = buildString {
            append(title).append(": ")
            append(value?.let { v -> listOfNotNull(v, unit).joinToString(" ") } ?: "sin valor")
            append(". ").append(status.text).append('.')
            if (stale) append(" Dato atrasado.")
            capturedAt?.let { append(" Capturado a las ").append(it.removePrefix("≈ ")) }
            if (timeApproximate) append(", hora aproximada de recepción")
            age?.let { append(", ").append(it) }
            append('.')
        }
}

/** Reglas de presentación de canales: nombres, unidades y estados (HU02, HU04, HU20). */
object ChannelPresentation {

    /** Canales de la fila principal del resumen, en el orden de la propuesta de interfaz. */
    val PRIMARY: List<ChannelKey> = listOf("mq6_raw", "mq4_raw", "temperature", "humidity").map(::ChannelKey)

    private val knownTitles = mapOf(
        "temperature" to "Temperatura",
        "humidity" to "Humedad",
        "front_distance_m" to "Distancia frontal",
        "ir_floor_front_left" to "Piso delantero izquierdo",
        "ir_floor_front_right" to "Piso delantero derecho",
        "ir_floor_rear_left" to "Piso trasero izquierdo",
        "ir_floor_rear_right" to "Piso trasero derecho",
        "ir_lateral_left" to "Obstáculo lateral izquierdo",
        "ir_lateral_right" to "Obstáculo lateral derecho",
    )

    private val gasNames = mapOf(
        "LPG" to "Gas LP",
        "CH4" to "Metano",
        "CO" to "Monóxido de carbono",
        "H2" to "Hidrógeno",
    )

    private val unitLabels = mapOf(
        "adc_count" to "ADC",
        "adc" to "ADC",
        "celsius" to "°C",
        "degC" to "°C",
        "percent" to "%",
        "pct" to "%",
        "m" to "m",
        "meter" to "m",
        "ppm" to "ppm",
        "V" to "V",
    )

    private val unitDecimals = mapOf(
        "adc_count" to 0,
        "adc" to 0,
        "celsius" to 1,
        "degC" to 1,
        "percent" to 1,
        "pct" to 1,
        "m" to 2,
        "meter" to 2,
        "ppm" to 0,
    )

    /** "mq6_raw" -> "MQ6". */
    private fun sensorModel(key: ChannelKey): String? =
        Regex("^(mq\\d+)_raw$").find(key.value)?.groupValues?.get(1)?.uppercase()

    /**
     * Nombre del canal. El gas solo se nombra si el catálogo lo declara; sin catálogo se nombra el
     * sensor, sin atribuirle un gas (§13: registrar un gas objetivo no certifica capacidad química).
     */
    fun title(key: ChannelKey, capability: ChannelCapability?): String {
        capability?.gasTarget?.let { return gasNames[it] ?: it }
        knownTitles[key.value]?.let { return it }
        sensorModel(key)?.let { return "Sensor $it" }
        return key.value.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /** Subtítulo: el sensor que mide, si el título es el gas. */
    fun subtitle(key: ChannelKey, capability: ChannelCapability?): String? {
        val model = sensorModel(key) ?: return null
        return if (capability?.gasTarget != null) "Sensor $model" else "Gas objetivo pendiente de catálogo"
    }

    fun unitLabel(unitCode: String?): String? = when (unitCode) {
        null, "boolean", "bool" -> null
        else -> unitLabels[unitCode] ?: unitCode
    }

    private fun isFloorSensor(key: ChannelKey, capability: ChannelCapability?) =
        capability?.metricType == "floor_presence" || key.value.startsWith("ir_floor_")

    /** IR lateral (FC51): `true` = libre, `false` = obstáculo (docs/data-model.md del repositorio principal). */
    private fun isLateralSensor(key: ChannelKey) = key.value.startsWith("ir_lateral_")

    /** Texto de un valor en su unidad. Un booleano de piso se traduce a su significado (§7.2). */
    fun formatValue(value: ReadingValue, unitCode: String?, key: ChannelKey, capability: ChannelCapability?): String? =
        when (value) {
            is ReadingValue.Numeric -> Formats.number(value.value, unitDecimals[unitCode] ?: 2)
            is ReadingValue.BooleanValue -> when {
                isFloorSensor(key, capability) -> if (value.value) "Piso detectado" else "Riesgo de borde"
                isLateralSensor(key) -> if (value.value) "Libre" else "Obstáculo"
                else -> if (value.value) "Sí" else "No"
            }
            ReadingValue.Absent -> null
        }

    /** Estado visible de una lectura (HU02 CDA 3, HU20 CDA 1). */
    fun status(interpretation: ReadingInterpretation): StatusLabel = when (interpretation) {
        is ReadingInterpretation.Measured -> if (interpretation.quality == ReadingQuality.Estimated) {
            StatusLabel("Dato estimado", StatusTone.Neutral, StatusIcon.Info)
        } else {
            StatusLabel("Dato válido", StatusTone.Operational, StatusIcon.Valid)
        }
        is ReadingInterpretation.RawSignal -> StatusLabel("Sin calibración", StatusTone.Neutral, StatusIcon.Uncalibrated)
        ReadingInterpretation.NotInstalled -> StatusLabel("No instalado", StatusTone.Neutral, StatusIcon.NotInstalled)
        ReadingInterpretation.Warming -> StatusLabel("Calentando", StatusTone.Warning, StatusIcon.Warming)
        ReadingInterpretation.Fault -> StatusLabel("Falla del sensor", StatusTone.Critical, StatusIcon.Fault)
        ReadingInterpretation.OutOfRange -> StatusLabel("Fuera de rango", StatusTone.Warning, StatusIcon.OutOfRange)
        ReadingInterpretation.NoData -> StatusLabel("Sin dato", StatusTone.Neutral, StatusIcon.NoData)
        is ReadingInterpretation.Uncertain -> StatusLabel("Calidad no reconocida", StatusTone.Warning, StatusIcon.Unknown)
    }

    /** Explicación breve de cada estado, para el detalle del canal (HU20: no confundir silencio con aire seguro). */
    fun explanation(interpretation: ReadingInterpretation): String = when (interpretation) {
        is ReadingInterpretation.Measured ->
            "Lectura válida según el método del canal. Un dato válido no significa ausencia de gases."
        is ReadingInterpretation.RawSignal ->
            "Señal cruda del sensor sin calibración. Es una señal relativa: no es una concentración en ppm."
        ReadingInterpretation.NotInstalled -> "Este sensor no está instalado en el robot. No se mide nada en este canal."
        ReadingInterpretation.Warming -> "El sensor se está calentando. No se muestran valores hasta que se estabilice."
        ReadingInterpretation.Fault -> "El sensor reporta una falla. La ausencia de lectura no significa aire seguro."
        ReadingInterpretation.OutOfRange -> "La lectura está fuera del rango del sensor o saturada; no se muestra el valor."
        ReadingInterpretation.NoData -> "No hay valor para este canal. Sin dato no equivale a cero ni a aire seguro."
        is ReadingInterpretation.Uncertain ->
            "El dispositivo reportó una calidad que la app no reconoce (\"${interpretation.rawQuality}\"). " +
                "El valor se muestra, pero no se afirma su validez."
    }

    /** Construye la tarjeta de un canal (HU02, HU04). */
    fun card(status: ChannelStatus, zone: ZoneId): MetricCardModel {
        val key = status.channelKey
        val capability = status.capability
        val interpretation = status.interpretation
        val (value, unitCode) = when (interpretation) {
            is ReadingInterpretation.Measured -> interpretation.value to interpretation.unitCode
            is ReadingInterpretation.RawSignal -> interpretation.value to interpretation.unitCode
            is ReadingInterpretation.Uncertain -> interpretation.value to capability?.unitCode
            else -> ReadingValue.Absent to null
        }
        val stamp = status.sample?.stamp
        val approximate = stamp?.timeIsApproximate == true
        val freshness = status.freshness
        return MetricCardModel(
            channelKey = key,
            title = title(key, capability),
            subtitle = subtitle(key, capability),
            value = formatValue(value, unitCode, key, capability),
            unit = if (value is ReadingValue.Numeric) unitLabel(unitCode) else null,
            status = status(interpretation),
            capturedAt = stamp?.let { (if (approximate) "≈ " else "") + Formats.time(it.referenceInstant, zone) },
            age = freshness?.age?.let(Formats::age),
            stale = freshness is DataFreshness.Stale,
            timeApproximate = approximate,
        )
    }
}
