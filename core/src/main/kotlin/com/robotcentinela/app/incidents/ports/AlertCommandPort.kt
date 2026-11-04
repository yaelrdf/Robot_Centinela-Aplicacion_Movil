package com.robotcentinela.app.incidents.ports

import com.robotcentinela.app.incidents.domain.AlertId

/**
 * Puerto de salida para cerrar alertas e incidentes.
 *
 * Es la única escritura que hace la app sobre `alerts` (ADR-011). La autorización (rol técnico del
 * domicilio) la aplica el servidor, no la app (RNF09).
 */
interface AlertCommandPort {

    /**
     * Marca la alerta como resuelta (`state = 'resolved'`, `resolved_at = now()`).
     *
     * Un cierre manual no demuestra que la condición física haya desaparecido (RB21).
     *
     * @return `true` si la alerta cambió de estado; `false` si ya estaba resuelta.
     * @throws com.robotcentinela.app.shared.AccessDeniedException si la persona no puede resolver alertas.
     */
    suspend fun resolve(alertId: AlertId): Boolean
}
