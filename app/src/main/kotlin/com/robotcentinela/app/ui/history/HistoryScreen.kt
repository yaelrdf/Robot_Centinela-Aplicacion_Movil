package com.robotcentinela.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robotcentinela.app.R
import com.robotcentinela.app.identity.domain.Robot
import com.robotcentinela.app.ui.app.uiDependencies
import com.robotcentinela.app.ui.components.AppIcon
import com.robotcentinela.app.ui.components.EmptyState
import com.robotcentinela.app.ui.components.ErrorState
import com.robotcentinela.app.ui.components.LineChart
import com.robotcentinela.app.ui.components.RangeSelector
import com.robotcentinela.app.ui.components.RefreshingNote
import com.robotcentinela.app.ui.components.ScreenTitle
import com.robotcentinela.app.ui.components.SectionCard
import com.robotcentinela.app.ui.components.SkeletonBlock
import com.robotcentinela.app.ui.model.Formats
import com.robotcentinela.app.ui.model.OperationPresentation
import com.robotcentinela.app.ui.model.RangePreset
import com.robotcentinela.app.ui.theme.CentinelaShapes
import com.robotcentinela.app.ui.theme.CentinelaText
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId

/**
 * Historial según el mockup móvil: resumen del periodo, alertas registradas, estado de los sensores y
 * "Lectura general" con estadísticas (HU07). Las fechas son días locales del domicilio; los casos de
 * uso las convierten a UTC.
 */
