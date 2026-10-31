package com.robotcentinela.app.ui.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robotcentinela.app.R
import com.robotcentinela.app.shell.AppContainer
import com.robotcentinela.app.ui.auth.LoginScreen
import com.robotcentinela.app.ui.components.AppIcon
import com.robotcentinela.app.ui.homes.HomeSelectionScreen
import com.robotcentinela.app.ui.main.MainScreen
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing

/** Raíz de la interfaz: tema, dependencias y la pantalla que corresponde a la sesión y al domicilio. */
@Composable
fun CentinelaApp(containerResult: Result<AppContainer>, preferences: UiPreferences) {
    val theme by preferences.theme.collectAsStateWithLifecycle()
    CentinelaTheme(preference = theme) {
        val container = containerResult.getOrNull()
        if (container == null) {
            ConfigurationError(containerResult.exceptionOrNull()?.message ?: "Configuración incompleta")
        } else {
            AppContent(container, preferences)
        }
    }
}

@Composable
private fun AppContent(container: AppContainer, preferences: UiPreferences) {
    val dependencies = remember(container) { UiDependencies(container, preferences) }
    CompositionLocalProvider(LocalUiDependencies provides dependencies) {
        val root: RootViewModel = viewModel { RootViewModel(container) }
        val state by root.state.collectAsStateWithLifecycle()
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
            label = "rootTransition",
        ) { s ->
            when (s) {
                RootState.Connecting -> Connecting()
                RootState.NeedsLogin -> LoginScreen()
                RootState.ChoosingHome -> HomeSelectionScreen(onChosen = root::homeChosen)
                is RootState.Ready -> MainScreen(scope = s.scope, onChangeHome = root::changeHome)
            }
        }
    }
}

/** Recuperando la sesión o entrando con la cuenta de prueba. */
@Composable
private fun Connecting() {
    val colors = CentinelaTheme.colors
    Column(
        Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = colors.primary)
        Text("Conectando con el servidor…", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    }
}

/** La configuración de Supabase es inválida (por ejemplo, clave privilegiada o URL sin HTTPS, RNF08). */
@Composable
private fun ConfigurationError(message: String) {
    val colors = CentinelaTheme.colors
    Column(
        Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
    ) {
        AppIcon(R.drawable.ic_error, size = 40.dp, tint = colors.critical)
        Text("No se pudo configurar la conexión", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        Text(
            "Revisa supabase.url y supabase.publishableKey en local.properties (ver local.properties.example).",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}
