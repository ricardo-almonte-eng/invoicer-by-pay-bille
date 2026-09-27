package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme

/**
 * Lienzo de una pantalla: fondo `canvas`, respeta barras del sistema y teclado, hace
 * scroll y limita el ancho para que en tablet el contenido no se estire de lado a lado.
 *
 * `centered = true` centra verticalmente cuando el contenido cabe (login); si no cabe,
 * hace scroll como cualquier otra pantalla.
 */
@Composable
fun PbScreen(
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    maxWidth: Dp = PbControl.maxFormWidth,
    /** `false` cuando encima hay una barra que ya respeta la barra de estado. */
    insetTop: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PbTheme.colors.canvas)
            // safeDrawing = barras del sistema + recorte de cámara + TECLADO.
            .windowInsetsPadding(
                if (insetTop) {
                    WindowInsets.safeDrawing
                } else {
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                },
            ),
    ) {
        // Dentro de un scroll la altura es infinita y `Arrangement.Center` no centra nada:
        // se fija una altura mínima igual a la visible para que sí lo haga.
        val visibleHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = visibleHeight)
                    .padding(horizontal = PbSpace.s6, vertical = PbSpace.s8),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (centered) Arrangement.Center else Arrangement.Top,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(PbSpace.s6),
                    content = content,
                )
            }
        }
    }
}
