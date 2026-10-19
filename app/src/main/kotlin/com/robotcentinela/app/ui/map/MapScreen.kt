package com.robotcentinela.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.robotcentinela.app.R
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelKey
import com.robotcentinela.app.homemapping.domain.Room
import com.robotcentinela.app.ui.components.AppIcon
import com.robotcentinela.app.ui.components.FloorPlanView
import com.robotcentinela.app.ui.components.ScreenTitle
import com.robotcentinela.app.ui.components.SectionCard
import com.robotcentinela.app.ui.components.channelIcon
import com.robotcentinela.app.ui.main.FloorPlanState
import com.robotcentinela.app.ui.main.LiveUiState
import com.robotcentinela.app.ui.model.ChannelPresentation
import com.robotcentinela.app.ui.model.MapLayers
import com.robotcentinela.app.ui.model.OperationPresentation
import com.robotcentinela.app.ui.model.StatusTone
import com.robotcentinela.app.ui.summary.mapSummary
import com.robotcentinela.app.ui.summary.planNote
import com.robotcentinela.app.ui.theme.CentinelaShapes
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing

/** Mapa asistido 2D de la misión en curso, según el mockup móvil (HU05, HU06, guía §12.5). */
@Composable
fun MapScreen(live: LiveUiState) {
    val colors = CentinelaTheme.colors
    var roomDetail by remember { mutableStateOf<Room?>(null) }
    val plan = (live.floorPlan as? FloorPlanState.Available)?.plan
    val mission = live.mission

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ScreenTitle("Mapa", live.robot?.let { "Robot ${it.publicCode}" })

        // Misión en curso | Estaciones
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            SectionCard(Modifier.weight(1f).fillMaxHeight()) {
                Text("Misión en curso", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    AppIcon(R.drawable.ic_replay, size = 18.dp, tint = colors.textPrimary)
                    Text(
                        mission?.name ?: if (live.missionId != null) "Misión activa" else "Sin misión",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    when {
                        mission != null -> OperationPresentation.missionStatus(mission.status).text
                        live.missionsSupported -> "Sin datos de misión"
                        else -> "Detalle pendiente de backend"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            SectionCard(Modifier.weight(1f).fillMaxHeight()) {
                Text("Estaciones", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    AppIcon(R.drawable.ic_conversion_path, size = 18.dp, tint = colors.textPrimary)
                    Text(mission?.let(OperationPresentation::stationProgress) ?: "—", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "Última actualización: ${live.lastUpdate ?: "sin datos"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }

        // Leyenda de sensores | Batería
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            SectionCard(Modifier.weight(1f).fillMaxHeight()) {
                ChannelPresentation.PRIMARY.forEach { key -> SensorLegendRow(key, live) }
            }
            SectionCard(Modifier.weight(1f).fillMaxHeight()) {
                Text("Batería", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    AppIcon(R.drawable.ic_battery_5_bar, size = 22.dp, tint = colors.graphic(StatusTone.Operational))
                    Text(live.battery.primary ?: "—", style = MaterialTheme.typography.titleLarge)
                }
                Text(live.battery.secondary, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
        }

        SectionCard(title = "Mapa asistido 2D") {
            if (live.robotMarker == null && live.trajectory.isEmpty() && plan == null) {
                Text(
                    "Los frames recientes no traen pose. Sin pose no hay posición: el robot nunca se dibuja en un punto supuesto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            } else {
                FloorPlanView(
                    plan = plan,
                    trajectory = live.trajectory,
                    robot = live.robotMarker,
                    layers = MapLayers(route = true, samples = true, transit = false),
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    interactive = true,
                    accessibilitySummary = mapSummary(live),
                )
            }
            Text(
                if (live.poseStale) "Posición del último reporte (atrasada)" else "Posición estimada - Incertidumbre visible",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.s),
            )
            planNote(live.floorPlan)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
        }

        SectionCard(title = "Habitaciones") {
            val rooms = plan?.rooms.orEmpty()
            if (rooms.isEmpty()) {
                Text(
                    "Las habitaciones vienen del plano validado del domicilio, que todavía no publica el backend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            } else {
                rooms.forEach { room ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = CentinelaShapes.minTouchTarget),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(room.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { roomDetail = room },
                            shape = CentinelaShapes.pill,
                            colors = ButtonDefaults.textButtonColors(containerColor = colors.chrome, contentColor = colors.onChrome),
                        ) {
                            AppIcon(R.drawable.ic_info, size = 16.dp, tint = colors.onChrome)
                            Text(" Ver detalles", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    roomDetail?.let { room -> RoomDetailSheet(room = room, onDismiss = { roomDetail = null }) }
}

/** Fila de la leyenda de sensores del mockup: ícono y nombre del canal. */
@Composable
private fun SensorLegendRow(key: ChannelKey, live: LiveUiState) {
    val colors = CentinelaTheme.colors
    val status = live.channelStatuses[key]
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        channelIcon(key)?.let { AppIcon(it, size = 18.dp, tint = colors.textPrimary) }
        Text(ChannelPresentation.title(key, status?.capability), style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}
