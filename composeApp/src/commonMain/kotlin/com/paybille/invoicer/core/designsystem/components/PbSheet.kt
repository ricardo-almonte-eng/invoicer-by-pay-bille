package com.paybille.invoicer.core.designsystem.components

import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbMotion
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme

/**
 * Hoja inferior (sustituye a los modales del POS). Va al FINAL del `Box` raíz de la
 * pantalla para quedar encima de todo.
 *
 * Entra deslizándose desde abajo y sale igual (el velo, solo con opacidad): sin rebote ni
 * escala, con la curva de la casa. Cabecera como el modal del diseño: título y cierre, con
 * una línea de 1 dp debajo.
 *
 * El botón o gesto *atrás* la cierra, igual que tocar fuera.
 */
@Composable
fun PbSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = visible,
        onBackCompleted = onDismiss,
    )

    AnimatedVisibility(
        visible = visible,
        // El contenedor no anima nada: cada hijo lleva su propia entrada y salida.
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        modifier = modifier,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Nunca más alta que el 85 % de la pantalla: por arriba siempre se ve la pantalla de
            // debajo (se entiende que es una hoja y que tocar ahí la cierra) y lo largo se
            // desplaza DENTRO de la hoja.
            val maxSheetHeight = maxHeight * MAX_HEIGHT_FRACTION
            Box(
                Modifier
                    .fillMaxSize()
                    .animateEnterExit(
                        enter = fadeIn(tween(PbMotion.SHEET_MS, easing = PbMotion.ease)),
                        exit = fadeOut(tween(PbMotion.MODAL_MS, easing = PbMotion.exit)),
                    )
                    .background(PbTheme.colors.backdrop)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Cerrar",
                        onClick = onDismiss,
                    ),
            )
            val shape = RoundedCornerShape(topStart = PbRadius.xl, topEnd = PbRadius.xl)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .animateEnterExit(
                        enter = slideInVertically(tween(PbMotion.SHEET_MS, easing = PbMotion.ease)) { it },
                        // Sale más rápido de lo que entra: cerrar es una confirmación, no un evento.
                        exit = slideOutVertically(tween(PbMotion.MODAL_MS, easing = PbMotion.exit)) { it },
                    )
                    .widthIn(max = 600.dp)
                    .heightIn(max = maxSheetHeight)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(PbTheme.colors.island, shape)
                    .border(PbControl.border, PbTheme.colors.outline, shape)
                    // Toca dentro de la hoja no la cierra.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .semantics { paneTitle = title }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = PbSpace.s6, end = PbSpace.s3, top = PbSpace.s3, bottom = PbSpace.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PbText(
                        text = title,
                        style = PbTheme.typography.heading,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    PbIconButton(icon = PbSymbols.Close, contentDescription = "Cerrar", onClick = onDismiss)
                }
                Box(Modifier.fillMaxWidth().height(PbControl.border).background(PbTheme.colors.outline))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(PbSpace.s6),
                    verticalArrangement = Arrangement.spacedBy(PbSpace.s5),
                    content = content,
                )
            }
        }
    }
}

private const val MAX_HEIGHT_FRACTION = 0.85f
