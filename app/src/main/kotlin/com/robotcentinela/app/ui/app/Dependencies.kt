package com.robotcentinela.app.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.robotcentinela.app.shell.AppContainer
import com.robotcentinela.app.ui.notifications.CriticalAlertGateway

/** Dependencias que la interfaz recibe de la raíz de composición. */
class UiDependencies(
    val container: AppContainer,
    val preferences: UiPreferences,
    val criticalAlertGateway: CriticalAlertGateway? = null,
)

val LocalUiDependencies = staticCompositionLocalOf<UiDependencies> {
    error("UiDependencies no está disponible: envuelve la interfaz en CentinelaApp")
}

/** Atajo para las pantallas. */
val uiDependencies: UiDependencies
    @Composable get() = LocalUiDependencies.current
