package com.paybille.invoicer.core.platform

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("ClickableViewAccessibility")
@Composable
actual fun HtmlView(html: String, modifier: Modifier, interactive: Boolean) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebViews.prepare()
            WebView(context).apply {
                setBackgroundColor(Color.WHITE)
                // La factura no ejecuta nada: es un documento.
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.setSupportZoom(interactive)
                settings.builtInZoomControls = interactive
                settings.displayZoomControls = false
                isVerticalScrollBarEnabled = interactive
                isHorizontalScrollBarEnabled = false
                if (!interactive) {
                    // La vista previa no consume toques: los recibe el Compose de encima.
                    setOnTouchListener { _, _ -> true }
                    isFocusable = false
                    importantForAccessibility = WebView.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
            }
        },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL(WebViews.BASE_URL, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

internal object WebViews {
    /** Origen ficticio: la factura no carga nada relativo y así no hereda permisos de `file://`. */
    const val BASE_URL = "https://invoicer.local/"

    private var prepared = false

    /**
     * `enableSlowWholeDocumentDraw` dibuja la página ENTERA (no solo lo visible), que es lo que
     * necesita el PDF. Solo vale si se llama antes de crear el primer WebView del proceso.
     */
    fun prepare() {
        if (prepared) return
        prepared = true
        WebView.enableSlowWholeDocumentDraw()
    }
}
