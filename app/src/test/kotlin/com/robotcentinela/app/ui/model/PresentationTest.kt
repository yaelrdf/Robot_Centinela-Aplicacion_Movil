package com.robotcentinela.app.ui.model

import com.robotcentinela.app.environmentalmonitoring.application.ChannelStatus
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelCapability
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelKey
import com.robotcentinela.app.environmentalmonitoring.domain.DataFreshness
import com.robotcentinela.app.environmentalmonitoring.domain.ReadingInterpretation
import com.robotcentinela.app.environmentalmonitoring.domain.ReadingQuality
import com.robotcentinela.app.environmentalmonitoring.domain.ReadingValue
import com.robotcentinela.app.robothealth.domain.BatteryInterpretation
import com.robotcentinela.app.shared.UtcInterval
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Reglas de presentación de la interfaz (HU02, HU04, HU08, HU20, RB04, RB05). */
class PresentationTest {

    private val zone = UtcInterval.DEFAULT_ZONE
    private val mq6 = ChannelKey("mq6_raw")
    private val mq6Catalog = ChannelCapability(mq6, "gas_signal", "adc_count", "LPG", 2.seconds, enabled = true)

    private fun status(interpretation: ReadingInterpretation, capability: ChannelCapability? = null, freshness: DataFreshness? = null) =
        ChannelStatus(mq6, interpretation, freshness, null, capability)

    @Test
    fun `la señal cruda se muestra en ADC y nunca en ppm`() {
        val card = ChannelPresentation.card(status(ReadingInterpretation.RawSignal(ReadingValue.Numeric(1860.0), "adc_count"), mq6Catalog), zone)
        assertEquals("1860", card.value)
        assertEquals("ADC", card.unit)
        assertEquals("Sin calibración", card.status.text)
        assertEquals("Gas LP", card.title)
    }

    @Test
    fun `las unidades del catálogo del equipo se muestran legibles`() {
        assertEquals("ADC", ChannelPresentation.unitLabel("adc"))
        assertEquals("m", ChannelPresentation.unitLabel("meter"))
        assertNull(ChannelPresentation.unitLabel("bool"))
        val card = ChannelPresentation.card(status(ReadingInterpretation.RawSignal(ReadingValue.Numeric(1860.0), "adc"), mq6Catalog.copy(unitCode = "adc")), zone)
        assertEquals("1860", card.value)
        assertEquals("ADC", card.unit)
    }

    @Test
    fun `el IR lateral se traduce a libre u obstaculo`() {
        val key = ChannelKey("ir_lateral_left")
        assertEquals("Obstáculo lateral izquierdo", ChannelPresentation.title(key, null))
        assertEquals("Libre", ChannelPresentation.formatValue(ReadingValue.BooleanValue(true), "bool", key, null))
        assertEquals("Obstáculo", ChannelPresentation.formatValue(ReadingValue.BooleanValue(false), "bool", key, null))
    }

    @Test
    fun `sin catálogo no se atribuye un gas al sensor`() {
        val card = ChannelPresentation.card(status(ReadingInterpretation.RawSignal(ReadingValue.Numeric(1860.0), null)), zone)
        assertEquals("Sensor MQ6", card.title)
        assertNull(card.unit)
    }

    @Test
    fun `los estados sin valor dibujan raya y nunca cero`() {
        listOf(
            ReadingInterpretation.NoData to "Sin dato",
            ReadingInterpretation.NotInstalled to "No instalado",
            ReadingInterpretation.Warming to "Calentando",
            ReadingInterpretation.Fault to "Falla del sensor",
            ReadingInterpretation.OutOfRange to "Fuera de rango",
        ).forEach { (interpretation, text) ->
            val card = ChannelPresentation.card(status(interpretation), zone)
            assertNull(card.value, "Un estado sin valor no muestra número: $text")
            assertEquals(text, card.status.text)
        }
    }

    @Test
    fun `una calidad desconocida conserva el valor sin afirmar validez`() {
        val card = ChannelPresentation.card(status(ReadingInterpretation.Uncertain(ReadingValue.Numeric(0.45), "calibrating")), zone)
        assertEquals("0.45", card.value)
        assertEquals("Calidad no reconocida", card.status.text)
    }

    @Test
    fun `el dato atrasado se marca y se describe para lectores de pantalla`() {
        val card = ChannelPresentation.card(
            status(ReadingInterpretation.Measured(ReadingValue.Numeric(24.6), "celsius", ReadingQuality.Valid), freshness = DataFreshness.Stale(40.seconds, 6.seconds)),
            zone,
        )
        assertTrue(card.stale)
        assertEquals("hace 40 s", card.age)
        assertTrue(card.accessibilityText.contains("Dato atrasado"))
    }

    @Test
    fun `el piso falso se traduce a riesgo de borde`() {
        val key = ChannelKey("ir_floor_front_left")
        assertEquals("Riesgo de borde", ChannelPresentation.formatValue(ReadingValue.BooleanValue(false), null, key, null))
        assertEquals("Piso detectado", ChannelPresentation.formatValue(ReadingValue.BooleanValue(true), null, key, null))
    }

    @Test
    fun `solo voltaje muestra porcentaje no disponible`() {
        val model = OperationPresentation.battery(BatteryInterpretation.PercentageUnavailable(11.4, "voltage_only"))
        assertEquals("11.4 V", model.primary)
        assertEquals("Porcentaje no disponible", model.secondary)
        assertEquals("Solo voltaje", model.method)
    }

    @Test
    fun `una severidad desconocida se conserva como texto`() {
        assertEquals("p1", OperationPresentation.severity("p1").text)
        assertTrue(OperationPresentation.severity("critical").let { it.tone == StatusTone.Critical })
        assertFalse(OperationPresentation.severity("warning").let { it.tone == StatusTone.Critical })
    }

    @Test
    fun `los rangos son días locales inclusivos`() {
        val today = LocalDate.of(2026, 10, 15)
        assertEquals(7, DateRange.of(RangePreset.Last7Days, today).days)
        assertEquals(LocalDate.of(2026, 10, 1), DateRange.of(RangePreset.ThisMonth, today).from)
        assertFailsWith<IllegalArgumentException> { DateRange.custom(today, today.minusDays(1)) }
    }

    @Test
    fun `la edad se expresa en la unidad mayor`() {
        assertEquals("hace 2 s", Formats.age(2.seconds))
        assertEquals("hace 3 min", Formats.age(200.seconds))
        assertEquals("hace 2 h", Formats.age(7_300.seconds))
    }
}
