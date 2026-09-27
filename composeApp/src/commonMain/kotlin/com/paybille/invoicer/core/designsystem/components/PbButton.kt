package com.paybille.invoicer.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.paybille.invoicer.core.designsystem.theme.PbColors
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbMotion
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.designsystem.theme.PbSymbol

/**
 * Variantes de botón. Regla del POS: **como mucho dos botones llenos por pantalla**
 * (`Fill` y, si acaso, `Accent`); todo lo demás es `Outline` o `Ghost`.
 */
enum class PbButtonVariant { Fill, Outline, Ghost, Danger, Accent }

@Immutable
private data class ButtonColors(val container: Color, val content: Color, val border: Color)

private fun PbColors.forVariant(variant: PbButtonVariant, enabled: Boolean): ButtonColors {
    if (!enabled) {
        return when (variant) {
            PbButtonVariant.Ghost -> ButtonColors(Color.Transparent, disabledFg, Color.Transparent)
            else -> ButtonColors(surface2, disabledFg, outline)
        }
    }
    return when (variant) {
        PbButtonVariant.Fill -> ButtonColors(btnFill, btnText, btnFill)
        // Secundario del diseño: tinta 2 sobre la isla; el contorno no compite con el relleno.
        PbButtonVariant.Outline -> ButtonColors(island, ink2, outlineStrong)
        PbButtonVariant.Ghost -> ButtonColors(Color.Transparent, primary, Color.Transparent)
        // "Borrar" / "Apagar" del diseño: contorno rojo claro, texto rojo, sin relleno.
        PbButtonVariant.Danger -> ButtonColors(island, error, errorBorder)
        PbButtonVariant.Accent -> ButtonColors(accent, btnText, accent)
    }
}

/**
 * Botón de 48 dp de alto (mínimo táctil). Con `loading` muestra el indicador DENTRO del
 * botón y deja de responder, pero no cambia de tamaño: el usuario no pierde el sitio.
 */
@Composable
fun PbButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PbButtonVariant = PbButtonVariant.Fill,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: PbSymbol? = null,
) {
    val palette = PbTheme.colors.forVariant(variant, enabled)
    val spec = tween<Color>(PbMotion.CONTROL_MS, easing = PbMotion.ease)
    val container by animateColorAsState(palette.container, spec, label = "container")
    val content by animateColorAsState(palette.content, spec, label = "content")
    val border by animateColorAsState(palette.border, spec, label = "border")
    val shape = RoundedCornerShape(PbRadius.md)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = PbControl.h, minWidth = PbControl.h)
            .clip(shape)
            .background(container, shape)
            .border(PbControl.border, border, shape)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = PbSpace.s6),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.alpha(if (loading) 0f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PbSpace.s3),
        ) {
            if (leadingIcon != null) {
                PbIcon(icon = leadingIcon, contentDescription = null, tint = content)
            }
            PbText(
                text = text,
                style = PbTheme.typography.button,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (loading) PbSpinner(color = content)
    }
}
