package com.robotcentinela.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing

/**
 * Leyenda de una función que esta versión no incluye. Hoy la usa el mapa asistido 2D, cuya
 * implementación queda para una versión futura (equipo de IoT): en vez de dibujar un plano o una
 * trayectoria, la app dice con honestidad que no está disponible.
 */
@Composable
fun FutureFeatureNotice(modifier: Modifier = Modifier) {
    val colors = CentinelaTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xl)
            .clearAndSetSemantics { contentDescription = "No disponible. Implementación futura." },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            "No disponible\nImplementación futura",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
