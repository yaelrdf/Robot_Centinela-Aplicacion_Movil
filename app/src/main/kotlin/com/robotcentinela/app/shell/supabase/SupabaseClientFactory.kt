package com.robotcentinela.app.shell.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

/**
 * Crea el cliente de Supabase con los tres módulos que usa la app:
 * - Auth: personas (RF01).
 * - Postgrest: consultas autorizadas por RLS (RF02).
 * - Realtime: avisos de cambios confirmados (RF07).
 *
 * Storage (mapas privados, RF32) todavía no se instala: el contrato de mapas está pendiente.
 */
object SupabaseClientFactory {

    /** Los cambios aditivos del contrato v1 no rompen la app (§14.2): se ignoran claves desconocidas. */
    val json = Json { ignoreUnknownKeys = true }

    /**
     * [withAuth] = false (build `demo`) no instala Auth: el demo es siempre el rol `anon` y así no puede reutilizar
     * una sesión guardada por otro build con el mismo `applicationId` y firma, que cambiaría el rol y, por RLS,
     * dejaría la lista de domicilios vacía.
     */
    fun create(config: SupabaseConfig, withAuth: Boolean = true): SupabaseClient =
        createSupabaseClient(supabaseUrl = config.url, supabaseKey = config.publishableKey) {
            defaultSerializer = KotlinXSerializer(json)
            if (withAuth) install(Auth)
            install(Postgrest)
            install(Realtime)
        }

    /**
     * Cliente solo para escribir alertas en el demo (ADR-011). Lleva una clave con permisos de escritura que
     * únicamente existe en el APK `demo` y se revoca al terminar la demostración. No instala Auth ni Realtime.
     */
    fun createDemoWriter(url: String, writeKey: String): SupabaseClient =
        createSupabaseClient(supabaseUrl = url, supabaseKey = writeKey) {
            defaultSerializer = KotlinXSerializer(json)
            install(Postgrest)
        }
}
