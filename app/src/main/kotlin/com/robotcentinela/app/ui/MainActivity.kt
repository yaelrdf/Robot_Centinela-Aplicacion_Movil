package com.robotcentinela.app.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.robotcentinela.app.shell.CentinelaApplication
import com.robotcentinela.app.ui.app.CentinelaApp
import com.robotcentinela.app.ui.app.UiPreferences

/**
 * Única actividad. Dibuja de borde a borde respetando las áreas seguras (§12.6). La cabecera y la
 * barra inferior son azul navy en ambos temas, así que los íconos del sistema van en claro.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Pantalla de inicio de la marca (Theme.Centinela.Starting); al terminar pasa a Theme.Centinela.
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val application = application as CentinelaApplication
        val preferences = UiPreferences(this)
        setContent {
            CentinelaApp(containerResult = application.containerResult, preferences = preferences)
        }
    }
}
