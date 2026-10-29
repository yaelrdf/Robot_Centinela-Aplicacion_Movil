package com.robotcentinela.app.shell

import com.robotcentinela.app.environmentalmonitoring.adapters.supabase.SupabaseCapabilitiesAdapter
import com.robotcentinela.app.environmentalmonitoring.ports.CapabilitiesPort
import com.robotcentinela.app.environmentalmonitoring.ports.MetricAggregatePort
import com.robotcentinela.app.homemapping.ports.FloorPlanPort
import com.robotcentinela.app.identity.adapters.supabase.PublicReadAuthAdapter
import com.robotcentinela.app.identity.adapters.supabase.PublicReadHomeScopeAdapter
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
    // Capacidades (tabla `channels`) y misiones (tabla `missions`) ya tienen adaptador.
    // [PENDIENTE DE CONTRATO REAL]: agregados métricos y planos cartográficos aún no cuentan con
    // tablas publicadas en el backend. Se mantienen como null para indicar explícitamente a la UI el
    // estado "pendiente de backend" sin usar datos inventados.
    val capabilities: CapabilitiesPort? = null,
    val aggregates: MetricAggregatePort? = null,
    val floorPlans: FloorPlanPort? = null,
    val missions: MissionQueryPort? = null,
) {
    companion object {

        /**
         * Adaptadores de Supabase conectados al backend real, según el esquema del equipo. Agregados
         * (cálculo en servidor) y planos (archivo del mapa en Storage) todavía esperan contrato (B8).
         *
         * Con [publicRead] (build `demo`) no hay inicio de sesión: la app lee solo con la clave pública, como
         * el rol `anon`. Solo cambian la sesión, el ámbito de domicilio y las columnas de telemetría;
         * el resto de los adaptadores y todo `:core` son los mismos.
         */
        fun supabase(config: SupabaseConfig, scope: CoroutineScope, publicRead: Boolean = false): Adapters {
            val client = SupabaseClientFactory.create(config)
            return Adapters(
                auth = if (publicRead) PublicReadAuthAdapter() else SupabaseAuthAdapter(client, scope),
                homeScope = if (publicRead) PublicReadHomeScopeAdapter(client) else SupabaseHomeScopeAdapter(client),
                telemetryQuery = SupabaseTelemetryQueryAdapter(client, publicRead),
                telemetryNotifier = SupabaseTelemetryChangeNotifier(client),
                alertQuery = SupabaseAlertQueryAdapter(client),
                capabilities = SupabaseCapabilitiesAdapter(client),
                missions = SupabaseMissionQueryAdapter(client),
            )
        }
    }
}
