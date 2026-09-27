package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.designsystem.theme.PbSymbol

/** Lista vacía: icono, frase y, si tiene sentido, una acción. */
@Composable
fun PbEmptyState(
    icon: PbSymbol,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PbSpace.s8, vertical = PbSpace.s10),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PbSpace.s4),
    ) {
        Box(
            Modifier.size(64.dp).background(PbTheme.colors.primary10, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PbIcon(icon = icon, contentDescription = null, tint = PbTheme.colors.primary, size = 32.dp)
        }
        PbText(text = title, style = PbTheme.typography.subtitle, textAlign = TextAlign.Center)
        if (message != null) {
            PbText(
                text = message,
                style = PbTheme.typography.body,
                color = PbTheme.colors.muted,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}
