package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme

/**
 * Tarjeta de una cifra (Resumen, reportes): etiqueta arriba, importe grande con cifras
 * tabulares y una pista pequeña debajo ("12 transacciones"). Borde de 1 dp, sin sombra.
 */
@Composable
fun PbStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    val shape = RoundedCornerShape(PbRadius.lg)
    Column(
        modifier = modifier
            .clip(shape)
            .background(PbTheme.colors.island, shape)
            .border(PbControl.border, PbTheme.colors.outline, shape)
            .padding(PbSpace.s6),
        verticalArrangement = Arrangement.spacedBy(PbSpace.s1),
    ) {
        PbOverline(text = label)
        PbText(text = value, style = PbTheme.typography.amountTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (hint != null) {
            PbText(text = hint, style = PbTheme.typography.caption, color = PbTheme.colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Gráfico de área de una serie (ventas por día), dibujado a mano: sin librerías y con los
 * colores del tema, así se ve bien en claro y en oscuro. Un solo punto se dibuja como una
 * línea plana. `description` es lo que lee el lector de pantalla.
 */
@Composable
fun PbAreaChart(
    values: List<Double>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    val colors = PbTheme.colors
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
    ) {
        val stroke = 2.dp.toPx()
        val top = stroke
        val bottom = size.height - stroke / 2
        // Línea base.
        drawLine(colors.outline, Offset(0f, bottom), Offset(size.width, bottom), PbControl.border.toPx())
        if (values.isEmpty()) return@Canvas

        val max = values.max().coerceAtLeast(0.0)
        val points = if (values.size == 1) listOf(values[0], values[0]) else values
        val stepX = size.width / (points.size - 1)
        fun y(v: Double): Float = if (max <= 0.0) bottom else (bottom - (v / max).toFloat() * (bottom - top))

        val line = Path().apply {
            points.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(i * stepX, y(v)) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, bottom)
            lineTo(0f, bottom)
            close()
        }
        drawPath(area, colors.primary10)
        drawPath(line, colors.primary, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Barra horizontal proporcional (0..1) para rankings: "Productos más vendidos". */
@Composable
fun PbMeterBar(fraction: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(PbRadius.pill)
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(PbTheme.colors.surface2),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(shape)
                .background(PbTheme.colors.primary),
        )
    }
}

/**
 * La caja "TOTAL" del diseño: rótulo a la izquierda, importe grande a la derecha, sobre un
 * relleno apenas más oscuro que la isla y con borde de 1 dp. Es el número que el usuario
 * cobra: se distingue por el relleno, no por el color.
 */
@Composable
fun PbTotalBox(label: String, amount: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(PbRadius.lg)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PbTheme.colors.surface2, shape)
            .border(PbControl.border, PbTheme.colors.outline, shape)
            .padding(horizontal = PbSpace.s6, vertical = PbSpace.s5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PbSpace.s5),
    ) {
        PbOverline(text = label, modifier = Modifier.weight(1f))
        PbText(text = amount, style = PbTheme.typography.amountLarge, textAlign = TextAlign.End, maxLines = 1)
    }
}
