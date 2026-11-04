package com.robotcentinela.app.ui.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.robotcentinela.app.R
import com.robotcentinela.app.incidents.domain.Alert
import com.robotcentinela.app.incidents.domain.AlertState
import com.robotcentinela.app.ui.components.AppIcon
import com.robotcentinela.app.ui.components.DetailRow
import com.robotcentinela.app.ui.components.StatusBadge
import com.robotcentinela.app.ui.components.drawable
import com.robotcentinela.app.ui.model.Formats
import com.robotcentinela.app.ui.model.OperationPresentation
import com.robotcentinela.app.ui.theme.CentinelaShapes
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.ZoneId

/** `true` si la alerta sigue sin cerrar y por tanto se puede resolver (ADR-011). */
fun Alert.isResolvable(): Boolean = state == AlertState.Open || state == AlertState.Acknowledged

/**
 * Fila de evento: severidad, título, origen traducido, fecha y estado (§12.5).
 *
 * Con [onResolve] y una alerta sin cerrar se añade el botón «Resolver» (ADR-011); [resolving] lo deshabilita
 * mientras se escribe en el servidor.
 */
@Composable
fun AlertRow(
    alert: Alert,
    zone: ZoneId,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onResolve: ((Alert) -> Unit)? = null,
    resolving: Boolean = false,
) {
    val colors = CentinelaTheme.colors
    val severity = OperationPresentation.severity(alert.severity)
    val state = OperationPresentation.state(alert.state)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = CentinelaShapes.minTouchTarget)
            .clip(CentinelaShapes.control)
            .clickable(onClickLabel = "Ver detalle del evento", role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.s, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        AppIcon(
            severity.icon.drawable(),
            contentDescription = "Severidad ${severity.text}",
            size = 28.dp,
            tint = colors.graphic(severity.tone),
        )
        Column(Modifier.weight(1f)) {
            Text(OperationPresentation.alertTitle(alert.type), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Text(
                "${OperationPresentation.origin(alert.origin)} · ${Formats.smartDateTime(alert.openedAt, zone, today)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            if (onResolve != null && alert.isResolvable()) {
                TextButton(
                    onClick = { onResolve(alert) },
                    enabled = !resolving,
                    modifier = Modifier.heightIn(min = CentinelaShapes.minTouchTarget),
                ) {
                    if (resolving) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.textSecondary)
                    } else {
                        Text("Resolver", color = colors.textPrimary)
                    }
                }
            }
        }
        StatusBadge(state)
    }
}

/**
 * Confirmación previa a cerrar una alerta (ADR-011). La alerta es compartida por todo el domicilio y el cierre no
 * se deshace desde la app.
 */
@Composable
fun ResolveAlertDialog(alert: Alert, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Resolver esta alerta?") },
        text = {
            Text(
                "«${OperationPresentation.alertTitle(alert.type)}» se cerrará en el servidor para todo el domicilio. " +
                    "Resolverla no garantiza que la condición física haya desaparecido: si persiste, el servidor abrirá un incidente nuevo.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Resolver") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Detalle de un evento persistido (§12.5, HU11): severidad, origen, inicio, última ocurrencia, cierre y
 * estado de procesamiento. Con [onResolve] y una alerta sin cerrar ofrece «Resolver alerta» (ADR-011);
 * [error] muestra el motivo si el servidor lo rechazó.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailSheet(
    alert: Alert,
    zone: ZoneId,
    today: LocalDate,
    onDismiss: () -> Unit,
    onResolve: ((Alert) -> Unit)? = null,
    resolving: Boolean = false,
    error: String? = null,
) {
    val colors = CentinelaTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                OperationPresentation.alertTitle(alert.type),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                StatusBadge(OperationPresentation.severity(alert.severity))
                StatusBadge(OperationPresentation.state(alert.state))
            }
            DetailRow("Origen", OperationPresentation.origin(alert.origin))
            DetailRow("Inicio", Formats.dateTimeSeconds(alert.openedAt, zone))
            DetailRow("Última ocurrencia", Formats.dateTimeSeconds(alert.lastSeenAt, zone))
            DetailRow("Cierre", alert.resolvedAt?.let { Formats.dateTimeSeconds(it, zone) } ?: "Sin cerrar")
            DetailRow("Regla", if (alert.ruleId != null) "Regla versionada del servidor" else "Sin regla (evento del dispositivo)")
            if (onResolve != null && alert.isResolvable()) {
                Button(
                    onClick = { onResolve(alert) },
                    enabled = !resolving,
                    shape = CentinelaShapes.control,
                    modifier = Modifier.fillMaxWidth().heightIn(min = CentinelaShapes.minTouchTarget),
                ) {
                    if (resolving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Resolver alerta")
                    }
                }
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = colors.critical)
                }
            }
            HorizontalDivider(color = colors.border)
            Text("Evidencia", style = MaterialTheme.typography.titleSmall)
            Text(
                "Las capturas de evidencia y el registro de ocurrencias detalladas se desplegarán al estar vinculadas al servidor.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            HorizontalDivider(color = colors.border)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                AppIcon(R.drawable.ic_info, tint = colors.textSecondary)
                Text(
                    "Las alertas las calcula el servidor con sus reglas y umbrales; la app solo las consulta. " +
                        "Un evento atendido no garantiza que la condición física haya desaparecido. " +
                        "Esta app no reemplaza las alertas locales del robot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

