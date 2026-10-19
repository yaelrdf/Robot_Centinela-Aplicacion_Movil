package com.robotcentinela.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.robotcentinela.app.ui.model.StatusTone

/**
 * Tokens de color "Lunar Base" (guía de interfaces §12.2). Toda la interfaz usa estos tokens; no hay
 * hexadecimales dispersos en las pantallas.
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
    val warning: Color,
    val critical: Color,
    // --- Derivados para contraste ---
    val onOperational: Color,
    val onWarning: Color,
    val onCritical: Color,
    /** "Azul grisáceo" de §12.1 para no instalado / sin calibración. */
    val statusNeutral: Color,
    val onStatusNeutral: Color,
    /** Barra de navegación y cabecera (azul navy en ambos temas, como la propuesta). */
    val chrome: Color,
    val onChrome: Color,
    /** Trama de zonas sin explorar en el mapa (§12.5). */
    val unexploredHatch: Color,
    val isDark: Boolean,
) {
    fun container(tone: StatusTone): Color = when (tone) {
        StatusTone.Operational -> operational
        StatusTone.Neutral -> statusNeutral
        StatusTone.Warning -> warning
        StatusTone.Critical -> critical
    }

    /**
     * Color de un ícono de estado dibujado directamente sobre `surface`, con al menos 3:1 (gráficos,
     * §12.2). En tema claro el verde y el azul grisáceo no alcanzan 3:1, así que se usan tonos de texto.
     */
    fun graphic(tone: StatusTone): Color = when (tone) {
        StatusTone.Operational -> if (isDark) operational else textPrimary
        StatusTone.Neutral -> textSecondary
        StatusTone.Warning -> warning
        StatusTone.Critical -> critical
    }

    fun onContainer(tone: StatusTone): Color = when (tone) {
        StatusTone.Operational -> onOperational
        StatusTone.Neutral -> onStatusNeutral
        StatusTone.Warning -> onWarning
        StatusTone.Critical -> onCritical
    }
}

val LightCentinelaColors = CentinelaColors(
    background = Color(0xFFEEEEEE),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFCCCCCC),
    textPrimary = Color(0xFF222831),
    textSecondary = Color(0xFF2D4059),
    primary = Color(0xFF2D4059),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFFF5722),
    border = Color(0xFFCCCCCC),
    focus = Color(0xFFFF5722),
    operational = Color(0xFFB5D0A2),
    warning = Color(0xFFFF5722),
    critical = Color(0xFFA23443),
    onOperational = Color(0xFF222831),
    onWarning = Color(0xFF222831),
    onCritical = Color(0xFFFFFFFF),
    statusNeutral = Color(0xFFDCE3EA),
    onStatusNeutral = Color(0xFF2D4059),
    chrome = Color(0xFF2D4059),
    onChrome = Color(0xFFFFFFFF),
    unexploredHatch = Color(0xFFB8C0C8),
    isDark = false,
)

val DarkCentinelaColors = CentinelaColors(
    background = Color(0xFF222831),
    surface = Color(0xFF2D4059),
    surfaceMuted = Color(0xFF1D2A3A),
    textPrimary = Color(0xFFEEEEEE),
    textSecondary = Color(0xFFA0B0C0),
    primary = Color(0xFFFF5722),
    onPrimary = Color(0xFF222831),
    secondary = Color(0xFF8A9BA8),
    border = Color(0xFF3E5675),
    focus = Color(0xFFFF5722),
    operational = Color(0xFF99CBAA),
    warning = Color(0xFFFF5722),
    critical = Color(0xFFF0A4B0),
    onOperational = Color(0xFF222831),
    onWarning = Color(0xFF222831),
    onCritical = Color(0xFF222831),
    statusNeutral = Color(0xFF3E5675),
    onStatusNeutral = Color(0xFFEEEEEE),
    chrome = Color(0xFF2D4059),
    onChrome = Color(0xFFEEEEEE),
    unexploredHatch = Color(0xFF4A5F7A),
    isDark = true,
)

val LocalCentinelaColors = staticCompositionLocalOf { LightCentinelaColors }
