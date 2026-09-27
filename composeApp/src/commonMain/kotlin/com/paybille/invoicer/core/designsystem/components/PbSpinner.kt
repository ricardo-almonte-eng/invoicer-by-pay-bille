package com.paybille.invoicer.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.theme.PbTheme

/** Indicador de carga. Va DENTRO de lo que carga (botón, lista), nunca tapando la pantalla. */
@Composable
fun PbSpinner(
    modifier: Modifier = Modifier,
    color: Color = PbTheme.colors.primary,
    size: Dp = 20.dp,
    strokeWidth: Dp = 2.dp,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    val track = color.copy(alpha = 0.2f)
    Canvas(modifier.size(size).semantics { contentDescription = "Cargando" }) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        drawArc(track, 0f, 360f, useCenter = false, style = stroke)
        rotate(angle) {
            drawArc(color, -90f, 100f, useCenter = false, style = stroke)
        }
    }
}
