package com.robotcentinela.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.robotcentinela.app.R
import com.robotcentinela.app.environmentalmonitoring.domain.ChannelKey
import com.robotcentinela.app.ui.model.BatteryModel
import com.robotcentinela.app.ui.model.ConnectivityItem
import com.robotcentinela.app.ui.model.MetricCardModel
import com.robotcentinela.app.ui.model.StatusTone
import com.robotcentinela.app.ui.theme.CentinelaShapes
import com.robotcentinela.app.ui.theme.CentinelaText
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing

/** Ícono del sensor según el mockup (Gas LP, metano, temperatura y humedad); `null` si no hay uno definido. */
fun channelIcon(channelKey: ChannelKey): Int? = when (channelKey.value) {
    "mq6_raw" -> R.drawable.ic_local_fire_department
    "mq4_raw" -> R.drawable.ic_airwave
    "temperature" -> R.drawable.ic_thermostat
    "humidity" -> R.drawable.ic_water_drop
    else -> null
}

/**
 * Tarjeta de métrica del mockup móvil (guía §12.5, HU02, HU04, HU20): nombre con ícono del sensor,
 * valor con unidad, "Última lectura" y estado de calidad con ícono.
 *
 * - Sin valor: raya "—" con la leyenda del estado ("Sin dato", "No instalado", "Calentando"); nunca 0.
 * - Falla del sensor: borde rojo. Dato atrasado: borde naranja y hora exacta de la última lectura.
 * - Toda la tarjeta es un solo elemento para lectores de pantalla, con una descripción completa.
 */
@Composable
fun MetricCard(model: MetricCardModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CentinelaTheme.colors
    val critical = model.status.tone == StatusTone.Critical
    Surface(
        onClick = onOpen,
        modifier = modifier
            .heightIn(min = 132.dp)
            .clearAndSetSemantics {
                contentDescription = model.accessibilityText
                role = Role.Button
                onClick(label = "Ver detalle de ${model.title}") { onOpen(); true }
            },
        shape = CentinelaShapes.card,
        color = colors.surface,
        contentColor = colors.textPrimary,
        border = BorderStroke(
            if (critical || model.stale) CentinelaShapes.focusWidth else CentinelaShapes.borderWidth,
            when {
                critical -> colors.critical
                model.stale -> colors.warning
                else -> colors.border
            },
        ),
    ) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(model.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                channelIcon(model.channelKey)?.let { AppIcon(it, size = 18.dp, tint = if (critical) colors.critical else colors.textPrimary) }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(model.value ?: "—", style = CentinelaText.metricValue, maxLines = 1)
                model.unit?.let {
                    Text(it, style = CentinelaText.metricUnit, color = colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            Text(
                when {
                    model.stale -> "Atrasado: última lectura ${model.capturedAt ?: "sin hora"}"
                    model.age != null -> "Última lectura: ${model.age}"
                    else -> "Última lectura: ----"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (model.stale) colors.textPrimary else colors.textSecondary,
            )
            StatusBadge(model.status, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Esqueleto de una tarjeta de métrica, para la carga inicial. */
@Composable
fun MetricCardSkeleton(modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier.heightIn(min = 148.dp)) {
        SkeletonBlock(Modifier.width(96.dp))
        SkeletonBlock(Modifier.width(72.dp).padding(top = Spacing.s), height = 32.dp)
        SkeletonBlock(Modifier.width(110.dp).padding(top = Spacing.s))
    }
}

/**
 * Barra de conectividad (§12.5): distingue robot, servidor de ingesta y esta app, y muestra "estado
 * desconocido" cuando no hay información.
 */
@Composable
fun ConnectivityBar(items: List<ConnectivityItem>, modifier: Modifier = Modifier) {
    val colors = CentinelaTheme.colors
    SectionCard(modifier = modifier, title = "Conectividad", contentPadding = Spacing.m) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clearAndSetSemantics {
                        contentDescription = "${item.title}: ${item.status.text}. ${item.detail ?: ""}"
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    AppIcon(
                        when (item.title) {
                            "Robot" -> R.drawable.ic_pin_drop
                            "Ingesta" -> R.drawable.ic_router
                            else -> R.drawable.ic_smartphone
                        },
                        tint = colors.textSecondary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.labelMedium)
                        item.detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary) }
                    }
                    StatusBadge(item.status)
                }
            }
        }
    }
}

/** Panel "Operación" del mockup: batería con su método y estaciones de la misión (HU08). */
@Composable
fun OperationCard(
    battery: BatteryModel,
    stations: String?,
    lastUpdate: String?,
    modifier: Modifier = Modifier,
) {
    val colors = CentinelaTheme.colors
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text("Operación", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text("Batería", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    AppIcon(R.drawable.ic_battery_5_bar, size = 20.dp, tint = colors.graphic(StatusTone.Operational))
                    Text(battery.primary ?: "—", style = MaterialTheme.typography.titleLarge)
                }
                Text(battery.secondary, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text("Estaciones", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    AppIcon(R.drawable.ic_conversion_path, size = 20.dp, tint = colors.textPrimary)
                    Text(stations ?: "—", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    if (stations == null) "Misiones pendientes de backend" else "Última actualización: ${lastUpdate ?: "sin datos"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
