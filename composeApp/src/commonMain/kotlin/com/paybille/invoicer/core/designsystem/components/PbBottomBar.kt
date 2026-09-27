package com.paybille.invoicer.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbIconSize
import com.paybille.invoicer.core.designsystem.theme.PbMotion
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.designsystem.theme.PbSymbol

/** Un destino de la barra inferior. El activo se dibuja con la variante rellena de `icon`. */
@Immutable
data class PbBottomBarItem(
    val label: String,
    val icon: PbSymbol,
)

/**
 * Barra inferior: isla con borde superior de 1 dp (sin sombra) que respeta la barra de
 * gestos del sistema. El destino activo lleva el icono relleno, el color de acento y una
 * barra de 3 dp en el borde superior que SE DESLIZA de un destino a otro (pedido del
 * usuario, 2026-09-27): el ojo sigue el cambio sin buscarlo.
 *
 * Caben seis destinos a 360 dp (~57 dp cada uno): por eso las etiquetas son de una palabra y
 * el activo no cambia de estilo de texto (en negrita "Productos" ya no cabría).
 */
@Composable
fun PbBottomBar(
    items: List<PbBottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(PbTheme.colors.island),
    ) {
        // Los destinos se reparten a partes iguales (`weight(1f)`), así que la posición de la
        // barra se calcula sin medir: no hay un primer fotograma con la barra en el sitio malo.
        val direction = LocalLayoutDirection.current
        val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
        val start = insets.calculateStartPadding(direction) + BAR_PADDING
        val end = insets.calculateEndPadding(direction) + BAR_PADDING
        val slot = (maxWidth - start - end) / items.size.coerceAtLeast(1)
        val indicatorWidth = slot * INDICATOR_FRACTION
        val indicatorX by animateDpAsState(
            targetValue = start + slot * selectedIndex + (slot - indicatorWidth) / 2,
            animationSpec = tween(PbMotion.INDICATOR_MS, easing = PbMotion.ease),
            label = "bottomBarIndicator",
        )

        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(PbControl.border).background(PbTheme.colors.outline))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = BAR_PADDING, vertical = PbSpace.s2)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    BottomBarEntry(
                        item = item,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Encima de la línea superior: la tapa en el destino activo.
        val indicatorShape = RoundedCornerShape(bottomStart = INDICATOR_HEIGHT, bottomEnd = INDICATOR_HEIGHT)
        Box(
            Modifier
                .offset(x = indicatorX)
                .width(indicatorWidth)
                .height(INDICATOR_HEIGHT)
                .clip(indicatorShape)
                .background(PbTheme.colors.primary, indicatorShape),
        )
    }
}

private val BAR_PADDING = PbSpace.s1
private val INDICATOR_HEIGHT = 3.dp
private const val INDICATOR_FRACTION = 0.5f

@Composable
private fun BottomBarEntry(
    item: PbBottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = PbTheme.colors
    val content by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.muted,
        animationSpec = tween<Color>(PbMotion.CONTROL_MS, easing = PbMotion.ease),
        label = "bottomBarContent",
    )
    Column(
        modifier = modifier
            .widthIn(min = PbControl.minTouch)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(PbRadius.md))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = PbSpace.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PbSpace.s1, Alignment.CenterVertically),
    ) {
        PbIcon(
            icon = item.icon,
            // El texto de abajo ya nombra el destino.
            contentDescription = null,
            tint = content,
            size = PbIconSize.xl,
            filled = selected,
        )
        PbText(
            text = item.label,
            style = PbTheme.typography.caption,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
