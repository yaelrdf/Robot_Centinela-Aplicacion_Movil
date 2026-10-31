package com.robotcentinela.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.robotcentinela.app.ui.components.FutureFeatureNotice
import com.robotcentinela.app.ui.components.ScreenTitle
import com.robotcentinela.app.ui.components.SectionCard
import com.robotcentinela.app.ui.main.LiveUiState
import com.robotcentinela.app.ui.theme.Spacing

/**
 * Pestaña Mapa. El mapa asistido 2D no se incluye en esta versión (lo implementará el equipo de IoT):
 * se muestra la leyenda "No disponible / Implementación futura" en lugar del plano, la pose y la ruta.
 */
@Composable
fun MapScreen(live: LiveUiState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        ScreenTitle("Mapa", live.robot?.let { "Robot ${it.publicCode}" })
        SectionCard(title = "Mapa asistido 2D") {
            FutureFeatureNotice()
        }
    }
}
