package com.paybille.invoicer.core.designsystem.components

import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.designsystem.theme.PbSymbol

/**
 * Fila pulsable. Regla de listas del POS portada a móvil: **tres datos como mucho**
 * (título, detalle y lo que vaya a la derecha); el resto vive en el detalle.
 */
@Composable
fun PbListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: PbSymbol? = null,
    /** Sustituye a `leadingIcon` (p. ej. el logo del banco). */
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    /** Texto a la derecha en lugar del chevron (p. ej. "Próximamente"). */
    trailingText: String? = null,
) {
    val colors = PbTheme.colors
    val shape = RoundedCornerShape(PbRadius.md)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PbControl.h + PbSpace.s3)
            .clip(shape)
            .background(colors.island, shape)
            .border(PbControl.border, colors.outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = PbSpace.s5, vertical = PbSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PbSpace.s5),
    ) {
        when {
            leading != null -> leading()
            leadingIcon != null -> PbIcon(icon = leadingIcon, contentDescription = null, tint = if (enabled) colors.primary else colors.muted2)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PbSpace.s1)) {
            PbText(
                text = title,
                style = PbTheme.typography.subtitle,
                color = if (enabled) colors.ink else colors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                PbText(
                    text = subtitle,
                    style = PbTheme.typography.caption,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingText != null) {
            PbText(text = trailingText, style = PbTheme.typography.caption, color = colors.muted, maxLines = 1)
        } else if (enabled) {
            PbIcon(icon = PbSymbols.ChevronRight, contentDescription = null, tint = colors.muted2)
        }
    }
}
