package com.robotcentinela.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.robotcentinela.app.R

/**
 * Poppins (guía §12.3) en una variante con dígitos tabulares generada para este proyecto: Poppins no
 * incluye la característica OpenType `tnum`, y la guía exige números de ancho fijo para que valores,
 * fechas y horas no se desplacen en tiempo real. La variante iguala el avance de 0-9 (OFL 1.1,
 * app/OFL-Poppins.txt). Además se pide `tnum` por si la fuente se reemplaza por una que la soporte.
 */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

/**
 * Questrial (Manual de identidad, §4.2): titulares y logotipo. Solo existe en peso regular, así que los
 * titulares no piden negrita (Compose la sintetizaría y deformaría las formas circulares de la fuente).
 */
val Questrial = FontFamily(Font(R.font.questrial_regular, FontWeight.Normal))

private const val TABULAR = "tnum"

private fun headline(size: Int, line: Int) = TextStyle(
    fontFamily = Questrial,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = line.sp,
)

private fun style(size: Int, weight: FontWeight, line: Int) = TextStyle(
    fontFamily = Poppins,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontFeatureSettings = TABULAR,
)

/**
 * Escala móvil de §12.3: título de página 24, secciones 18-20, texto 16, datos numéricos 28-32,
 * ayudas 13-14. El cuerpo nunca baja de 16 para encajar componentes.
 */
val CentinelaTypography = Typography(
    displaySmall = headline(32, 40),
    headlineMedium = headline(28, 36),
    headlineSmall = headline(26, 34),
    titleLarge = headline(22, 30),
    titleMedium = style(18, FontWeight.SemiBold, 26),
    titleSmall = style(16, FontWeight.SemiBold, 24),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(16, FontWeight.Normal, 24),
    bodySmall = style(14, FontWeight.Normal, 20),
    labelLarge = style(16, FontWeight.Medium, 24),
    labelMedium = style(14, FontWeight.Medium, 20),
    labelSmall = style(13, FontWeight.Medium, 18),
)

/** Estilos propios fuera de la escala de Material. */
object CentinelaText {
    /** Lectura numérica de las tarjetas de métrica (28-32 en móvil). */
    val metricValue = style(30, FontWeight.Medium, 36)

    /** Unidad junto a la lectura. */
    val metricUnit = style(14, FontWeight.Normal, 20)
}
