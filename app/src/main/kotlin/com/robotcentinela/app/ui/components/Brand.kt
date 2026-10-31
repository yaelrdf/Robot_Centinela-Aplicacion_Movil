package com.robotcentinela.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robotcentinela.app.R
import com.robotcentinela.app.ui.theme.Poppins
import com.robotcentinela.app.ui.theme.Spacing
import com.robotcentinela.app.ui.theme.VisorPalette

/**
 * Isotipo V.I.S.O.R. (Manual de identidad, §3). Se dibuja siempre con sus colores originales y sin
 * deformarlo (pág. 16): [onDark] elige la variante de círculo claro para fondos azules; la de círculo
 * azul es para fondos claros. Es decorativo: el nombre de la marca va en el texto vecino.
 */
@Composable
fun VisorMark(size: Dp, modifier: Modifier = Modifier, onDark: Boolean = true) {
    Image(
        painter = painterResource(if (onDark) R.drawable.ic_visor_isotipo_inverso else R.drawable.ic_visor_isotipo),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/**
 * Pantalla de bienvenida de la marca (Manual de identidad, pág. 27): fondo Black Blue con el isotipo,
 * el nombre, el lema y "Bienvenido". Se muestra mientras se recupera la sesión, igual en ambos temas.
 */
@Composable
fun VisorSplash(modifier: Modifier = Modifier, status: String? = null) {
    Column(
        modifier.fillMaxSize().background(VisorPalette.BlackBlue).padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VisorMark(size = 132.dp)
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
            color = VisorPalette.OffWhite,
        )
        Text(
            stringResource(R.string.visor_lema),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = VisorPalette.OffWhite,
            textAlign = TextAlign.Center,
        )
        Text(
            "BIENVENIDO",
            style = MaterialTheme.typography.labelSmall,
            color = VisorPalette.OffWhite,
            modifier = Modifier.padding(top = Spacing.s),
        )
        if (status != null) {
            CircularProgressIndicator(
                color = VisorPalette.BrightOrange,
                strokeWidth = 3.dp,
                modifier = Modifier.padding(top = Spacing.l).size(28.dp),
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = VisorPalette.OffWhite.copy(alpha = 0.8f))
        }
    }
}
