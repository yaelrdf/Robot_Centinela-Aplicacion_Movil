package com.robotcentinela.app.shell

import com.robotcentinela.app.environmentalmonitoring.ports.CapabilitiesPort
import com.robotcentinela.app.environmentalmonitoring.ports.MetricAggregatePort
import com.robotcentinela.app.homemapping.ports.FloorPlanPort
import com.robotcentinela.app.identity.adapters.supabase.SupabaseAuthAdapter
import com.robotcentinela.app.identity.adapters.supabase.SupabaseHomeScopeAdapter
import com.robotcentinela.app.identity.ports.AuthPort
import com.robotcentinela.app.identity.ports.HomeScopePort
import com.robotcentinela.app.incidents.adapters.supabase.SupabaseAlertQueryAdapter
import com.robotcentinela.app.incidents.ports.AlertQueryPort
import com.robotcentinela.app.missions.ports.MissionQueryPort
import com.robotcentinela.app.shell.supabase.SupabaseClientFactory
import com.robotcentinela.app.shell.supabase.SupabaseConfig
import com.robotcentinela.app.telemetry.adapters.supabase.SupabaseTelemetryChangeNotifier
import com.robotcentinela.app.telemetry.adapters.supabase.SupabaseTelemetryQueryAdapter
import com.robotcentinela.app.telemetry.ports.TelemetryChangeNotifier
import com.robotcentinela.app.telemetry.ports.TelemetryQueryPort
import kotlinx.coroutines.CoroutineScope

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Contenedor de instancias de adaptadores de infraestructura que implementan los puertos secundarios del núcleo.
 * Los puertos con contratos aún no definidos en backend se mantienen opcionales (nulos) de forma explícita.
 */
class Adapters(
    val auth: AuthPort,
    val homeScope: HomeScopePort,
    val telemetryQuery: TelemetryQueryPort,
    val telemetryNotifier: TelemetryChangeNotifier,
    val alertQuery: AlertQueryPort,
    // [PENDIENTE DE CONTRATO REAL]: Capacidades, agregados métricos, planos cartográficos y misiones
    // aún no cuentan con endpoints o tablas publicadas en el backend/robot. Se mantienen como null
    // para indicar explícitamente a la UI el estado "pendiente de backend" sin usar datos inventados.
    val capabilities: CapabilitiesPort? = null,
    val aggregates: MetricAggregatePort? = null,
    val floorPlans: FloorPlanPort? = null,
    val missions: MissionQueryPort? = null,
) {
    companion object {

        /** Adaptadores de Supabase conectados al backend real. Capacidades, agregados, planos y misiones esperan definición de contrato. */
        fun supabase(config: SupabaseConfig, scope: CoroutineScope): Adapters {
            val client = SupabaseClientFactory.create(config)
            return Adapters(
                auth = SupabaseAuthAdapter(client, scope),
                homeScope = SupabaseHomeScopeAdapter(client),
                telemetryQuery = SupabaseTelemetryQueryAdapter(client),
                telemetryNotifier = SupabaseTelemetryChangeNotifier(client),
                alertQuery = SupabaseAlertQueryAdapter(client),
            )
        }
    }
}
