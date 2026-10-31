package com.robotcentinela.app.ui.missions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
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
import com.robotcentinela.app.ui.components.FutureFeatureNotice
import com.robotcentinela.app.ui.components.ErrorState
import com.robotcentinela.app.ui.components.PagerControls
import com.robotcentinela.app.ui.components.ScreenTitle
import com.robotcentinela.app.ui.components.SectionCard
import com.robotcentinela.app.ui.components.SkeletonBlock
import com.robotcentinela.app.ui.model.Formats
import com.robotcentinela.app.ui.model.OperationPresentation
import com.robotcentinela.app.ui.model.StatusTone
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 760.dp
        val columns = when {
            maxWidth >= 820.dp -> 3
            maxWidth >= 340.dp -> 2
            else -> 1
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ScreenTitle("Misiones", "Robot ${robot.publicCode}")
                Button(
                    onClick = { creating = true },
                    shape = CentinelaShapes.pill,
                    contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.warning, contentColor = colors.onWarning),
                    modifier = Modifier.heightIn(min = CentinelaShapes.minTouchTarget),
                ) {
                    Text("Generar nueva misión", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(Spacing.xs))
                    AppIcon(R.drawable.ic_add_circle, size = 18.dp, tint = colors.onWarning)
                }
            }

            if (!state.supported) {
                SectionCard {
                    EmptyState(
                        icon = R.drawable.ic_edit_road,
                        title = "Sin historial de misiones",
                        message = "El servidor no tiene habilitado el registro ampliado de misiones (estaciones de muestreo, desvío y métricas de navegación). " +
                            (if (currentMissionId != null) "La telemetría en vivo sí reporta una misión activa." else "Actualmente el robot no reporta misión en curso."),
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
                            SelectedMissionMap()
                        }
                        MissionGrid(
                            missions = state.missions,
                            selectedId = selectedId,
                            zone = zone,
                            columns = columns,
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
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    AppIcon(R.drawable.ic_replay, size = 18.dp, tint = colors.textPrimary)
                    Text(mission.name ?: "Misión sin nombre", style = MaterialTheme.typography.titleSmall, maxLines = 2)
                }
                Text(OperationPresentation.missionStatus(mission.status).text, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    AppIcon(R.drawable.ic_timelapse, size = 16.dp, tint = colors.textPrimary)
                    Text("Duración", style = MaterialTheme.typography.labelMedium)
                }
                Text(OperationPresentation.missionDuration(mission, Instant.now()), style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    AppIcon(R.drawable.ic_conversion_path, size = 16.dp, tint = colors.textPrimary)
                    Text("Estaciones", style = MaterialTheme.typography.labelMedium)
                }
                Text(OperationPresentation.stationProgress(mission), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** El mapa asistido 2D de la misión no se incluye en esta versión: se muestra la leyenda de implementación futura. */
@Composable
private fun SelectedMissionMap() {
    SectionCard(title = "Mapa asistido 2D") {
        FutureFeatureNotice()
    }
}

/** Cuadrícula adaptable de tarjetas de misión del mockup (dos columnas en teléfono, tres en tablet). */
@Composable
private fun MissionGrid(
    missions: List<MissionSummary>,
    selectedId: String?,
    zone: ZoneId,
    columns: Int = 2,
    onSelect: (MissionSummary) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        missions.chunked(columns).forEach { row ->
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
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MissionCard(mission: MissionSummary, selected: Boolean, zone: ZoneId, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CentinelaTheme.colors
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.graphic(StatusTone.Operational) else colors.border,
        animationSpec = tween(durationMillis = 220),
        label = "missionCardBorder",
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CentinelaShapes.card,
        color = colors.surface,
        contentColor = colors.textPrimary,
        border = BorderStroke(
            if (selected) CentinelaShapes.focusWidth else CentinelaShapes.borderWidth,
            borderColor,
        ),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(mission.name ?: "Misión sin nombre", style = MaterialTheme.typography.titleSmall, maxLines = 2)
            IconLine(R.drawable.ic_calendar_month, Formats.dateTime(mission.startedAt, zone))
            IconLine(R.drawable.ic_timelapse, "Duración: ${OperationPresentation.missionDuration(mission, Instant.now())}")
            IconLine(R.drawable.ic_conversion_path, "Estaciones: ${mission.plannedStations ?: "pendiente"}")
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
