package com.robotcentinela.app.incidents.domain

import com.robotcentinela.app.identity.domain.RobotId
import java.time.Instant
import java.util.UUID

/** Identificador de alerta o incidente (`alerts.id`, §13.2). */
@JvmInline
value class AlertId(val value: UUID)

/** Regla versionada que originó una alerta (`alert_rules.id`, §13.2). */
@JvmInline
value class AlertRuleId(val value: UUID)

/**
 * Estado del ciclo de vida de una alerta (RB21: "open, acknowledged y resolved").
 *
 * Un acuse humano no demuestra que la condición física haya desaparecido (RB21).
 */
sealed interface AlertState {
    val raw: String

    data object Open : AlertState {
        override val raw = "open"
    }

    data object Acknowledged : AlertState {
        override val raw = "acknowledged"
    }

    data object Resolved : AlertState {
        override val raw = "resolved"
    }

    /** Valor no documentado; se conserva para no perder información. */
    data class Unknown(override val raw: String) : AlertState

    companion object {
        fun fromRaw(raw: String): AlertState = when (raw) {
            Open.raw -> Open
            Acknowledged.raw -> Acknowledged
            Resolved.raw -> Resolved
            else -> Unknown(raw)
        }
    }
}

/**
 * Origen de una alerta (RB24, §12.5): reportada por el dispositivo o calculada por una regla del
 * backend.
 */
sealed interface AlertOrigin {
    val raw: String

    /**
     * Evento reportado por el robot, incluidas las alertas legacy de E01. No se presenta como umbral
     * de exposición certificado (RF16).
     */
    data object DeviceReported : AlertOrigin {
        override val raw = "device_reported"
    }

    /** Alerta calculada por una regla configurada en la base de datos. */
    data object BackendRule : AlertOrigin {
        override val raw = "backend_rule"
    }

    data class Unknown(override val raw: String) : AlertOrigin

    companion object {
        fun fromRaw(raw: String): AlertOrigin = when (raw) {
            DeviceReported.raw -> DeviceReported
            BackendRule.raw -> BackendRule
            else -> Unknown(raw)
        }
    }
}

/**
 * Alerta o incidente persistido (`alerts`, §13.2).
 *
 * **La app no calcula alertas.** La lógica, los umbrales y la histéresis viven en la base de datos
 * (requisito del proyecto, RB22, RB23). Una condición sostenida es un solo incidente con varias
 * ocurrencias, no una fila por frame (HU11, RB22). La app consulta el resultado; lo único que puede
 * escribir es el cierre manual de una alerta ([com.robotcentinela.app.incidents.ports.AlertCommandPort], ADR-011).
 *
 * @property type tipo de alerta. El catálogo no está documentado, así que se conserva el texto.
 * @property severity severidad. El catálogo no está documentado, así que se conserva el texto.
 * @property openedAt inicio del incidente.
 * @property lastSeenAt última ocurrencia (HU11 CDA 2).
 * @property resolvedAt cierre, si existe.
 */
data class Alert(
    val id: AlertId,
    val robotId: RobotId,
    val ruleId: AlertRuleId?,
    val type: String,
    val severity: String,
    val state: AlertState,
    val origin: AlertOrigin,
    val openedAt: Instant,
    val lastSeenAt: Instant,
    val resolvedAt: Instant?,
)
