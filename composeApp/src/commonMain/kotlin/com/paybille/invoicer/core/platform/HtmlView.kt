package com.paybille.invoicer.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Visor de HTML del sistema (Android `WebView`, iOS `WKWebView`) para la factura que genera la
 * app. Fondo blanco siempre: es papel, no tema.
 *
 * `interactive = false` es la vista previa del detalle: no hace scroll ni zoom, y los toques
 * los recibe quien esté encima (para abrir el visor completo). Con `true`, desplazar y
 * pellizcar para acercar.
 */
@Composable
expect fun HtmlView(html: String, modifier: Modifier = Modifier, interactive: Boolean = true)
