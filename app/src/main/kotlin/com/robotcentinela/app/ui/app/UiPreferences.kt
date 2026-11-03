package com.robotcentinela.app.ui.app

import android.content.Context
import com.robotcentinela.app.ui.notifications.CriticalAlertPreferences
import com.robotcentinela.app.ui.theme.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferencias locales de la interfaz (solo en este teléfono).
 *
 * - Tema elegido explícitamente (HU12 CDA 1).
 * - Preferencias de señalización de alerta crítica (sonido, vibración y flash).
 * - Último correo con el que se inició sesión, para conservarlo en el formulario (§12.5) y saludar.
 *
 * No se guardan contraseñas, tokens ni datos del domicilio.
 */
class UiPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("centinela_ui", Context.MODE_PRIVATE)

    private val themeState = MutableStateFlow(
        runCatching { ThemePreference.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemePreference.System),
    )
    val theme: StateFlow<ThemePreference> = themeState.asStateFlow()

    private val criticalPrefsState = MutableStateFlow(
        CriticalAlertPreferences(
            soundEnabled = prefs.getBoolean(KEY_CRITICAL_SOUND, true),
            vibrationEnabled = prefs.getBoolean(KEY_CRITICAL_VIBRATION, true),
            flashEnabled = prefs.getBoolean(KEY_CRITICAL_FLASH, true),
        ),
    )
    val criticalAlertPreferences: StateFlow<CriticalAlertPreferences> = criticalPrefsState.asStateFlow()

    fun setTheme(value: ThemePreference) {
        prefs.edit().putString(KEY_THEME, value.name).apply()
        themeState.value = value
    }

    fun setCriticalSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CRITICAL_SOUND, enabled).apply()
        criticalPrefsState.value = criticalPrefsState.value.copy(soundEnabled = enabled)
    }

    fun setCriticalVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CRITICAL_VIBRATION, enabled).apply()
        criticalPrefsState.value = criticalPrefsState.value.copy(vibrationEnabled = enabled)
    }

    fun setCriticalFlashEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CRITICAL_FLASH, enabled).apply()
        criticalPrefsState.value = criticalPrefsState.value.copy(flashEnabled = enabled)
    }

    var lastEmail: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_EMAIL, value).apply()
        }

    /** Nombre para el saludo, derivado de la parte local del correo ("daniel.morton" -> "Daniel Morton"). */
    fun displayName(): String? = lastEmail.substringBefore('@').takeIf { it.isNotBlank() }
        ?.split('.', '_', '-')
        ?.filter { it.isNotBlank() }
        ?.joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_EMAIL = "last_email"
        const val KEY_CRITICAL_SOUND = "critical_sound_enabled"
        const val KEY_CRITICAL_VIBRATION = "critical_vibration_enabled"
        const val KEY_CRITICAL_FLASH = "critical_flash_enabled"
    }
}
