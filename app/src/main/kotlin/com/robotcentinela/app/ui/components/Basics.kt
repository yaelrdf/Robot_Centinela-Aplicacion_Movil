package com.robotcentinela.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.robotcentinela.app.R
import com.robotcentinela.app.ui.model.StatusIcon
import com.robotcentinela.app.ui.model.StatusLabel
import com.robotcentinela.app.ui.theme.CentinelaShapes
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing

/** Recurso de la familia Material Symbols Rounded para cada estado (§12.6: una sola familia). */
@DrawableRes
fun StatusIcon.drawable(): Int = when (this) {
    StatusIcon.Valid -> R.drawable.ic_check_circle
    StatusIcon.Info -> R.drawable.ic_info
    StatusIcon.Uncalibrated -> R.drawable.ic_tune
    StatusIcon.NotInstalled -> R.drawable.ic_do_not_disturb_on
    StatusIcon.Warming -> R.drawable.ic_hourglass_top
    StatusIcon.Fault -> R.drawable.ic_error
    StatusIcon.OutOfRange -> R.drawable.ic_unfold_more
    StatusIcon.NoData -> R.drawable.ic_remove
    StatusIcon.Unknown -> R.drawable.ic_help
    StatusIcon.Stale -> R.drawable.ic_schedule
    StatusIcon.Live -> R.drawable.ic_circle
    StatusIcon.Syncing -> R.drawable.ic_sync
    StatusIcon.Offline -> R.drawable.ic_cloud_off
    StatusIcon.Error -> R.drawable.ic_sync_problem
    StatusIcon.Paused -> R.drawable.ic_schedule
}

/** Ícono decorativo (el texto vecino ya lo describe) o con descripción propia. */
@Composable
fun AppIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 20.dp,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(
        painter = painterResource(id),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/**
 * Insignia de estado: color + ícono + texto (§12.1). El texto cumple 4.5:1 sobre el relleno en ambos
 * temas (ver CentinelaColors).
 */
@Composable
fun StatusBadge(label: StatusLabel, modifier: Modifier = Modifier) {
    val colors = CentinelaTheme.colors
    val content = colors.onContainer(label.tone)
    Row(
        modifier = modifier
            .clip(CentinelaShapes.pill)
            .background(colors.container(label.tone))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        AppIcon(label.icon.drawable(), size = 16.dp, tint = content)
        Text(label.text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 2)
    }
}

/** Contenedor de sección (tarjeta con esquinas suavizadas y borde de 1 dp, §12.2-12.3). */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    contentPadding: Dp = Spacing.l,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CentinelaTheme.colors
    val body: @Composable () -> Unit = {
        Column(
            Modifier
                .padding(contentPadding)
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        ) {
            if (title != null || trailing != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (title != null) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    trailing?.invoke()
                }
            }
            content()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.semantics {
                role = Role.Button
                if (onClickLabel != null) onClick(label = onClickLabel) { onClick(); true }
            },
            shape = CentinelaShapes.card,
            color = colors.surface,
            contentColor = colors.textPrimary,
            border = BorderStroke(CentinelaShapes.borderWidth, colors.border),
            content = body,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = CentinelaShapes.card,
            color = colors.surface,
            contentColor = colors.textPrimary,
            border = BorderStroke(CentinelaShapes.borderWidth, colors.border),
            content = body,
        )
    }
}

/** Bloque de esqueleto para la carga inicial (§12.5). Estático: sin animaciones ambiguas. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    Box(
        modifier
            .height(height)
            .clip(CentinelaShapes.control)
            .background(CentinelaTheme.colors.surfaceMuted.copy(alpha = 0.6f)),
    )
}

/** Estado vacío con explicación clara (§12.5: "las ausencias de robot enlazan explicaciones claras"). */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = CentinelaTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CentinelaShapes.card)
                .background(colors.statusNeutral.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, size = 34.dp, tint = colors.textSecondary)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.s))
            OutlinedButton(
                onClick = onAction,
                shape = CentinelaShapes.control,
                modifier = Modifier.heightIn(min = CentinelaShapes.minTouchTarget),
            ) { Text(actionLabel) }
        }
    }
}

/** Error con causa y acciones; un error de permisos ofrece volver al selector de domicilio (§12.5). */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val colors = CentinelaTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CentinelaShapes.card,
        color = colors.critical.copy(alpha = 0.10f),
        border = BorderStroke(CentinelaShapes.borderWidth, colors.critical.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                AppIcon(R.drawable.ic_error, size = 24.dp, tint = colors.critical)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            }
            if (onRetry != null || (secondaryLabel != null && onSecondary != null)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (onRetry != null) {
                        Button(
                            onClick = onRetry,
                            shape = CentinelaShapes.control,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                            modifier = Modifier.heightIn(min = CentinelaShapes.minTouchTarget),
                        ) { Text("Reintentar") }
                    }
                    if (secondaryLabel != null && onSecondary != null) {
                        OutlinedButton(
                            onClick = onSecondary,
                            shape = CentinelaShapes.control,
                            modifier = Modifier.heightIn(min = CentinelaShapes.minTouchTarget),
                        ) { Text(secondaryLabel) }
                    }
                }
            }
        }
    }
}

/** Fila etiqueta-valor para detalles. */
@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = CentinelaTheme.colors
    Row(
        modifier.fillMaxWidth().padding(vertical = Spacing.xs).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.width(140.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}
