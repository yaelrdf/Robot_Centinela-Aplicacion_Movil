package com.robotcentinela.app.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robotcentinela.app.R
import com.robotcentinela.app.identity.domain.Robot
import com.robotcentinela.app.incidents.domain.Alert
import com.robotcentinela.app.ui.app.uiDependencies
import com.robotcentinela.app.ui.components.EmptyState
import com.robotcentinela.app.ui.components.ErrorState
import com.robotcentinela.app.ui.components.FilterChipRow
import com.robotcentinela.app.ui.components.PagerControls
import com.robotcentinela.app.ui.components.RangeSelector
import com.robotcentinela.app.ui.components.RefreshingNote
import com.robotcentinela.app.ui.components.ScreenTitle
import com.robotcentinela.app.ui.components.SectionCard
import com.robotcentinela.app.ui.components.SkeletonBlock
import com.robotcentinela.app.ui.model.OperationPresentation
import com.robotcentinela.app.ui.model.RangePreset
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing
import java.time.ZoneId

@Composable
fun EventsScreen(robot: Robot, zone: ZoneId, onOpenAlert: (Alert) -> Unit, onChangeHome: () -> Unit) {
    val deps = uiDependencies
    val vm: EventsViewModel = viewModel {
        EventsViewModel(
            queryAlertHistory = deps.container.queryAlertHistory,
            clock = deps.container.clock,
            robot = robot,
            zone = zone,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = CentinelaTheme.colors

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        ScreenTitle("Eventos", "Alertas e incidentes registrados por el servidor")
        SectionCard(title = "Filtros") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                RangeSelector(
                    range = state.range,
                    today = vm.todayDate,
                    onChange = vm::setRange,
                    presets = listOf(RangePreset.Today, RangePreset.Last7Days, RangePreset.Last30Days, RangePreset.Custom),
                    label = "Periodo:",
                )
                Text("Severidad", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                FilterChipRow(
                    options = SeverityFilter.entries,
                    selected = SeverityFilter.entries.filter { it.raw in state.severities }.toSet(),
                    label = { it.label },
                    onToggle = { vm.toggleSeverity(it.raw) },
                )
                Text("Estado", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                FilterChipRow(
                    options = StateFilter.entries,
                    selected = StateFilter.entries.filter { it.state in state.states }.toSet(),
                    label = { it.label },
                    onToggle = { vm.toggleState(it.state) },
                )
            }
        }

        SectionCard(title = "Resultados", trailing = { if (state.loading && state.alerts.isNotEmpty()) RefreshingNote() }) {
            val error = state.error
            when {
                state.loading && state.alerts.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    repeat(4) { SkeletonBlock(Modifier.fillMaxWidth()) }
                }
                error != null -> ErrorState(
                    OperationPresentation.errorMessage(error),
                    onRetry = vm::retry,
                    secondaryLabel = if (OperationPresentation.isPermissionError(error)) "Elegir domicilio" else null,
                    onSecondary = if (OperationPresentation.isPermissionError(error)) onChangeHome else null,
                )
                state.alerts.isEmpty() -> EmptyState(
                    icon = R.drawable.ic_event_available,
                    title = "Sin eventos",
                    message = "No hay eventos con estos filtros. La ausencia de eventos no significa ausencia de gases.",
                )
                else -> Column {
                    state.alerts.forEachIndexed { index, alert ->
                        if (index > 0) HorizontalDivider(color = colors.border)
                        AlertRow(alert, zone, vm.todayDate, onClick = { onOpenAlert(alert) })
                    }
                }
            }
            PagerControls(
                page = state.page,
                hasPrevious = state.page > 0 && !state.loading,
                hasNext = state.hasNext && !state.loading,
                onPrevious = vm::previousPage,
                onNext = vm::nextPage,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
        Text(
            "El historial de la base de datos es la fuente principal de consulta: las notificaciones push no son un canal " +
                "de alarma garantizado.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}
