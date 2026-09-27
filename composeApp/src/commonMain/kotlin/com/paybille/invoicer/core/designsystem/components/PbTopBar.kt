package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme

/**
 * Barra superior. Sin fondo ni borde propios: la pone el contenedor (en el Inicio, la
 * isla de cabecera). `navigation` va a la izquierda, `actions` a la derecha.
 */
@Composable
fun PbTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = PbSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PbSpace.s2),
    ) {
        navigation()
        if (title != null) {
            PbText(
                text = title,
                style = PbTheme.typography.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions()
    }
}

/**
 * Cabecera de los destinos: `leading` a la izquierda, `actions` a la derecha y `center`
 * CENTRADO EN LA PANTALLA, no en el hueco que queda (si a un lado hay un icono y al otro dos,
 * el centro no se corre). El centro recibe el ancho que deja el lado más ancho, por los dos
 * lados, y se recorta con puntos suspensivos si no cabe.
 */
@Composable
fun PbAppBar(
    leading: @Composable () -> Unit,
    center: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = PbSpace.s3),
        content = {
            Box(contentAlignment = Alignment.CenterStart) { leading() }
            Box(contentAlignment = Alignment.Center) { center() }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val start = measurables[0].measure(loose)
        val end = measurables[2].measure(loose)
        val gap = PbSpace.s2.roundToPx()
        val side = maxOf(start.width, end.width) + gap
        val centerWidth = (constraints.maxWidth - side * 2).coerceAtLeast(0)
        val middle = measurables[1].measure(loose.copy(maxWidth = centerWidth))
        val height = maxOf(start.height, middle.height, end.height, constraints.minHeight)
        layout(constraints.maxWidth, height) {
            start.placeRelative(0, (height - start.height) / 2)
            middle.placeRelative((constraints.maxWidth - middle.width) / 2, (height - middle.height) / 2)
            end.placeRelative(constraints.maxWidth - end.width, (height - end.height) / 2)
        }
    }
}
