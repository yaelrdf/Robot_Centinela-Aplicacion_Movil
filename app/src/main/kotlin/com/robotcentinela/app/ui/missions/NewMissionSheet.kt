package com.robotcentinela.app.ui.missions

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
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
import com.robotcentinela.app.ui.components.FutureFeatureNotice
import com.robotcentinela.app.ui.components.AppIcon
import com.robotcentinela.app.ui.components.SectionCard
import com.robotcentinela.app.ui.theme.CentinelaShapes
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing

/**
 * Hoja "Nueva misión": interfaz gráfica para la captura de propósito, estaciones y tiempo estimado.
 *
 * [PENDIENTE DE CONTRATO / DUMMY OPERATIVO]:
 * Actualmente no existe contrato ni canal de comandos hacia el robot para iniciar misiones remotamente.
 * El botón "Guardar" no despacha eventos a la red; únicamente despliega un aviso informando que
 * la acción requiere integración de comandos directos con el dispositivo centinela.
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
                            errorBorderColor = colors.criticalText,
                            errorSupportingTextColor = colors.criticalText,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Elige las estaciones", style = MaterialTheme.typography.titleSmall)
                    // Las estaciones se eligen sobre el mapa 2D, que no se incluye en esta versión.
                    FutureFeatureNotice()
                    Row(Modifier.fillMaxWidth()) {
                        Text("Estaciones: 0", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text("Tiempo estimado: —", style = MaterialTheme.typography.labelMedium)
                    }
                    if (pendingNotice) {
                        Surface(
                            color = colors.statusNeutral.copy(alpha = 0.2f),
                            border = BorderStroke(CentinelaShapes.borderWidth, colors.border),
                            shape = CentinelaShapes.control,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(Spacing.m).semantics { liveRegion = LiveRegionMode.Polite },
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(R.drawable.ic_info, tint = colors.textSecondary)
                                Text(
                                    "Modo de planificación: La app funciona en modo de consulta según las especificaciones del sistema. El envío de comandos de navegación directa al hardware del robot se habilitará en la siguiente fase de integración.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textPrimary,
                                )
                            }
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
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                            modifier = Modifier.heightIn(min = CentinelaShapes.minTouchTarget),
                        ) {
                            Text("Planificar misión ", style = MaterialTheme.typography.labelMedium)
                            AppIcon(R.drawable.ic_check, size = 16.dp, tint = colors.onPrimary)
                        }
                    }
                }
            }
        }
    }
}
