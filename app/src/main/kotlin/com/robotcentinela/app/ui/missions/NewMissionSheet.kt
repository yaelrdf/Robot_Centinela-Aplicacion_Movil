package com.robotcentinela.app.ui.missions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.robotcentinela.app.R
import com.robotcentinela.app.ui.components.AppIcon
import com.robotcentinela.app.ui.components.SectionCard
import com.robotcentinela.app.ui.theme.CentinelaShapes
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing

/**
 * Hoja "Nueva misión" del mockup: propósito, estaciones y tiempo estimado.
 *
 * Todavía no hay contrato en el backend para crear misiones, ni plano con estaciones para elegirlas.
 * Además, el expediente define la app como de consulta: el robot se inicia y se detiene localmente
 * (§1, §7.1). Por eso "Guardar" no envía nada; avisa que la función está pendiente de definición.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMissionSheet(onDismiss: () -> Unit) {
    val colors = CentinelaTheme.colors
    var purpose by rememberSaveable { mutableStateOf("") }
    var purposeError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingNotice by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.chrome,
        contentColor = colors.onChrome,
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text("Nueva Misión", style = MaterialTheme.typography.headlineSmall, color = colors.onChrome, modifier = Modifier.semantics { heading() })
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    OutlinedTextField(
                        value = purpose,
                        onValueChange = { purpose = it; purposeError = null; pendingNotice = false },
                        label = { Text("Propósito de la misión") },
                        supportingText = { purposeError?.let { Text(it) } },
                        isError = purposeError != null,
                        singleLine = true,
                        shape = CentinelaShapes.control,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.focus,
                            unfocusedBorderColor = colors.border,
                            errorBorderColor = colors.critical,
                            errorSupportingTextColor = colors.critical,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Elige las estaciones", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Las estaciones se eligen sobre el plano validado del domicilio, que todavía no publica el backend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text("Estaciones: 0", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text("Tiempo estimado: —", style = MaterialTheme.typography.labelMedium)
                    }
                    if (pendingNotice) {
                        Row(
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            AppIcon(R.drawable.ic_info, tint = colors.textSecondary)
                            Text(
                                "No se guardó: crear misiones desde la app todavía no tiene contrato en el backend.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                if (purpose.isBlank()) {
                                    purposeError = "Escribe el propósito de la misión."
                                } else {
                                    pendingNotice = true
                                }
                            },
                            shape = CentinelaShapes.pill,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.secondary, contentColor = colors.onWarning),
                            modifier = Modifier.heightIn(min = CentinelaShapes.minTouchTarget),
                        ) {
                            Text("Guardar ", style = MaterialTheme.typography.labelMedium)
                            AppIcon(R.drawable.ic_check, size = 16.dp, tint = colors.onWarning)
                        }
                    }
                }
            }
        }
    }
}
