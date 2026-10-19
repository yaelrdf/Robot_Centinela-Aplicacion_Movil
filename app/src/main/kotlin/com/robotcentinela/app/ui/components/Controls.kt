package com.robotcentinela.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.robotcentinela.app.R
import com.robotcentinela.app.ui.model.DateRange
import com.robotcentinela.app.ui.model.RangePreset
import com.robotcentinela.app.ui.theme.CentinelaShapes
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Título de página con subtítulo (propuesta: "Resumen del domicilio"). */
@Composable
fun ScreenTitle(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    val colors = CentinelaTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
    }
}

/**
 * Selector "Rango de datos" con rangos predefinidos y uno personalizado (HU07). El botón naranja usa
 * texto oscuro para cumplir 4.5:1 (con blanco sería 3.2:1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeSelector(
    range: DateRange,
    today: LocalDate,
    onChange: (DateRange) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<RangePreset> = RangePreset.entries,
    label: String = "Rango de datos:",
) {
    val colors = CentinelaTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
        Box {
            Button(
                onClick = { menuOpen = true },
                shape = CentinelaShapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = colors.secondary, contentColor = colors.onWarning),
                modifier = Modifier.heightIn(min = CentinelaShapes.minTouchTarget),
            ) {
                Text(range.label, style = MaterialTheme.typography.labelMedium)
                AppIcon(R.drawable.ic_expand_more, size = 20.dp, tint = colors.onWarning, modifier = Modifier.padding(start = Spacing.xs))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.label) },
                        onClick = {
                            menuOpen = false
                            if (preset == RangePreset.Custom) pickerOpen = true else onChange(DateRange.of(preset, today))
                        },
                    )
                }
            }
        }
    }

    if (pickerOpen) {
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = range.from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = range.to.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val start = state.selectedStartDateMillis
                        if (start != null) {
                            val end = state.selectedEndDateMillis ?: start
                            onChange(DateRange.custom(start.toUtcDate(), end.toUtcDate()))
                        }
                        pickerOpen = false
                    },
                ) { Text("Aplicar") }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("Cancelar") } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.weight(1f), showModeToggle = true)
        }
    }
}

/** El selector de fechas de Material entrega medianoche UTC; se convierte de vuelta a día local. */
private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Fila de filtros de selección múltiple. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> FilterChipRow(
    options: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CentinelaTheme.colors
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        options.forEach { option ->
            val isSelected = option in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(option) },
                label = { Text(label(option)) },
                leadingIcon = if (isSelected) {
                    { AppIcon(R.drawable.ic_check_circle, size = 18.dp, tint = colors.onPrimary) }
                } else {
                    null
                },
                shape = CentinelaShapes.control,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primary,
                    selectedLabelColor = colors.onPrimary,
                    labelColor = colors.textPrimary,
                ),
                modifier = Modifier.heightIn(min = CentinelaShapes.minTouchTarget),
            )
        }
    }
}

/** Paginación explícita (§12.5: sin desplazamiento infinito). */
@Composable
fun PagerControls(
    page: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CentinelaTheme.colors
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(onClick = onPrevious, enabled = hasPrevious, shape = CentinelaShapes.control) {
            AppIcon(R.drawable.ic_chevron_left, size = 18.dp, tint = if (hasPrevious) colors.textPrimary else colors.textSecondary)
            Text("Anterior")
        }
        Text("Página ${page + 1}", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        OutlinedButton(onClick = onNext, enabled = hasNext, shape = CentinelaShapes.control) {
            Text("Siguiente")
            AppIcon(R.drawable.ic_chevron_right, size = 18.dp, tint = if (hasNext) colors.textPrimary else colors.textSecondary)
        }
    }
}

/** Indicador pequeño de reconsulta: conserva los últimos valores y avisa que se actualizan (§12.5). */
@Composable
fun RefreshingNote(text: String = "Actualizando…", modifier: Modifier = Modifier) {
    val colors = CentinelaTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        AppIcon(R.drawable.ic_sync, size = 16.dp, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
    }
}
