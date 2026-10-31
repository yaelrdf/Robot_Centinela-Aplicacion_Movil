package com.robotcentinela.app.shell

import com.robotcentinela.app.environmentalmonitoring.application.DescribeChannelStatus
import com.robotcentinela.app.environmentalmonitoring.application.QueryChannelCapabilities
import com.robotcentinela.app.environmentalmonitoring.application.QueryMetricAggregates
import com.robotcentinela.app.environmentalmonitoring.domain.FreshnessPolicy
import com.robotcentinela.app.identity.application.CurrentHomeScope
import com.robotcentinela.app.identity.application.ListAuthorizedHomes
import com.robotcentinela.app.identity.application.RecoverAccess
import com.robotcentinela.app.identity.application.SelectHomeScope
import com.robotcentinela.app.identity.application.SignIn
import com.robotcentinela.app.identity.application.SignOut
import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.identity.ports.AuthPort
import com.robotcentinela.app.incidents.application.QueryAlertHistory
import com.robotcentinela.app.missions.application.GetMissionSummary
import com.robotcentinela.app.missions.application.ListMissions
import com.robotcentinela.app.robothealth.application.EvaluateRobotHealth
import com.robotcentinela.app.robothealth.domain.IngestConnectivityPolicy
import com.robotcentinela.app.shell.supabase.SupabaseConfig
import com.robotcentinela.app.telemetry.application.QueryTelemetryHistory
import com.robotcentinela.app.telemetry.application.SyncConfig
import com.robotcentinela.app.telemetry.application.TelemetrySynchronizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

/**
 * Instituto Politécnico Nacional
 * Unidad Profesional Interdisciplinaria de Ingeniería Ciencias Sociales y Administrativas
 *
 * Raíz de composición de la aplicación.
 *
 * Centraliza la instanciación de casos de uso del dominio y su enlace con los adaptadores de infraestructura
 * mediante inyección manual de dependencias.
 */
class AppContainer(
    adapters: Adapters,
    val clock: Clock,
) {
    // --- Adaptadores ---
    val authPort: AuthPort = adapters.auth
    private val telemetryQuery = adapters.telemetryQuery
    private val telemetryNotifier = adapters.telemetryNotifier

    // --- Estado compartido de la sesión ---
    val currentHomeScope = CurrentHomeScope()

    // --- Políticas con sus valores iniciales documentados (ADR-004) ---
    val freshnessPolicy = FreshnessPolicy()
    private val ingestConnectivityPolicy = IngestConnectivityPolicy()

    // --- Casos de uso ---
    val signIn = SignIn(authPort)
    val signOut = SignOut(authPort, currentHomeScope)
    val recoverAccess = RecoverAccess(authPort)
    val listAuthorizedHomes = ListAuthorizedHomes(adapters.homeScope)
    val selectHomeScope = SelectHomeScope(adapters.homeScope, currentHomeScope)
    val queryTelemetryHistory = QueryTelemetryHistory(telemetryQuery, currentHomeScope)
    val queryAlertHistory = QueryAlertHistory(adapters.alertQuery, currentHomeScope)
    val describeChannelStatus = DescribeChannelStatus(freshnessPolicy, clock)
    val evaluateRobotHealth = EvaluateRobotHealth(ingestConnectivityPolicy, freshnessPolicy, clock)

    // --- Casos de uso de contratos pendientes (null si el origen de datos no los ofrece) ---
    val queryChannelCapabilities = adapters.capabilities?.let { QueryChannelCapabilities(it, currentHomeScope) }
    val queryMetricAggregates = adapters.aggregates?.let { QueryMetricAggregates(it, currentHomeScope) }
    val listMissions = adapters.missions?.let { ListMissions(it, currentHomeScope) }
    val getMissionSummary = adapters.missions?.let { GetMissionSummary(it, currentHomeScope) }

    /**
     * Crea un sincronizador para un robot del domicilio seleccionado (HU03).
     *
     * @throws com.robotcentinela.app.shared.NotInScopeException si el robot no pertenece al ámbito.
     */
    fun telemetrySynchronizer(robotId: RobotId, config: SyncConfig = SyncConfig()): TelemetrySynchronizer {
        currentHomeScope.requireRobot(robotId)
        return TelemetrySynchronizer(robotId, telemetryQuery, telemetryNotifier, config)
    }

    companion object {

        /** Ámbito de vida de la aplicación, para estados compartidos como la sesión. */
        private fun applicationScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun supabase(
            config: SupabaseConfig,
            clock: Clock = Clock.systemUTC(),
            publicRead: Boolean = false,
        ): AppContainer = AppContainer(Adapters.supabase(config, applicationScope(), publicRead), clock)
    }
}
