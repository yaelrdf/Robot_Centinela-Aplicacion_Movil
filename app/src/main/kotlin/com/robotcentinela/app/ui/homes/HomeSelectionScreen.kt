package com.robotcentinela.app.ui.homes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().background(colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(colors.chrome)
                .statusBarsPadding()
                .padding(horizontal = Spacing.xl, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Elige un domicilio",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onChrome,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(
                    onClick = { scope.launch { deps.container.signOut() } },
                    shape = CentinelaShapes.pill,
                ) {
                    AppIcon(R.drawable.ic_logout, size = 18.dp, tint = colors.onChrome)
                    Spacer(Modifier.size(Spacing.xs))
                    Text("Salir", style = MaterialTheme.typography.labelSmall, color = colors.onChrome)
                }
            }
            Text(
                "Solo verás los datos del domicilio que elijas. Puedes cambiarlo después desde el menú.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onChrome.copy(alpha = 0.9f),
            )
        }

        Box(
            Modifier
                .weight(1f)
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
        ) {
            when {
                state.loading -> Column(
                    Modifier.fillMaxWidth().padding(Spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = colors.primary)
                    Text(
                        "Cargando tus domicilios…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = Spacing.m),
                    )
                }
                state.homes.isEmpty() && state.error != null -> Box(Modifier.padding(Spacing.l)) {
                    ErrorState(state.error!!, onRetry = vm::load)
                }
                state.homes.isEmpty() -> EmptyState(
                    icon = R.drawable.ic_home,
                    title = "Sin domicilios asignados",
                    message = "Tu cuenta no tiene membresías activas vinculadas a un domicilio en el servidor.",
                    actionLabel = "Reintentar",
                    onAction = vm::load,
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize().navigationBarsPadding(),
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
            Modifier.padding(Spacing.l).heightIn(min = 60.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CentinelaShapes.control)
                    .background(colors.statusNeutral.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(R.drawable.ic_home, size = 24.dp, tint = colors.textSecondary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(membership.home.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text(
                    "Zona horaria: ${membership.home.timezone.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            if (selecting) {
                CircularProgressIndicator(Modifier.size(24.dp), color = colors.primary, strokeWidth = 2.dp)
            } else {
                AppIcon(R.drawable.ic_chevron_right, tint = colors.textSecondary)
            }
        }
    }
}
