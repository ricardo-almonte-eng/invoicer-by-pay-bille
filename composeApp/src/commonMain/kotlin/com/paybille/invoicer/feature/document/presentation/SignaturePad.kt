package com.paybille.invoicer.feature.document.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.feature.document.domain.SignaturePoint

/**
 * Lienzo para firmar con el dedo. Es papel (blanco con tinta oscura también en tema oscuro).
 * Los puntos se guardan en el sistema del `viewBox` ([SIGNATURE_W] × [SIGNATURE_H]), no en
 * píxeles: la firma sale igual en cualquier pantalla y en el PDF.
 */
@Composable
fun SignaturePad(
    strokes: List<List<SignaturePoint>>,
    onStrokesChange: (List<List<SignaturePoint>>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PbTheme.colors
    val shape = RoundedCornerShape(PbRadius.md)
    val current by rememberUpdatedState(strokes)
    val onChange by rememberUpdatedState(onStrokesChange)
    var size by remember { mutableStateOf(IntSize.Zero) }
    // El trazo que se está dibujando: se pinta al momento y se entrega al levantar el dedo.
    var live by remember { mutableStateOf<List<SignaturePoint>>(emptyList()) }

    fun toPoint(offset: Offset): SignaturePoint {
        val w = size.width.coerceAtLeast(1)
        val h = size.height.coerceAtLeast(1)
        return SignaturePoint(
            x = (offset.x / w * SIGNATURE_W).coerceIn(0f, SIGNATURE_W),
            y = (offset.y / h * SIGNATURE_H).coerceIn(0f, SIGNATURE_H),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(SIGNATURE_W / SIGNATURE_H)
            .clip(shape)
            .background(colors.logoPlate)
            .border(PbControl.border, colors.outlineStrong, shape)
            .semantics { contentDescription = "Lienzo para la firma. Dibuja con el dedo." },
    ) {
        if (strokes.isEmpty() && live.isEmpty()) {
            PbText(
                text = "Firma aquí",
                style = PbTheme.typography.caption,
                color = colors.muted2,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        // La línea de firma, como en el papel.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = PbSpace.s8, vertical = PbSpace.s6)
                .fillMaxWidth()
                .height(PbControl.border)
                .background(colors.outlineStrong),
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    size = this.size
                    detectTapGestures { offset ->
                        val p = toPoint(offset)
                        onChange(current + listOf(listOf(p)))
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    size = this.size
                    detectDragGestures(
                        onDragStart = { offset -> live = listOf(toPoint(offset)) },
                        onDrag = { change, _ ->
                            change.consume()
                            live = live + toPoint(change.position)
                        },
                        onDragEnd = {
                            if (live.isNotEmpty()) onChange(current + listOf(live))
                            live = emptyList()
                        },
                        onDragCancel = {
                            if (live.isNotEmpty()) onChange(current + listOf(live))
                            live = emptyList()
                        },
                    )
                },
        ) {
            val sx = this.size.width / SIGNATURE_W
            val sy = this.size.height / SIGNATURE_H
            val stroke = Stroke(width = 2.4f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            (strokes + listOf(live)).filter { it.isNotEmpty() }.forEach { points ->
                val path = Path().apply {
                    moveTo(points[0].x * sx, points[0].y * sy)
                    if (points.size == 1) lineTo(points[0].x * sx + 0.1f, points[0].y * sy + 0.1f)
                    points.drop(1).forEach { lineTo(it.x * sx, it.y * sy) }
                }
                drawPath(path, colors.paperInk, style = stroke)
            }
        }
    }
}
