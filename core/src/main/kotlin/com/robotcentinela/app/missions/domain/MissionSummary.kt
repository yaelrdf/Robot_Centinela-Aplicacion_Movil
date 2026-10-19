package com.robotcentinela.app.missions.domain

import com.robotcentinela.app.homemapping.domain.MapVersionId
import com.robotcentinela.app.homemapping.domain.StationId
import com.robotcentinela.app.identity.domain.RobotId
import java.time.Instant

/**
 * Estado de una misión (`missions.status`).
 *
 * **PROVISIONAL.** El catálogo no está documentado; se conserva el texto en [Unknown].
 */
sealed interface MissionStatus {
    val raw: String

    data object Running : MissionStatus {
        override val raw = "running"
    }

    data object Completed : MissionStatus {
        override val raw = "completed"
    }

    data object Aborted : MissionStatus {
        override val raw = "aborted"
    }

    data class Unknown(override val raw: String) : MissionStatus

    companion object {
        fun fromRaw(raw: String): MissionStatus = when (raw) {
            Running.raw -> Running
            Completed.raw -> Completed
            Aborted.raw -> Aborted
            else -> Unknown(raw)
        }
    }
}

/** Resultado de una estación prevista en la misión (HU06 CDA 2, RF33). */
sealed interface StationOutcome {

    /** La estación se visitó y se tomó la muestra. */
    data class Visited(val at: Instant?) : StationOutcome

    /** Todavía no se visita (misión en curso). */
    data object Pending : StationOutcome

    /**
     * La estación se omitió. La causa es obligatoria: una estación omitida sin causa no permite evaluar
     * la cobertura honestamente (HU06 CDA 2).
     */
    data class Omitted(val cause: String) : StationOutcome {
        init {
            require(cause.isNotBlank()) { "Una estación omitida debe indicar su causa" }
        }
    }
}

/** Estación prevista con su resultado. */
data class StationVisit(val stationId: StationId, val name: String, val outcome: StationOutcome)

/**
 * Consumo de energía de una misión (HU08 CDA 2).
 *
 * @property method cómo se obtuvo el consumo (`measured` o `estimated`). Se muestra siempre: un
 * consumo sin método no sustenta el ensayo de autonomía.
 */
data class MissionEnergy(
    val startVoltageV: Double?,
    val endVoltageV: Double?,
    val consumedWh: Double?,
    val method: String,
)

/**
 * Resumen de una misión (RF14, RF33).
 *
 * **PROVISIONAL.** El contrato del resumen de misión está pendiente (docs/pendientes.md, "Misiones").
 * Este modelo recoge lo que piden HU05, HU06 y HU08 y se ajustará al contrato publicado.
 *
 * @property name nombre visible de la misión (mockup: "Patrullaje 03 - Regular"); `null` si no tiene.
 * @property purpose propósito declarado al crearla (mockup "Nueva misión"); `null` si no tiene.
 * @property mapVersionId versión exacta de mapa usada; la trayectoria se dibuja sobre ella (HU05).
 */
data class MissionSummary(
    val id: MissionId,
    val robotId: RobotId,
    val name: String?,
    val purpose: String?,
    val mapVersionId: MapVersionId?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val status: MissionStatus,
    val stations: List<StationVisit>,
    val energy: MissionEnergy?,
) {
    val plannedStations: Int get() = stations.size
    val visitedStations: Int get() = stations.count { it.outcome is StationOutcome.Visited }
    val omittedStations: Int get() = stations.count { it.outcome is StationOutcome.Omitted }
}
