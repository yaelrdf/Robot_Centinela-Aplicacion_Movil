package com.robotcentinela.app.incidents.adapters.supabase

import com.robotcentinela.app.incidents.domain.AlertId
import com.robotcentinela.app.incidents.ports.AlertCommandPort
import com.robotcentinela.app.shell.supabase.SupabaseResources
import com.robotcentinela.app.shell.supabase.SupabaseResources.Columns
import com.robotcentinela.app.shell.supabase.supabaseCall
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns as PostgrestColumns
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Cierra alertas con la función `resolve_alert` de la base de datos (ADR-011).
 *
 * La función comprueba que la persona autenticada sea técnico activo del domicilio del robot y deja
 * `state = 'resolved'` y `resolved_at = now()`. Con RLS la app no puede actualizar `alerts` directamente.
 */
class SupabaseAlertCommandAdapter(private val supabase: SupabaseClient) : AlertCommandPort {

    override suspend fun resolve(alertId: AlertId): Boolean = supabaseCall("resolver alerta") {
        supabase.postgrest
            .rpc(SupabaseResources.RPC_RESOLVE_ALERT, buildJsonObject { put(PARAM_ALERT_ID, alertId.value.toString()) })
            .decodeAs<Boolean>()
    }

    private companion object {
        const val PARAM_ALERT_ID = "p_alert_id"
    }
}

/**
 * Cierre de alertas del build `demo` (ADR-011, excepción temporal).
 *
 * El demo no tiene sesión, así que no puede llamar a `resolve_alert`. Con una clave con permisos de
 * escritura incrustada solo en ese APK (`BuildConfig.DEMO_WRITE_KEY`, que se revoca al terminar la demo) se
 * actualiza la fila directamente. [supabase] es un cliente aparte que solo usa este adaptador; las lecturas
 * del demo siguen con la clave pública.
 */
class SupabaseDemoAlertCommandAdapter(private val supabase: SupabaseClient) : AlertCommandPort {

    override suspend fun resolve(alertId: AlertId): Boolean = supabaseCall("resolver alerta") {
        supabase.from(SupabaseResources.ALERTS)
            .update(
                buildJsonObject {
                    put(Columns.STATE, "resolved")
                    // Igual que el script de simulación (database-test.py, resolve()).
                    put(Columns.RESOLVED_AT, Instant.now().toString())
                    put(Columns.LAST_SEEN_AT, Instant.now().toString())
                },
            ) {
                select(PostgrestColumns.raw(Columns.ID))
                filter {
                    eq(Columns.ID, alertId.value.toString())
                    isIn(Columns.STATE, listOf("open", "acknowledged"))
                }
            }
            .decodeList<ChangedRowDto>()
            .isNotEmpty()
    }
}

@Serializable
internal data class ChangedRowDto(val id: String)
