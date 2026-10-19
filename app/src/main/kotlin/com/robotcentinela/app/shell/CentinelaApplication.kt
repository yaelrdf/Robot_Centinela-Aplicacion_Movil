package com.robotcentinela.app.shell

import android.app.Application
import com.robotcentinela.app.shell.supabase.SupabaseConfig

/**
 * Punto de entrada Android. Expone el [AppContainer] a la capa de presentación.
 *
 * El contenedor se crea de forma diferida. Si `local.properties` no tiene una configuración válida de
 * Supabase, la interfaz muestra el error con su causa en lugar de arrancar sin backend.
 */
class CentinelaApplication : Application() {

    /** Contenedor, o el error de configuración que impide crearlo. */
    val containerResult: Result<AppContainer> by lazy {
        runCatching { AppContainer.supabase(SupabaseConfig.fromBuildConfig()) }
    }
}
