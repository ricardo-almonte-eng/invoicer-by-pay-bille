package com.paybille.invoicer.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbMotion
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Pestañas de texto con subrayado, alineadas a la izquierda y desplazables si no caben.
 *
 * El subrayado SE DESLIZA de una pestaña a otra, igual que la barra de la barra inferior
 * (pedido del usuario, 2026-09-27). Mide lo que ocupa el texto de cada pestaña, así que el
 * primer fotograma lo coloca sin animar y solo los cambios posteriores se deslizan.
 */
@Composable
fun PbTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Posición y ancho del TEXTO de cada pestaña, en píxeles, dentro de la fila.
    val slots = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    // Coordenadas del contenido desplazable: las del texto se miden contra ellas, así que
    // desplazar la fila no mueve nada ni vuelve a medir.
    val container = remember { ContainerRef() }
    val x = remember { Animatable(0f) }
    val width = remember { Animatable(0f) }
    val target = slots[selectedIndex]

    LaunchedEffect(target) {
        val (tx, tw) = target ?: return@LaunchedEffect
        val spec = tween<Float>(PbMotion.INDICATOR_MS, easing = PbMotion.ease)
        if (width.value == 0f) {
            x.snapTo(tx.toFloat())
            width.snapTo(tw.toFloat())
        } else {
            coroutineScope {
                launch { x.animateTo(tx.toFloat(), spec) }
                launch { width.animateTo(tw.toFloat(), spec) }
            }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = PbSpace.s3)
                .onPlaced { container.coordinates = it },
        ) {
            Row(Modifier.selectableGroup()) {
                tabs.forEachIndexed { index, label ->
                    PbTab(
                        label = label,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        onTextPlaced = { coordinates ->
                            val origin = container.coordinates ?: return@PbTab
                            val slot = origin.localPositionOf(coordinates, Offset.Zero).x.roundToInt() to coordinates.size.width
                            if (slots[index] != slot) slots[index] = slot
                        },
                    )
                }
            }
            if (width.value > 0f) {
                val density = LocalDensity.current
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset(x.value.roundToInt(), 0) }
                        .width(with(density) { width.value.toDp() })
                        .height(INDICATOR_HEIGHT)
                        .clip(RoundedCornerShape(topStart = INDICATOR_HEIGHT, topEnd = INDICATOR_HEIGHT))
                        .background(PbTheme.colors.primary),
                )
            }
        }
        // Línea base de la fila, bajo el subrayado.
        Box(
            Modifier
                .fillMaxWidth()
                .height(PbControl.border)
                .background(PbTheme.colors.outline),
        )
    }
}

private val INDICATOR_HEIGHT = 3.dp

private class ContainerRef {
    var coordinates: LayoutCoordinates? = null
}

@Composable
private fun PbTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onTextPlaced: (LayoutCoordinates) -> Unit,
) {
    val colors = PbTheme.colors
    val spec = tween<Color>(PbMotion.CONTROL_MS, easing = PbMotion.ease)
    val textColor by animateColorAsState(if (selected) colors.primary else colors.muted, spec, label = "tabText")

    Box(
        modifier = Modifier
            .heightIn(min = PbControl.h)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = PbSpace.s5),
        contentAlignment = Alignment.Center,
    ) {
        PbText(
            text = label,
            style = PbTheme.typography.subtitle,
            color = textColor,
            maxLines = 1,
            modifier = Modifier
                .padding(bottom = INDICATOR_HEIGHT)
                .onGloballyPositioned(onTextPlaced),
        )
    }
}
