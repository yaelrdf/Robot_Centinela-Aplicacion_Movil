package com.robotcentinela.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robotcentinela.app.BuildConfig
import com.robotcentinela.app.R
import com.robotcentinela.app.ui.app.uiDependencies
import com.robotcentinela.app.ui.components.AppIcon
import com.robotcentinela.app.ui.theme.CentinelaShapes
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing
import com.robotcentinela.app.ui.theme.ThemePreference
import kotlinx.coroutines.launch

/** Ajustes: tema, domicilio y cierre de sesión (HU01). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(homeName: String, onChangeHome: () -> Unit, onDismiss: () -> Unit) {
    val deps = uiDependencies
    val colors = CentinelaTheme.colors
    val theme by deps.preferences.theme.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

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
            Text("Ajustes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })

            Text("Tema", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup()) {
                ThemePreference.entries.forEach { option ->
                    RadioRow(
                        label = option.label,
                        selected = option == theme,
                        onSelect = { deps.preferences.setTheme(option) },
                    )
                }
            }

            HorizontalDivider(color = colors.border)
            Text("Domicilio", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                AppIcon(R.drawable.ic_home, tint = colors.textSecondary)
                Text(homeName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            OutlinedButton(onClick = onChangeHome, shape = CentinelaShapes.control, modifier = Modifier.fillMaxWidth().heightIn(min = CentinelaShapes.minTouchTarget)) {
                Text("Cambiar de domicilio")
            }

            HorizontalDivider(color = colors.border)
            Text("Cuenta y Sesión", style = MaterialTheme.typography.titleSmall)
            val sessionState by deps.container.authPort.sessionState.collectAsStateWithLifecycle()
            val userId = (sessionState as? com.robotcentinela.app.identity.domain.SessionState.Authenticated)?.userId?.value
            val savedEmail = deps.preferences.lastEmail.trim()
            if (userId != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    modifier = Modifier.padding(vertical = Spacing.xs),
                ) {
                    AppIcon(R.drawable.ic_person, tint = colors.textSecondary)
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (BuildConfig.DEMO_PUBLIC_READ) "Modo demostración" else if (savedEmail.isNotEmpty()) savedEmail else "Usuario autenticado",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            if (BuildConfig.DEMO_PUBLIC_READ) "Lectura pública, sin inicio de sesión" else "ID: ${userId.take(8)}...${userId.takeLast(4)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
            // El build demo usa una sesión fija de lectura pública: no hay sesión que cerrar.
            if (!BuildConfig.DEMO_PUBLIC_READ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        scope.launch { deps.container.signOut() }
                    },
                    shape = CentinelaShapes.control,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = CentinelaShapes.minTouchTarget),
                ) {
                    AppIcon(R.drawable.ic_logout, tint = colors.criticalText)
                    Spacer(Modifier.size(Spacing.s))
                    Text("Cerrar sesión", color = colors.criticalText)
                }
            }

            HorizontalDivider(color = colors.border)
            Text(
                "V.I.S.O.R. ${BuildConfig.VERSION_NAME}. Esta app es de consulta: no envía comandos al robot.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit, description: String? = null) {
    val colors = CentinelaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CentinelaShapes.minTouchTarget)
            .clip(CentinelaShapes.control)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.primary, unselectedColor = colors.textSecondary),
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            description?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary) }
        }
    }
}
