package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbIconSize
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.designsystem.theme.PbSymbol

/**
 * Icono pulsable de 44 dp. `contentDescription` es obligatorio: sin texto al lado, es lo
 * único que lee el lector de pantalla.
 *
 * `onClick = null` lo deja visible pero inerte (acciones que todavía no existen).
 */
@Composable
fun PbIconButton(
    icon: PbSymbol,
    contentDescription: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tint: Color = PbTheme.colors.ink2,
) {
    Box(
        modifier = modifier
            .size(PbControl.minTouch)
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        PbIcon(icon = icon, contentDescription = contentDescription, tint = tint, size = PbIconSize.xl)
    }
}
