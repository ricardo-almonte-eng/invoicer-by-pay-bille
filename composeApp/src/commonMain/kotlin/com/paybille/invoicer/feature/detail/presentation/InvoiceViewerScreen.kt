package com.paybille.invoicer.feature.detail.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbIconButton
import com.paybille.invoicer.core.designsystem.components.PbSpinner
import com.paybille.invoicer.core.designsystem.components.PbTopBar
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.platform.DocumentPlatform
import com.paybille.invoicer.core.platform.HtmlView
import com.paybille.invoicer.feature.detail.data.InvoicePdfStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * La factura completa en el visor HTML del sistema: se desplaza y se acerca con los dedos.
 * Compartir genera el PDF en el teléfono con lo que se está viendo.
 */
data class InvoiceViewerScreen(val saleId: Int, val html: String, val title: String) : Screen {
    override val key: String = "invoice-viewer-$saleId"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val platform = koinInject<DocumentPlatform>()
        val pdfs = koinInject<InvoicePdfStore>()
        val scope = rememberCoroutineScope()
        var exporting by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

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
                        if (exporting) {
                            Box(Modifier.size(PbControl.h), contentAlignment = Alignment.Center) { PbSpinner(size = 20.dp) }
                        } else {
                            PbIconButton(
                                icon = PbSymbols.Share,
                                contentDescription = "Compartir PDF",
                                onClick = {
                                    exporting = true
                                    error = null
                                    scope.launch {
                                        try {
                                            platform.share(pdfs.write(saleId, html), "application/pdf", title)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            error = "No se pudo generar el PDF: ${e.message ?: "inténtalo otra vez"}."
                                        } finally {
                                            exporting = false
                                        }
                                    }
                                },
                            )
                        }
                    },
                )
                Box(Modifier.fillMaxWidth().height(PbControl.border).background(PbTheme.colors.outline))
            }
            error?.let { PbBanner(message = it, tone = PbBannerTone.Error, modifier = Modifier.windowInsetsPadding(WindowInsets(PbSpace.s4))) }
            HtmlView(
                html = html,
                interactive = true,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
            )
        }
    }
}
