package com.paybille.invoicer.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Respuesta al toque de PayBille: un velo del color de la tinta que aparece y se va.
 * Sin ripple que se expande y sin escalar: "nada rebota, nada escala".
 *
 * El velo se pinta sobre el rectángulo del nodo, así que el componente recorta
 * (`clip(shape)`) ANTES de `clickable`.
 */
internal class PbPressIndication(private val tint: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressVeilNode(interactionSource, tint)

    override fun equals(other: Any?): Boolean = other is PbPressIndication && other.tint == tint
    override fun hashCode(): Int = tint.hashCode()
}

private const val PRESSED_ALPHA = 0.08f

private class PressVeilNode(
    private val source: InteractionSource,
    private val tint: Color,
) : Modifier.Node(), DrawModifierNode {

    private val alpha = Animatable(0f)
    private var animation: Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            val pressed = mutableSetOf<PressInteraction.Press>()
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressed += interaction
                    is PressInteraction.Release -> pressed -= interaction.press
                    is PressInteraction.Cancel -> pressed -= interaction.press
                    else -> return@collect
                }
                val target = if (pressed.isEmpty()) 0f else PRESSED_ALPHA
                animation?.cancel()
                animation = launch {
                    alpha.animateTo(target, tween(PbMotion.CONTROL_MS, easing = PbMotion.ease))
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val value = alpha.value
        if (value > 0f) drawRect(tint.copy(alpha = value))
    }
}
