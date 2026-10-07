package com.paybille.invoicer.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIColor
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun HtmlView(html: String, modifier: Modifier, interactive: Boolean) {
    // Lo último que se cargó: `update` corre en cada recomposición y recargar parpadea.
    val loaded = remember { arrayOfNulls<String>(1) }
    UIKitView(
        factory = {
            WKWebView(frame = CGRectZero.readValue(), configuration = WKWebViewConfiguration()).apply {
                // Papel: blanco también con el teléfono en oscuro.
                setOpaque(true)
                setBackgroundColor(UIColor.whiteColor)
                scrollView.setBackgroundColor(UIColor.whiteColor)
                scrollView.setScrollEnabled(interactive)
                setUserInteractionEnabled(interactive)
            }
        },
        update = { view ->
            if (loaded[0] != html) {
                loaded[0] = html
                view.loadHTMLString(html, baseURL = null)
            }
        },
        modifier = modifier,
        properties = UIKitInteropProperties(isInteractive = interactive, isNativeAccessibilityEnabled = interactive),
    )
}
