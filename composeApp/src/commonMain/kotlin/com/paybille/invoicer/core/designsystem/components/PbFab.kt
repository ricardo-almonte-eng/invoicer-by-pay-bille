package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.designsystem.theme.PbSymbol

/** Tamaño del botón flotante. Las listas reservan este alto (+ márgenes) al final. */
val PbFabSize = 56.dp

/** Hueco que deja al final toda lista con el "+" encima, para que no tape la última fila. */
val PbFabClearance = PbFabSize + PbSpace.s6 * 2

/**
 * Botón flotante de la acción principal ("+"). Cuenta como uno de los dos botones llenos
 * de la pantalla. **Sin sombra**, aunque flote: lo separa de la lista un borde del lienzo.
 */
@Composable
fun PbFab(
    icon: PbSymbol,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PbTheme.colors
    Box(
        modifier = modifier
            .size(PbFabSize)
            .clip(CircleShape)
            .background(colors.btnFill, CircleShape)
            // Anillo del color del lienzo: separa el botón de la fila que tenga debajo.
            .border(PbControl.borderFocus, colors.canvas, CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PbIcon(icon = icon, contentDescription = contentDescription, tint = colors.btnText, size = 28.dp)
    }
}
