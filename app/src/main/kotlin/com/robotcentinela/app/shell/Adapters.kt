package com.robotcentinela.app.shell

import com.robotcentinela.app.environmentalmonitoring.adapters.supabase.SupabaseCapabilitiesAdapter
import com.robotcentinela.app.environmentalmonitoring.ports.CapabilitiesPort
import com.robotcentinela.app.environmentalmonitoring.ports.MetricAggregatePort
import com.robotcentinela.app.homemapping.ports.FloorPlanPort
import com.robotcentinela.app.identity.adapters.supabase.SupabaseAuthAdapter
import com.robotcentinela.app.identity.adapters.supabase.SupabaseHomeScopeAdapter
import com.robotcentinela.app.identity.ports.AuthPort
import com.robotcentinela.app.identity.ports.HomeScopePort
import com.robotcentinela.app.incidents.adapters.supabase.SupabaseAlertQueryAdapter
import com.robotcentinela.app.incidents.ports.AlertQueryPort
import com.robotcentinela.app.missions.adapters.supabase.SupabaseMissionQueryAdapter
import com.robotcentinela.app.missions.ports.MissionQueryPort
import com.robotcentinela.app.shell.supabase.SupabaseClientFactory
import com.robotcentinela.app.shell.supabase.SupabaseConfig
import com.robotcentinela.app.telemetry.adapters.supabase.SupabaseTelemetryChangeNotifier
import com.robotcentinela.app.telemetry.adapters.supabase.SupabaseTelemetryQueryAdapter
import com.robotcentinela.app.telemetry.ports.TelemetryChangeNotifier
import com.robotcentinela.app.telemetry.ports.TelemetryQueryPort
import kotlinx.coroutines.CoroutineScope

/**
 * Implementaciones de los puertos de `:core`.
 *
 * Los puertos cuyo contrato está pendiente son opcionales (`null`): la interfaz muestra entonces un
 * estado explícito de "pendiente de backend" en lugar de inventar datos (docs/pendientes.md).
 */
class Adapters(
    val auth: AuthPort,
    val homeScope: HomeScopePort,
    val telemetryQuery: TelemetryQueryPort,
    val telemetryNotifier: TelemetryChangeNotifier,
    val alertQuery: AlertQueryPort,
    val capabilities: CapabilitiesPort? = null,
    val aggregates: MetricAggregatePort? = null,
    val floorPlans: FloorPlanPort? = null,
    val missions: MissionQueryPort? = null,
) {
    companion object {

        /**
         * Adaptadores de Supabase según el esquema del equipo. Agregados (cálculo en servidor) y planos
         * (archivo del mapa en Storage) todavía no tienen contrato (B8).
         */
        fun supabase(config: SupabaseConfig, scope: CoroutineScope): Adapters {
            val client = SupabaseClientFactory.create(config)
            return Adapters(
                auth = SupabaseAuthAdapter(client, scope),
                homeScope = SupabaseHomeScopeAdapter(client),
                telemetryQuery = SupabaseTelemetryQueryAdapter(client),
                telemetryNotifier = SupabaseTelemetryChangeNotifier(client),
                alertQuery = SupabaseAlertQueryAdapter(client),
                capabilities = SupabaseCapabilitiesAdapter(client),
                missions = SupabaseMissionQueryAdapter(client),
            )
        }
    }
}
