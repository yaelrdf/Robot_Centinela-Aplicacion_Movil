package com.robotcentinela.app.telemetry.adapters.supabase

import com.robotcentinela.app.environmentalmonitoring.domain.ChannelKey
import com.robotcentinela.app.environmentalmonitoring.domain.ReadingQuality
import com.robotcentinela.app.environmentalmonitoring.domain.ReadingValue
import com.robotcentinela.app.shell.supabase.SupabaseClientFactory
import com.robotcentinela.app.telemetry.domain.FrameRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El esquema normalizado de Supabase (telemetry_frames + tablas hijas) se reconstruye en el frame v1
 * y se interpreta con las mismas reglas del contrato.
 */
class NormalizedFrameAssemblerTest {

    private fun embedded(): JsonObject {
        val text = checkNotNull(javaClass.getResource("/fixtures/telemetry/frame_supabase_embebido.json")).readText()
        return SupabaseClientFactory.json.parseToJsonElement(text).jsonObject
    }

    private fun interpret(row: JsonObject) =
        assertIs<FrameRecord.Interpreted>(TelemetryFrameMapper.fromRow(NormalizedFrameAssembler.toContractRow(row))).frame

    @Test
    fun `reconstruye el frame de JSON_estructura desde las tablas normalizadas`() {
        val frame = interpret(embedded())

        assertEquals(UUID.fromString("9f225860-42f2-4cd0-8c3a-e4e970395307"), frame.id.value)
        assertEquals(NormalizedFrameAssembler.STORED_SCHEMA_VERSION, frame.schemaVersion)
        assertEquals(UUID.fromString("bf8d0f7d-f739-4529-9a8a-5975f5b26960"), frame.deviceId.value)
        assertEquals(UUID.fromString("b503cb0b-3866-491b-aebc-e164ea73d9aa"), frame.bootId.value)
        assertEquals(1843, frame.sequence)
        assertEquals(Instant.parse("2026-10-11T15:24:12Z"), frame.capturedAt)
        assertEquals(Instant.parse("2026-10-11T15:24:13.250Z"), frame.receivedAt)
        assertEquals(30, frame.streamVersion.value)
        assertEquals(UUID.fromString("ecb3df81-477f-4e72-92c6-8b38f856cdb5"), frame.mapVersionId?.value)
        assertEquals(2.15, frame.pose?.xM)
        assertEquals(0.08, frame.pose?.sigmaPositionM)
        assertEquals("moving", frame.robotState?.raw)
        assertEquals(true, frame.motorsActive)
        assertEquals("0.2.0", frame.firmwareVersion)
        assertEquals("voltage_only", frame.battery?.quality)
        assertNull(frame.battery?.socPct)

        assertEquals(10, frame.readings.size)
        val byKey = frame.readings.associateBy { it.channelKey }
        assertEquals(ReadingValue.Numeric(1860.0), byKey.getValue(ChannelKey("mq6_raw")).value)
        assertEquals(ReadingQuality.fromRaw("uncalibrated"), byKey.getValue(ChannelKey("mq6_raw")).quality)
        // Sensor no instalado: ausencia, nunca cero (RB04).
        assertEquals(ReadingValue.Absent, byKey.getValue(ChannelKey("mq7_raw")).value)
        assertEquals(ReadingValue.BooleanValue(true), byKey.getValue(ChannelKey("ir_floor_rear_right")).value)
    }

    @Test
    fun `acepta relaciones a uno devueltas como arreglo`() {
        val row = embedded()
        val asArrays = JsonObject(
            row + listOf("frame_poses", "battery_readings", "robot_states", "device_sessions")
                .associateWith { JsonArray(listOf(row.getValue(it))) },
        )
        assertEquals(2.15, interpret(asArrays).pose?.xM)
    }

    @Test
    fun `sin pose ni bateria el frame no inventa posicion ni carga`() {
        val row = JsonObject(embedded() + mapOf("frame_poses" to JsonNull, "battery_readings" to JsonArray(emptyList())))
        val frame = interpret(row)
        assertNull(frame.pose)
        assertNull(frame.mapVersionId)
        assertNull(frame.battery)
    }

    @Test
    fun `sin la sesion de arranque el frame se rechaza sin bloquear el flujo`() {
        val row = JsonObject(embedded() - "device_sessions")
        assertIs<FrameRecord.Rejected>(TelemetryFrameMapper.fromRow(NormalizedFrameAssembler.toContractRow(row)))
    }

    /** Payload original del dispositivo, con los identificadores que RLS oculta en `device_sessions` y `devices`. */
    private fun payload(bootId: String) = JsonObject(
        mapOf(
            "schema_version" to JsonPrimitive("1.1"),
            "boot_id" to JsonPrimitive(bootId),
            "device_id" to JsonPrimitive("bf8d0f7d-f739-4529-9a8a-5975f5b26960"),
            "firmware_version" to JsonPrimitive("0.2.0"),
        ),
    )

    @Test
    fun `sin sesion de arranque visible toma los identificadores del payload original`() {
        val row = JsonObject(embedded() - "device_sessions" + ("raw_payload" to payload("b503cb0b-3866-491b-aebc-e164ea73d9aa")))
        val frame = interpret(row)
        assertEquals(UUID.fromString("b503cb0b-3866-491b-aebc-e164ea73d9aa"), frame.bootId.value)
        assertEquals(UUID.fromString("bf8d0f7d-f739-4529-9a8a-5975f5b26960"), frame.deviceId.value)
        assertEquals("0.2.0", frame.firmwareVersion)
        assertEquals("1.1", frame.schemaVersion)
    }

    @Test
    fun `con sesion de arranque visible no se usa el payload original`() {
        val row = JsonObject(embedded() + ("raw_payload" to payload("11111111-1111-4111-8111-111111111111")))
        assertEquals(UUID.fromString("b503cb0b-3866-491b-aebc-e164ea73d9aa"), interpret(row).bootId.value)
    }

    @Test
    fun `la consulta normal no pide columnas que no estan en el esquema compartido`() {
        assertTrue("raw_payload" !in NormalizedFrameAssembler.SELECT)
        assertTrue("raw_payload" in NormalizedFrameAssembler.SELECT_PUBLIC_READ)
    }
}
