package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.theme.PbColors
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme

/** Tonos de etiqueta (`<CustomTags>` del POS). No es pulsable. */
enum class PbTagTone { Success, Warning, Neutral, Danger, Info }

private data class TagColors(val container: Color, val border: Color, val content: Color)

private fun PbColors.tag(tone: PbTagTone) = when (tone) {
    PbTagTone.Success -> TagColors(success10, successBorder, success)
    // El naranja es la señal de "falta dinero": la guía 06 lo reserva para "Pendiente".
    PbTagTone.Warning -> TagColors(accent10, accentBorder, accentText)
    PbTagTone.Neutral -> TagColors(surface3, outlineStrong, muted)
    PbTagTone.Danger -> TagColors(error10, errorBorder, error)
    PbTagTone.Info -> TagColors(primary10, primaryBorder, primary)
}

@Composable
fun PbTag(text: String, tone: PbTagTone, modifier: Modifier = Modifier) {
    val c = PbTheme.colors.tag(tone)
    val shape = RoundedCornerShape(50)
    PbText(
        text = text,
        style = PbTheme.typography.label,
        color = c.content,
        maxLines = 1,
        modifier = modifier
            .heightIn(min = 24.dp)
            .background(c.container, shape)
            .border(PbControl.border, c.border, shape)
            .padding(horizontal = PbSpace.s4, vertical = 3.dp),
    )
}
