package com.paybille.invoicer.core.designsystem.components

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
import com.paybille.invoicer.core.designsystem.theme.PbIconSize
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbol
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme

/**
 * `<CustomSelect>` del POS en móvil: parece un campo (mismo rótulo, alto y borde) pero al
 * tocarlo abre una hoja ([PbOptionSheet]), nunca un desplegable.
 */
@Composable
fun PbSelectField(
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: PbSymbol? = null,
    enabled: Boolean = true,
) {
    val colors = PbTheme.colors
    val shape = RoundedCornerShape(PbRadius.lg)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PbSpace.s2)) {
        PbOverline(text = label, modifier = Modifier.padding(start = PbSpace.s1))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PbControl.field)
                .clip(shape)
                .background(if (enabled) colors.island else colors.surface2, shape)
                .border(PbControl.border, colors.outlineStrong, shape)
                .clickable(enabled = enabled, role = Role.DropdownList, onClickLabel = "Elegir $label", onClick = onClick)
                .padding(start = PbSpace.s5, end = PbSpace.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PbSpace.s4),
        ) {
            if (leadingIcon != null) PbIcon(icon = leadingIcon, contentDescription = null, tint = colors.muted)
            PbText(
                text = value ?: placeholder,
                style = PbTheme.typography.input,
                color = when {
                    !enabled -> colors.disabledFg
                    value == null -> colors.muted2
                    else -> colors.ink
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PbIcon(icon = PbSymbols.ExpandMore, contentDescription = null, tint = colors.muted, size = PbIconSize.lg)
        }
    }
}
