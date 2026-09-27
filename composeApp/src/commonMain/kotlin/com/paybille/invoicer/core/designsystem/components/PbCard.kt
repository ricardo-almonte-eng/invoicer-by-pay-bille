package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme

/**
 * La "isla" de PayBille: blanca sobre el lienzo azulado y delimitada por un borde de 1 dp.
 * Radio 12 como las islas del diseño (el `.CustomCard` del POS usa 10), padding 20.
 * **Nunca sombra** — ni `shadow()` ni elevación. La jerarquía la dan borde y relleno.
 */
@Composable
fun PbCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(PbSpace.s7),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(PbSpace.s5),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(PbRadius.lg)
    Column(
        modifier = modifier
            .clip(shape)
            .background(PbTheme.colors.island, shape)
            .border(PbControl.border, PbTheme.colors.outline, shape)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}
