package com.robotcentinela.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.robotcentinela.app.ui.model.StatusTone

/**
 * Paleta de marca V.I.S.O.R. (Manual de identidad corporativa, §4.1 y tabla de estados de la
 * iconografía). Son los valores oficiales; los tokens de [CentinelaColors] los combinan por tema.
 */
object VisorPalette {
    val BlackBlue = Color(0xFF041839)
    val OffWhite = Color(0xFFEEEEEE)
    val Navy = Color(0xFF2D4059)
    val BrightOrange = Color(0xFFFF5722)

    /** Dato validado. */
    val Valid = Color(0xFFB5D0A2)

    /** Alerta: lecturas de riesgo. */
    val Alert = Color(0xFFF7B32D)

    /** Crítico: lecturas críticas. */
    val Critical = Color(0xFFC00000)
}

/**
 * Tokens de color de la interfaz (guía de interfaces §12.2 sobre la paleta del Manual de identidad
 * V.I.S.O.R.). Toda la interfaz usa estos tokens; no hay hexadecimales dispersos en las pantallas.
 *
 * Los tokens `on*` y `status*` son DERIVADOS (no están en la tabla 12.2): se agregan para cumplir el
 * contraste que exige la misma guía (4.5:1 en texto, 3:1 en gráficos). En tema claro, `operational`
 * (#B5D0A2) y `warning` (#FF5722) no alcanzan 4.5:1 como texto sobre `surface` (1.7:1 y 3.2:1), así que
 * los estados se dibujan como insignias rellenas con texto oscuro encima (8.8:1 y 4.7:1). Ver
 * docs/ui/interfaz-movil.md.
 */
@Immutable
data class CentinelaColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val border: Color,
    val focus: Color,
    val operational: Color,
    /** Naranja de marca: notificaciones de riesgo y acento (avisos, "atrasado", acciones). */
    val warning: Color,
    /** Rojo crítico de la marca (#C00000) como relleno de insignias; el texto va en [onCritical]. */
    val critical: Color,
    // --- Derivados para contraste ---
    val onOperational: Color,
    val onWarning: Color,
    val onCritical: Color,
    /** Amarillo de alerta de la marca (lecturas de riesgo), relleno de insignias. */
    val alert: Color,
    val onAlert: Color,
    /** Rojo crítico para texto e íconos sobre `surface`: en oscuro se aclara para llegar a 4.5:1. */
    val criticalText: Color,
    /** "Azul grisáceo" de §12.1 para no instalado / sin calibración. */
    val statusNeutral: Color,
    val onStatusNeutral: Color,
    /** Barra de navegación y cabecera (azul navy en ambos temas, como la propuesta). */
    val chrome: Color,
    val onChrome: Color,
    val isDark: Boolean,
) {
    fun container(tone: StatusTone): Color = when (tone) {
        StatusTone.Operational -> operational
        StatusTone.Neutral -> statusNeutral
        StatusTone.Warning -> alert
        StatusTone.Critical -> critical
    }

    /**
     * Color de un ícono de estado dibujado directamente sobre `surface`, con al menos 3:1 (gráficos,
     * §12.2). En tema claro el verde y el azul grisáceo no alcanzan 3:1, así que se usan tonos de texto.
     */
    fun graphic(tone: StatusTone): Color = when (tone) {
        StatusTone.Operational -> if (isDark) operational else textPrimary
        StatusTone.Neutral -> textSecondary
        // El amarillo no llega a 3:1 sobre blanco: en claro se usa el naranja de marca.
        StatusTone.Warning -> if (isDark) alert else warning
        StatusTone.Critical -> criticalText
    }

    fun onContainer(tone: StatusTone): Color = when (tone) {
        StatusTone.Operational -> onOperational
        StatusTone.Neutral -> onStatusNeutral
        StatusTone.Warning -> onAlert
        StatusTone.Critical -> onCritical
    }
}

val LightCentinelaColors = CentinelaColors(
    background = VisorPalette.OffWhite,
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFCCCCCC),
    textPrimary = VisorPalette.BlackBlue,
    textSecondary = VisorPalette.Navy,
    primary = VisorPalette.Navy,
    onPrimary = Color(0xFFFFFFFF),
    secondary = VisorPalette.BrightOrange,
    border = Color(0xFFCCCCCC),
    focus = VisorPalette.BrightOrange,
    operational = VisorPalette.Valid,
    warning = VisorPalette.BrightOrange,
    critical = VisorPalette.Critical,
    onOperational = VisorPalette.BlackBlue,
    onWarning = VisorPalette.BlackBlue,
    onCritical = Color(0xFFFFFFFF),
    alert = VisorPalette.Alert,
    onAlert = VisorPalette.BlackBlue,
    criticalText = VisorPalette.Critical,
    statusNeutral = Color(0xFFDCE3EA),
    onStatusNeutral = VisorPalette.Navy,
    chrome = VisorPalette.Navy,
    onChrome = Color(0xFFFFFFFF),
    isDark = false,
)

val DarkCentinelaColors = CentinelaColors(
    background = VisorPalette.BlackBlue,
    surface = VisorPalette.Navy,
    surfaceMuted = Color(0xFF142846),
    textPrimary = VisorPalette.OffWhite,
    textSecondary = Color(0xFFA0B0C0),
    primary = VisorPalette.BrightOrange,
    onPrimary = VisorPalette.BlackBlue,
    secondary = Color(0xFF8A9BA8),
    border = Color(0xFF3E5675),
    focus = VisorPalette.BrightOrange,
    operational = VisorPalette.Valid,
    warning = VisorPalette.BrightOrange,
    critical = VisorPalette.Critical,
    onOperational = VisorPalette.BlackBlue,
    onWarning = VisorPalette.BlackBlue,
    onCritical = Color(0xFFFFFFFF),
    alert = VisorPalette.Alert,
    onAlert = VisorPalette.BlackBlue,
    criticalText = Color(0xFFF0A4B0),
    statusNeutral = Color(0xFF3E5675),
    onStatusNeutral = VisorPalette.OffWhite,
    chrome = VisorPalette.Navy,
    onChrome = VisorPalette.OffWhite,
    isDark = true,
)

val LocalCentinelaColors = staticCompositionLocalOf { LightCentinelaColors }
