package com.robotcentinela.app.ui.missions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robotcentinela.app.R
import com.robotcentinela.app.identity.domain.Robot
import com.robotcentinela.app.missions.domain.MissionId
import com.robotcentinela.app.missions.domain.MissionSummary
import com.robotcentinela.app.ui.app.uiDependencies
import com.robotcentinela.app.ui.components.AppIcon
import com.robotcentinela.app.ui.components.EmptyState
import com.robotcentinela.app.ui.components.ErrorState
import com.robotcentinela.app.ui.components.FloorPlanView
import com.robotcentinela.app.ui.components.PagerControls
import com.robotcentinela.app.ui.components.ScreenTitle
import com.robotcentinela.app.ui.components.SectionCard
import com.robotcentinela.app.ui.components.SkeletonBlock
import com.robotcentinela.app.ui.main.FloorPlanState
import com.robotcentinela.app.ui.model.Formats
import com.robotcentinela.app.ui.model.MapLayers
import com.robotcentinela.app.ui.model.OperationPresentation
import com.robotcentinela.app.ui.model.StatusTone
import com.robotcentinela.app.ui.summary.planNote
import com.robotcentinela.app.ui.theme.CentinelaShapes
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId

/**
 * Misiones según el mockup móvil: "Generar nueva misión", detalle de la misión seleccionada, su mapa y
 * las tarjetas de misiones anteriores (RF14, HU05, HU06, HU08).
 */
@Composable
fun MissionsScreen(robot: Robot, zone: ZoneId, currentMissionId: MissionId?) {
    val deps = uiDependencies
    val vm: MissionsViewModel = viewModel { MissionsViewModel(deps.container, robot) }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = CentinelaTheme.colors
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    // Por defecto se selecciona la misión en curso o, si no hay, la más reciente.
    LaunchedEffect(state.missions, currentMissionId) {
        if (selectedId == null || state.missions.none { it.id.value.toString() == selectedId }) {
            selectedId = (state.missions.firstOrNull { it.id == currentMissionId } ?: state.missions.firstOrNull())
                ?.id?.value?.toString()
        }
    }
    val selected = state.missions.firstOrNull { it.id.value.toString() == selectedId }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ScreenTitle("Misiones", "Robot ${robot.publicCode}")
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { creating = true },
                shape = CentinelaShapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = colors.secondary, contentColor = colors.onWarning),
                modifier = Modifier.heightIn(min = CentinelaShapes.minTouchTarget),
            ) {
                Text("Generar nueva misión ", style = MaterialTheme.typography.labelMedium)
                AppIcon(R.drawable.ic_add_circle, size = 18.dp, tint = colors.onWarning)
            }
        }

        if (!state.supported) {
            SectionCard {
                EmptyState(
                    icon = R.drawable.ic_edit_road,
                    title = "Misiones pendientes de backend",
                    message = "El backend todavía no publica el contrato de misiones (nombre, estaciones previstas, " +
                        "visitadas y omitidas, duración y consumo). " +
                        (if (currentMissionId != null) "El último frame sí indica una misión en curso." else "El último frame no indica misión."),
                )
            }
        } else {
            val error = state.error
            when {
                state.loading && state.missions.isEmpty() -> SkeletonBlock(Modifier.fillMaxWidth(), height = 160.dp)
                error != null && state.missions.isEmpty() -> ErrorState(OperationPresentation.errorMessage(error), onRetry = vm::retry)
                state.missions.isEmpty() -> SectionCard {
                    EmptyState(R.drawable.ic_edit_road, "Sin misiones", "El robot todavía no registra misiones.")
                }
                else -> {
                    selected?.let { mission ->
                        MissionDetailsCard(mission)
                        SelectedMissionMap(robot, zone, mission)
                    }
                    MissionGrid(
                        missions = state.missions,
                        selectedId = selectedId,
                        zone = zone,
                        onSelect = { selectedId = it.id.value.toString() },
                    )
                    PagerControls(
                        page = state.page,
                        hasPrevious = state.page > 0 && !state.loading,
                        hasNext = state.hasNext && !state.loading,
                        onPrevious = vm::previous,
                        onNext = vm::next,
                    )
                }
            }
        }
    }

    if (creating) {
        NewMissionSheet(onDismiss = { creating = false })
    }
}

