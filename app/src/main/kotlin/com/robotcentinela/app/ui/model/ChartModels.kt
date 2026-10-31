package com.robotcentinela.app.ui.model

/**
 * Punto de una serie temporal. `y` nulo = hueco real (sin muestras válidas): se deja vacío y nunca se
 * une con trazo continuo (§12.5, RF10).
 *
 * @property low mínimo del intervalo (banda), si existe.
 * @property high máximo del intervalo (banda), si existe.
 */
data class ChartPoint(val xMs: Long, val y: Double?, val low: Double? = null, val high: Double? = null)
