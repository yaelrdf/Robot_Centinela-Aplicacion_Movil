package com.robotcentinela.app.ui.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.unit.dp
import com.robotcentinela.app.R
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelKey
import com.robotcentinela.app.incidents.domain.Alert
import com.robotcentinela.app.ui.components.AppIcon
import com.robotcentinela.app.ui.components.FutureFeatureNotice
import com.robotcentinela.app.ui.components.ErrorState
import com.robotcentinela.app.ui.components.MetricCard
import com.robotcentinela.app.ui.components.MetricCardSkeleton
import com.robotcentinela.app.ui.components.OperationCard
import com.robotcentinela.app.ui.components.RangeSelector
import com.robotcentinela.app.ui.components.RefreshingNote
import com.robotcentinela.app.ui.components.ScreenTitle
import com.robotcentinela.app.ui.components.SectionCard
import com.robotcentinela.app.ui.components.SkeletonBlock
import com.robotcentinela.app.ui.events.AlertRow
import com.robotcentinela.app.ui.main.LiveUiState
import com.robotcentinela.app.ui.model.DateRange
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
    onOpenEvents: () -> Unit,
    onOpenAlert: (Alert) -> Unit,
    onRetryAlerts: () -> Unit,
) {
    val colors = CentinelaTheme.colors

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 600.dp) 4 else 2
        val isWide = maxWidth >= 760.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
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

            AnimatedVisibility(
                visible = live.offlineNotice != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                live.offlineNotice?.let { Notice(it, R.drawable.ic_cloud_off) }
            }
            if (live.rejectedFrames > 0) {
                Notice("${live.rejectedFrames} frames recibidos no cumplen el contrato de la app y no se muestran.", R.drawable.ic_info)
            }

            // Tarjetas ambientales: 2 x 2 en móvil (mockup), 4 en fila en pantallas anchas.
            MetricGrid(if (live.initialLoading) List(4) { null } else live.primaryCards, columns, onOpenChannel)
            Operation(live)

            if (isWide) {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    MapCard(Modifier.weight(1f).fillMaxHeight())
                    EventsCard(live, onOpenEvents, onOpenAlert, onRetryAlerts, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                MapCard()
                EventsCard(live, onOpenEvents, onOpenAlert, onRetryAlerts)
            }

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
        missionsSupported = live.missionsSupported,
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

/** El mapa asistido 2D no se incluye en esta versión: se muestra la leyenda de implementación futura. */
@Composable
private fun MapCard(modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier, title = "Mapa asistido 2D") {
        FutureFeatureNotice()
    }
}

@Composable
private fun EventsCard(
    live: LiveUiState,
    onOpenEvents: () -> Unit,
    onOpenAlert: (Alert) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CentinelaTheme.colors
    SectionCard(
        modifier = modifier,
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
