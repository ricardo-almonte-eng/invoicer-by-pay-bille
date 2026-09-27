package com.paybille.invoicer.feature.detail.presentation

import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.paybille.invoicer.core.designsystem.components.PbIconButton
import com.paybille.invoicer.core.designsystem.components.PbSpinner
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.components.PbTopBar
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.platform.DocumentPlatform
import org.koin.compose.koinInject

/**
 * El PDF completo, página tras página, al ancho de la pantalla. Para acercar, el botón de
 * compartir lo abre en el visor del sistema.
 */
data class PdfViewerScreen(val path: String, val title: String) : Screen {
    override val key: String = "pdf-viewer-$path"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val platform = koinInject<DocumentPlatform>()
        var pages by remember { mutableStateOf<List<ImageBitmap>?>(null) }

        Column(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
            Column(
                Modifier
                    .background(PbTheme.colors.island)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            ) {
                PbTopBar(
                    title = title,
                    navigation = {
                        PbIconButton(icon = PbSymbols.ArrowBack, contentDescription = "Volver", onClick = { navigator.pop() })
                    },
                    actions = {
                        PbIconButton(
                            icon = PbSymbols.Share,
                            contentDescription = "Compartir PDF",
                            onClick = { platform.share(path, "application/pdf", title) },
                        )
                    },
                )
                Box(Modifier.fillMaxWidth().height(PbControl.border).background(PbTheme.colors.outline))
            }

            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val width = maxWidth.coerceAtMost(900.dp)
                val widthPx = with(LocalDensity.current) { width.roundToPx() }
                LaunchedEffect(widthPx) { pages = platform.renderPdf(path, widthPx) }

                val current = pages
                when {
                    current == null -> PbSpinner(Modifier.align(Alignment.Center))
                    current.isEmpty() -> PbText("El PDF no se pudo abrir.", modifier = Modifier.align(Alignment.Center))
                    else -> LazyColumn(
                        modifier = Modifier.widthIn(max = width).fillMaxSize(),
                        contentPadding = PaddingValues(PbSpace.s6),
                        verticalArrangement = Arrangement.spacedBy(PbSpace.s5),
                    ) {
                        itemsIndexed(current) { index, page ->
                            Image(
                                bitmap = page,
                                contentDescription = "Página ${index + 1} de ${current.size}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(page.width.toFloat() / page.height.toFloat())
                                    .border(PbControl.border, PbTheme.colors.outlineStrong),
                            )
                        }
                    }
                }
            }
        }
    }
}
