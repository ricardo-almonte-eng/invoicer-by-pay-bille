package com.paybille.invoicer.core.designsystem.components

import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.designsystem.theme.PbSymbol

enum class PbBannerTone { Error, Success, Offline }

/**
 * Mensaje en línea, dentro de la pantalla. Sustituye al `MessageBox` global del POS:
 * aquí el aviso vive junto a lo que lo provocó.
 */
@Composable
fun PbBanner(
    message: String,
    tone: PbBannerTone,
    modifier: Modifier = Modifier,
) {
    val colors = PbTheme.colors
    val (container, border, content, icon) = when (tone) {
        PbBannerTone.Error -> BannerStyle(colors.error10, colors.errorBorder, colors.error, PbSymbols.Error)
        PbBannerTone.Success ->
            BannerStyle(colors.success10, colors.successBorder, colors.success, PbSymbols.CheckCircle)
        PbBannerTone.Offline -> BannerStyle(colors.surface2, colors.outlineStrong, colors.ink2, PbSymbols.CloudOff)
    }
    val shape = RoundedCornerShape(PbRadius.md)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container, shape)
            .border(PbControl.border, border, shape)
            .padding(horizontal = PbSpace.s5, vertical = PbSpace.s4)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PbSpace.s4),
    ) {
        PbIcon(icon = icon, contentDescription = null, tint = content)
        PbText(text = message, style = PbTheme.typography.bodyStrong, color = content, modifier = Modifier.weight(1f))
    }
}

private data class BannerStyle(
    val container: Color,
    val border: Color,
    val content: Color,
    val icon: PbSymbol,
)
