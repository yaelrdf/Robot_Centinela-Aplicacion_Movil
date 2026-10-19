package com.robotcentinela.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.robotcentinela.app.R
import com.robotcentinela.app.homemapping.domain.Room
import com.robotcentinela.app.ui.components.EmptyState
import com.robotcentinela.app.ui.components.SectionCard
import com.robotcentinela.app.ui.theme.CentinelaTheme
import com.robotcentinela.app.ui.theme.Spacing

/**
 * Hoja "Estado de [habitación]" del mockup.
 *
 * Las lecturas por habitación las debe calcular el backend con la geometría validada del plano: la app
 * no asigna lecturas a habitaciones por su cuenta (RB11, HU06 CDA 1). Mientras no exista ese contrato,
 * la hoja lo indica en lugar de mostrar una gráfica inventada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailSheet(room: Room, onDismiss: () -> Unit) {
    val colors = CentinelaTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.chrome,
        contentColor = colors.onChrome,
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(
                "Estado de ${room.name}",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onChrome,
                modifier = Modifier.semantics { heading() },
            )
            SectionCard {
                EmptyState(
                    icon = R.drawable.ic_show_chart,
                    title = "Lecturas por habitación pendientes",
                    message = "El backend todavía no agrupa las lecturas por habitación. Cuando publique ese dato, " +
                        "aquí se verá la gráfica de Gas LP, metano, temperatura y humedad de ${room.name}.",
                )
            }
        }
    }
}