@Composable
fun HistoryScreen(robot: Robot, zone: ZoneId, onChangeHome: () -> Unit) {
    val deps = uiDependencies
    val vm: HistoryViewModel = viewModel { HistoryViewModel(deps.container, robot, zone) }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = CentinelaTheme.colors

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ScreenTitle("Historial", "Fechas en la zona del domicilio (${zone.id})")
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            RangeSelector(
                range = state.range,
                today = state.today,
                onChange = vm::setRange,
                presets = listOf(RangePreset.Today, RangePreset.Last7Days, RangePreset.ThisMonth, RangePreset.Last30Days, RangePreset.Custom),
            )
        }

        SummaryCard(state)

        // Lectura general
        SectionCard(title = "Lectura general", trailing = { if (state.chartLoading && state.chart.isNotEmpty()) RefreshingNote() }) {
            val error = state.chartError
            when {
                state.chartLoading && state.chart.isEmpty() -> SkeletonBlock(Modifier.fillMaxWidth(), height = 220.dp)
                error != null -> ErrorState(
                    vm.errorText(error),
                    onRetry = vm::retry,
                    secondaryLabel = if (OperationPresentation.isPermissionError(error)) "Elegir domicilio" else null,
                    onSecondary = if (OperationPresentation.isPermissionError(error)) onChangeHome else null,
                )
                state.chart.isEmpty() -> EmptyState(R.drawable.ic_show_chart, "Sin datos", "No hay lecturas de ${state.channel.label} en el periodo.")
                else -> {
                    val multiDay = state.range.days > 1
                    LineChart(
                        points = state.chart,
                        unit = state.unit,
                        decimals = state.decimals,
                        xLabel = { ms ->
                            val instant = Instant.ofEpochMilli(ms)
                            if (multiDay) Formats.shortDate(instant.atZone(zone).toLocalDate()) else Formats.shortTime(instant, zone)
                        },
                        selectedIndex = state.selectedIndex,
                        onSelect = vm::select,
                        area = true,
                        accessibilitySummary = "Gráfica de ${state.channel.label} en ${state.range.label}. " +
                            (state.stats?.let { "Mínimo ${it.min}, máximo ${it.max}, promedio ${it.avg}." } ?: ""),
                    )
                    if (state.selection.isNotEmpty()) {
                        Column(
                            Modifier.padding(top = Spacing.s).semantics { liveRegion = LiveRegionMode.Polite },
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            state.selection.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = Spacing.m))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Canal de lectura", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                ChannelDropdown(state, vm::setChannel)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                StatValue("Máxima", state.stats?.max, Modifier.weight(1f))
                StatValue("Mínima", state.stats?.min, Modifier.weight(1f))
                StatValue("Promedio", state.stats?.avg, Modifier.weight(1f))
            }
            state.stats?.let {
                Text(
                    "${it.valid} lecturas válidas · ${it.invalid} sin valor. ${it.source}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
            state.chartNote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary) }
        }

        SectionCard(title = "Por habitación") {
            Text(
                "Las lecturas por habitación las calculará el backend con el plano validado del domicilio; " +
                    "la app no asigna lecturas a habitaciones por su cuenta.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

/** Tarjeta de resumen del mockup: misiones y horas, alertas registradas y estado de los sensores. */
@Composable
private fun SummaryCard(state: HistoryUiState) {
    val colors = CentinelaTheme.colors
    SectionCard(contentPadding = Spacing.l) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val isWide = maxWidth >= 560.dp
            if (isWide) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Resumen", style = MaterialTheme.typography.labelMedium)
                        SummaryLine(R.drawable.ic_flag, "Misiones completadas", "—")
                        SummaryLine(R.drawable.ic_schedule, "Horas de operación", "—")
                        Text("Pendiente de backend", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    }
                    VerticalDivider(Modifier.fillMaxHeight(), color = colors.border)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Alertas registradas", style = MaterialTheme.typography.labelMedium)
                        when {
                            state.alertsLoading -> SkeletonBlock(Modifier.size(64.dp), height = 64.dp)
                            state.alertsError != null -> Text("No se pudieron cargar", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                            else -> AlertDonut(state.alerts ?: AlertBreakdown(0, 0, 0))
                        }
                    }
                    VerticalDivider(Modifier.fillMaxHeight(), color = colors.border)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Estado de los sensores", style = MaterialTheme.typography.labelMedium)
                        Text("Calibrados: —", style = MaterialTheme.typography.labelSmall)
                        Text("Última calibración: —", style = MaterialTheme.typography.labelSmall)
                        Text("Pendiente de backend", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text("Resumen", style = MaterialTheme.typography.labelMedium)
                            SummaryLine(R.drawable.ic_flag, "Misiones completadas", "—")
                            SummaryLine(R.drawable.ic_schedule, "Horas de operación", "—")
                            Text("Pendiente de backend", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                        }
                        VerticalDivider(Modifier.fillMaxHeight(), color = colors.border)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text("Alertas registradas", style = MaterialTheme.typography.labelMedium)
                            when {
                                state.alertsLoading -> SkeletonBlock(Modifier.size(64.dp), height = 64.dp)
                                state.alertsError != null -> Text("No se pudieron cargar", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                else -> AlertDonut(state.alerts ?: AlertBreakdown(0, 0, 0))
                            }
                        }
                    }
                    HorizontalDivider(color = colors.border)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Estado de los sensores", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Calibrados: —", style = MaterialTheme.typography.labelSmall)
                            Text("Última calibración: —", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("Pendiente de backend", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(icon: Int, label: String, value: String) {
    val colors = CentinelaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        AppIcon(icon, size = 16.dp, tint = colors.textPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** Dona de alertas por severidad, con su texto (el color nunca va solo, §12.1). */
@Composable
private fun AlertDonut(breakdown: AlertBreakdown) {
    val colors = CentinelaTheme.colors
    val total = breakdown.total
    val description = if (total == 0) {
        "Sin alertas en el periodo"
    } else {
        "${breakdown.critical} críticas, ${breakdown.warning} advertencias y ${breakdown.other} informativas"
    }
    Box(Modifier.size(72.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(64.dp)) {
            val stroke = 13.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (total == 0) {
                drawArc(colors.border, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            } else {
                var start = -90f
                listOf(
                    breakdown.critical to colors.critical,
                    breakdown.warning to colors.warning,
                    breakdown.other to colors.statusNeutral,
                ).forEach { (count, color) ->
                    if (count > 0) {
                        val sweep = 360f * count / total
                        drawArc(color, start, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                        start += sweep
                    }
                }
            }
        }
        Text("$total", style = MaterialTheme.typography.titleSmall)
    }
    if (total > 0) {
        Text("Críticas: ${breakdown.critical}", style = MaterialTheme.typography.labelSmall)
        Text("Advertencias: ${breakdown.warning}", style = MaterialTheme.typography.labelSmall)
    } else {
        Text("Sin alertas", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
    }
}

/** Selector de canal del mockup (botón naranja "Gas LP"). */
@Composable
private fun ChannelDropdown(state: HistoryUiState, onSelect: (ChannelOption) -> Unit) {
    val colors = CentinelaTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Button(
            onClick = { open = true },
            shape = CentinelaShapes.pill,
            colors = ButtonDefaults.buttonColors(containerColor = colors.warning, contentColor = colors.onWarning),
            contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.xs),
            modifier = Modifier.heightIn(min = CentinelaShapes.minTouchTarget),
        ) {
            Text(state.channel.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Spacer(Modifier.width(Spacing.xs))
            AppIcon(R.drawable.ic_expand_more, size = 16.dp, tint = colors.onWarning)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.channels.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

@Composable
private fun StatValue(label: String, value: String?, modifier: Modifier = Modifier) {
    val colors = CentinelaTheme.colors
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = CentinelaShapes.control,
        color = colors.statusNeutral.copy(alpha = 0.35f),
    ) {
        Column(
            Modifier.padding(vertical = Spacing.s, horizontal = Spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Text(value ?: "—", style = CentinelaText.metricUnit.copy(color = colors.textPrimary), maxLines = 1)
        }
    }
}
