package com.robotcentinela.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Escala de espacios en múltiplos de 4 (§12.3). */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/**
 * Geometría (§12.3) con bordes sutilmente suavizados para mayor amistosidad visual:
 * tarjetas 16 dp, controles e insumos 12 dp, hojas modales 24 dp, objetivos táctiles de 48 dp o más.
 */
object CentinelaShapes {
    val card = RoundedCornerShape(16.dp)
    val control = RoundedCornerShape(12.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val pill = RoundedCornerShape(50)
    val minTouchTarget = 48.dp
    val borderWidth = 1.dp
    val focusWidth = 2.dp
}

@Composable
private fun animateCentinelaColors(target: CentinelaColors): CentinelaColors {
    val spec = tween<Color>(durationMillis = 280)
    val background by animateColorAsState(target.background, spec, label = "bg")
    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val surfaceMuted by animateColorAsState(target.surfaceMuted, spec, label = "surfaceMuted")
    val textPrimary by animateColorAsState(target.textPrimary, spec, label = "textPrimary")
    val textSecondary by animateColorAsState(target.textSecondary, spec, label = "textSecondary")
    val primary by animateColorAsState(target.primary, spec, label = "primary")
    val onPrimary by animateColorAsState(target.onPrimary, spec, label = "onPrimary")
    val secondary by animateColorAsState(target.secondary, spec, label = "secondary")
    val border by animateColorAsState(target.border, spec, label = "border")
    val focus by animateColorAsState(target.focus, spec, label = "focus")
    val operational by animateColorAsState(target.operational, spec, label = "operational")
    val warning by animateColorAsState(target.warning, spec, label = "warning")
    val critical by animateColorAsState(target.critical, spec, label = "critical")
    val onOperational by animateColorAsState(target.onOperational, spec, label = "onOperational")
    val onWarning by animateColorAsState(target.onWarning, spec, label = "onWarning")
    val onCritical by animateColorAsState(target.onCritical, spec, label = "onCritical")
    val alert by animateColorAsState(target.alert, spec, label = "alert")
    val onAlert by animateColorAsState(target.onAlert, spec, label = "onAlert")
    val criticalText by animateColorAsState(target.criticalText, spec, label = "criticalText")
    val statusNeutral by animateColorAsState(target.statusNeutral, spec, label = "statusNeutral")
    val onStatusNeutral by animateColorAsState(target.onStatusNeutral, spec, label = "onStatusNeutral")
    val chrome by animateColorAsState(target.chrome, spec, label = "chrome")
    val onChrome by animateColorAsState(target.onChrome, spec, label = "onChrome")

    return target.copy(
        background = background,
        surface = surface,
        surfaceMuted = surfaceMuted,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        border = border,
        focus = focus,
        operational = operational,
        warning = warning,
        critical = critical,
        onOperational = onOperational,
        onWarning = onWarning,
        onCritical = onCritical,
        alert = alert,
        onAlert = onAlert,
        criticalText = criticalText,
        statusNeutral = statusNeutral,
        onStatusNeutral = onStatusNeutral,
        chrome = chrome,
        onChrome = onChrome,
    )
}

private fun CentinelaColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = if (isDark) surfaceMuted else statusNeutral,
        onPrimaryContainer = textPrimary,
        secondary = secondary,
        onSecondary = if (isDark) background else textPrimary,
        secondaryContainer = if (isDark) border else statusNeutral,
        onSecondaryContainer = textPrimary,
        tertiary = warning,
        onTertiary = onWarning,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceMuted,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        outline = border,
        outlineVariant = border,
        error = critical,
        onError = onCritical,
        errorContainer = critical,
        onErrorContainer = onCritical,
    )
}

@Composable
fun CentinelaTheme(
    preference: ThemePreference = ThemePreference.System,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        ThemePreference.System -> isSystemInDarkTheme()
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    val targetColors = if (dark) DarkCentinelaColors else LightCentinelaColors
    val colors = animateCentinelaColors(targetColors)
    CompositionLocalProvider(LocalCentinelaColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = CentinelaTypography,
            shapes = Shapes(
                extraSmall = CentinelaShapes.control,
                small = CentinelaShapes.control,
                medium = CentinelaShapes.card,
                large = CentinelaShapes.card,
                extraLarge = CentinelaShapes.sheet,
            ),
            content = content,
        )
    }
}

/** Acceso a los tokens: `CentinelaTheme.colors.operational`. */
object CentinelaTheme {
    val colors: CentinelaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCentinelaColors.current
}
