package com.robotcentinela.app.ui.summary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.robotcentinela.app.R
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelKey
import com.robotcentinela.app.incidents.domain.Alert
import com.robotcentinela.app.ui.components.AppIcon
import com.robotcentinela.app.ui.components.ErrorState
import com.robotcentinela.app.ui.components.FloorPlanView
import com.robotcentinela.app.ui.components.MetricCard
import com.robotcentinela.app.ui.components.MetricCardSkeleton
import com.robotcentinela.app.ui.components.OperationCard
import com.robotcentinela.app.ui.components.RangeSelector
import com.robotcentinela.app.ui.components.RefreshingNote
import com.robotcentinela.app.ui.components.ScreenTitle
import com.robotcentinela.app.ui.components.SectionCard
import com.robotcentinela.app.ui.components.SkeletonBlock
import com.robotcentinela.app.ui.events.AlertRow
import com.robotcentinela.app.ui.main.FloorPlanState
import com.robotcentinela.app.ui.main.LiveUiState
import com.robotcentinela.app.ui.model.DateRange
import com.robotcentinela.app.ui.model.MapLayers
import com.robotcentinela.app.ui.model.MetricCardModel
import com.robotcentinela.app.ui.model.OperationPresentation
import com.robotcentinela.app.ui.model.RangePreset
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing

/** Resumen del domicilio (propuesta móvil §12.4.2, HU02, HU04, HU08). */
@Composable
fun SummaryScreen(
    live: LiveUiState,
    onRangeChange: (DateRange) -> Unit,
    onOpenChannel: (ChannelKey) -> Unit,
    onOpenMap: () -> Unit,
    onOpenEvents: () -> Unit,
    onOpenAlert: (Alert) -> Unit,
    onRetryAlerts: () -> Unit,
) {
    val colors = CentinelaTheme.colors

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 600.dp) 4 else 2
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            ScreenTitle(
                title = "Resumen del domicilio",
                subtitle = listOfNotNull(live.robot?.let { "Robot ${it.publicCode}" }, live.syncLabel?.text).joinToString(" · "),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (live.refreshing) RefreshingNote()
                Spacer(Modifier.weight(1f))
                RangeSelector(
                    range = live.alertRange,
                    today = live.today,
                    onChange = onRangeChange,
                    presets = listOf(RangePreset.Today, RangePreset.Last7Days, RangePreset.ThisMonth, RangePreset.Custom),
                )
            }

            live.offlineNotice?.let { Notice(it, R.drawable.ic_cloud_off) }
            if (live.rejectedFrames > 0) {
                Notice("${live.rejectedFrames} frames recibidos no cumplen el contrato de la app y no se muestran.", R.drawable.ic_info)
            }

            // Tarjetas ambientales: 2 x 2 en móvil (mockup), 4 en fila en pantallas anchas.
            MetricGrid(if (live.initialLoading) List(4) { null } else live.primaryCards, columns, onOpenChannel)
            Operation(live)
            MapCard(live, onOpenMap)
            EventsCard(live, onOpenEvents, onOpenAlert, onRetryAlerts)
            Text(
                "Un estado \"Dato válido\" no significa ausencia de gases. Las alertas las calcula el servidor con sus reglas.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun Operation(live: LiveUiState) {
    OperationCard(
        battery = live.battery,
        stations = live.mission?.let(OperationPresentation::stationProgress),
        lastUpdate = live.lastUpdate,
    )
}

/** Cuadrícula de tarjetas; `null` dibuja un esqueleto (carga inicial). */
@Composable
private fun MetricGrid(cards: List<MetricCardModel?>, columns: Int, onOpen: (ChannelKey) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        cards.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                row.forEach { card ->
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    if (card == null) MetricCardSkeleton(cell) else MetricCard(card, onOpen = { onOpen(card.channelKey) }, modifier = cell)
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MapCard(live: LiveUiState, onOpenMap: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CentinelaTheme.colors
    SectionCard(
        modifier = modifier.clickable(onClickLabel = "Abrir el mapa", role = Role.Button, onClick = onOpenMap),
        title = "Mapa asistido 2D",
        trailing = { AppIcon(R.drawable.ic_chevron_right, tint = colors.textSecondary) },
    ) {
        val plan = (live.floorPlan as? FloorPlanState.Available)?.plan
        if (live.floorPlan == FloorPlanState.Loading && live.robotMarker == null) {
            SkeletonBlock(Modifier.fillMaxWidth(), height = 200.dp)
        }
        if (live.robotMarker != null || live.trajectory.isNotEmpty() || plan != null) {
            FloorPlanView(
                plan = plan,
                trajectory = live.trajectory,
                robot = live.robotMarker,
                layers = MapLayers(route = true, samples = false),
                modifier = Modifier.fillMaxWidth().height(220.dp),
                accessibilitySummary = mapSummary(live),
            )
        }
        Text(
            when {
                live.robotMarker == null -> "Sin posición: el último frame no trae pose."
                live.poseStale -> "Posición del último reporte (atrasada) · ${live.poseText}"
                else -> "Posición estimada · ${live.poseText}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.s),
        )
        planNote(live.floorPlan)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }
    }
}

/** Nota sobre el plano cuando no se puede dibujar (HU05). */
fun planNote(state: FloorPlanState): String? = when (state) {
    FloorPlanState.ContractPending -> "Plano no disponible: el contrato de mapas está pendiente. Se dibuja la trayectoria sobre una cuadrícula."
    FloorPlanState.NoFile -> "Esta versión de mapa no tiene archivo de plano. Consulta las estaciones en Misiones."
    FloorPlanState.NoMapVersion -> "El último frame no indica versión de mapa."
    is FloorPlanState.Error -> "No se pudo cargar el plano: ${state.message}"
    else -> null
}

/** Texto alternativo del mapa para lectores de pantalla. */
fun mapSummary(live: LiveUiState): String = buildString {
    append("Mapa asistido. ")
    append(live.poseText?.let { "Robot en posición estimada $it. " } ?: "Sin posición del robot. ")
    if (live.poseStale) append("La posición es del último reporte y está atrasada. ")
    append("Ruta con ${live.trajectory.size} puntos. ")
    live.mission?.let { append("Estaciones visitadas ${OperationPresentation.stationProgress(it)}.") }
}

@Composable
private fun EventsCard(live: LiveUiState, onOpenEvents: () -> Unit, onOpenAlert: (Alert) -> Unit, onRetry: () -> Unit) {
    val colors = CentinelaTheme.colors
    SectionCard(
        title = "Eventos y Avisos",
        trailing = { TextButton(onClick = onOpenEvents) { Text("Ver todos", color = colors.textSecondary) } },
    ) {
        val panel = live.alerts
        when {
            panel.loading -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SkeletonBlock(Modifier.fillMaxWidth())
                SkeletonBlock(Modifier.fillMaxWidth(0.7f))
            }
            panel.error != null && panel.alerts.isEmpty() -> ErrorState(panel.error, onRetry = onRetry)
            panel.alerts.isEmpty() -> Text(
                "Sin eventos en ${live.alertRange.label.lowercase()}. La ausencia de eventos no significa ausencia de gases.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                panel.alerts.forEach { alert ->
                    AlertRow(alert = alert, zone = live.zone, today = live.today, onClick = { onOpenAlert(alert) })
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, icon: Int) {
    val colors = CentinelaTheme.colors
    SectionCard(contentPadding = Spacing.m) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, tint = colors.textSecondary)
            Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
        }
    }
}