/** Tarjeta "Detalles de misión" del mockup. */
@Composable
private fun MissionDetailsCard(mission: MissionSummary) {
    val colors = CentinelaTheme.colors
    SectionCard(title = "Detalles de misión") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.2f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    AppIcon(R.drawable.ic_replay, size = 18.dp, tint = colors.textPrimary)
                    Text(mission.name ?: "Misión sin nombre", style = MaterialTheme.typography.titleSmall, maxLines = 2)
                }
                Text(OperationPresentation.missionStatus(mission.status).text, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    AppIcon(R.drawable.ic_timelapse, size = 16.dp, tint = colors.textPrimary)
                    Text("Duración", style = MaterialTheme.typography.labelMedium)
                }
                Text(OperationPresentation.missionDuration(mission, Instant.now()), style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    AppIcon(R.drawable.ic_conversion_path, size = 16.dp, tint = colors.textPrimary)
                    Text("Estaciones", style = MaterialTheme.typography.labelMedium)
                }
                Text(OperationPresentation.stationProgress(mission), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Mapa de la misión seleccionada, sobre la versión EXACTA de mapa que usó (HU05 CDA 2). */
@Composable
private fun SelectedMissionMap(robot: Robot, zone: ZoneId, mission: MissionSummary) {
    val deps = uiDependencies
    val colors = CentinelaTheme.colors
    val id = mission.id.value.toString()
    val vm: MissionDetailViewModel = viewModel(key = "mission-$id") { MissionDetailViewModel(deps.container, robot, zone, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    SectionCard(title = "Mapa asistido 2D") {
        val plan = (state.floorPlan as? FloorPlanState.Available)?.plan
        if (state.trajectory.isEmpty() && plan == null) {
            Text("Sin poses registradas para esta misión.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        } else {
            FloorPlanView(
                plan = plan,
                trajectory = state.trajectory,
                robot = null,
                layers = MapLayers(route = true, samples = true),
                modifier = Modifier.fillMaxWidth().height(240.dp),
                interactive = true,
                accessibilitySummary = "Trayectoria de la misión con ${state.trajectory.size} puntos.",
            )
        }
        Text(
            "Posición estimada - Incertidumbre visible",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.s),
        )
        planNote(state.floorPlan)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary) }
    }
}

/** Cuadrícula de tarjetas de misión del mockup (dos columnas). */
@Composable
private fun MissionGrid(missions: List<MissionSummary>, selectedId: String?, zone: ZoneId, onSelect: (MissionSummary) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        missions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                row.forEach { mission ->
                    MissionCard(
                        mission = mission,
                        selected = mission.id.value.toString() == selectedId,
                        zone = zone,
                        onClick = { onSelect(mission) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MissionCard(mission: MissionSummary, selected: Boolean, zone: ZoneId, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CentinelaTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CentinelaShapes.card,
        color = colors.surface,
        contentColor = colors.textPrimary,
        border = BorderStroke(
            if (selected) CentinelaShapes.focusWidth else CentinelaShapes.borderWidth,
            if (selected) colors.graphic(StatusTone.Operational) else colors.border,
        ),
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(mission.name ?: "Misión sin nombre", style = MaterialTheme.typography.titleSmall, maxLines = 2)
            IconLine(R.drawable.ic_calendar_month, Formats.dateTime(mission.startedAt, zone))
            IconLine(R.drawable.ic_timelapse, "Duración: ${OperationPresentation.missionDuration(mission, Instant.now())}")
            IconLine(R.drawable.ic_conversion_path, "Estaciones: ${mission.plannedStations}")
        }
    }
}

@Composable
private fun IconLine(icon: Int, text: String) {
    val colors = CentinelaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        AppIcon(icon, size = 16.dp, tint = colors.textPrimary)
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}
