package com.robotcentinela.app.shell

import com.robotcentinela.app.BuildConfig

/**
 * Cuenta de PRUEBA de Supabase Auth, solo para builds debug.
 *
 * Supabase solo entrega datos a usuarios autenticados con membresía activa en el domicilio (RLS). La
 * pantalla de inicio de sesión se hace al final del proyecto, por indicación del equipo; mientras
 * tanto la app entra sola con esta cuenta, que se configura en `local.properties` (no se versiona):
 * `centinela.devEmail` y `centinela.devPassword`. En release los campos están vacíos.
 */
class DevAccount private constructor(val email: String, val password: String) {

    /** No incluye la contraseña, para que no termine en logs (RNF08). */
    override fun toString(): String = "DevAccount(email=$email, password=***)"

    companion object {
        /** La cuenta configurada, o `null` si falta alguno de los dos valores. */
        fun fromBuildConfig(): DevAccount? {
            val email = BuildConfig.DEV_EMAIL.trim()
            val password = BuildConfig.DEV_PASSWORD
            return if (email.isEmpty() || password.isEmpty()) null else DevAccount(email, password)
        }
    }
}
