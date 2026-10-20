package com.robotcentinela.app.ui.homes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.robotcentinela.app.R
import com.robotcentinela.app.identity.domain.HomeMembership
import com.robotcentinela.app.ui.app.uiDependencies
import com.robotcentinela.app.ui.components.AppIcon
import com.robotcentinela.app.ui.components.EmptyState
import com.robotcentinela.app.ui.components.ErrorState
import com.robotcentinela.app.ui.theme.CentinelaShapes
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing

@Composable
fun HomeSelectionScreen(onChosen: () -> Unit) {
    val deps = uiDependencies
    val vm: HomeSelectionViewModel = viewModel {
        HomeSelectionViewModel(
            listAuthorizedHomes = deps.container.listAuthorizedHomes,
            selectHomeScope = deps.container.selectHomeScope,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = CentinelaTheme.colors

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier.fillMaxWidth().background(colors.chrome).statusBarsPadding().padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text("Elige un domicilio", style = MaterialTheme.typography.headlineSmall, color = colors.onChrome, modifier = Modifier.semantics { heading() })
            Text(
                "Solo verás los datos del domicilio que elijas. Puedes cambiarlo después desde el menú.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onChrome,
            )
        }

        when {
            state.loading -> Column(Modifier.fillMaxWidth().padding(Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = colors.primary)
                Text("Cargando tus domicilios…", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.m))
            }
            state.homes.isEmpty() && state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            state.homes.isEmpty() -> EmptyState(
                icon = R.drawable.ic_home,
                title = "Sin domicilios disponibles",
                message = "El servidor no devolvió domicilios. Mientras no exista el inicio de sesión, solo se ven los " +
                    "domicilios que el backend expone sin sesión.",
                actionLabel = "Reintentar",
                onAction = vm::load,
            )
            else -> LazyColumn(
                Modifier.weight(1f).navigationBarsPadding(),
                contentPadding = PaddingValues(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                state.error?.let { message -> item { ErrorState(message) } }
                items(state.homes, key = { it.home.id.value.toString() }) { membership ->
                    HomeRow(
                        membership = membership,
                        selecting = state.selecting == membership.home.id,
                        enabled = state.selecting == null,
                        onClick = { vm.select(membership.home.id, onChosen) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeRow(membership: HomeMembership, selecting: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = CentinelaTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CentinelaShapes.card,
        color = colors.surface,
        border = BorderStroke(CentinelaShapes.borderWidth, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Spacing.l).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            AppIcon(R.drawable.ic_home, size = 28.dp, tint = colors.textSecondary)
            Column(Modifier.weight(1f)) {
                Text(membership.home.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text(
                    "Zona horaria: ${membership.home.timezone.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            if (selecting) {
                CircularProgressIndicator(Modifier.padding(Spacing.xs), color = colors.primary, strokeWidth = 2.dp)
            } else {
                AppIcon(R.drawable.ic_chevron_right, tint = colors.textSecondary)
            }
        }
    }
}
