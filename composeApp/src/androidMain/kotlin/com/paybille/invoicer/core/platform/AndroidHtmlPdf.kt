package com.paybille.invoicer.core.platform

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONTokener
import java.io.File

/**
 * HTML → PDF A4 con un WebView fuera de pantalla, sin diálogo de impresión: se maqueta la
 * página a 794 px CSS (el ancho de un A4 a 96 dpi), se dibuja en un [PdfDocument] y se corta
 * en páginas por los bordes de las filas y bloques que dice el propio HTML.
 *
 * El servicio de impresión (`createPrintDocumentAdapter`) pagina mejor, pero solo escribe a un
 * archivo con callbacks ocultos del SDK; esto usa solo API pública.
 */
internal class AndroidHtmlPdf(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun write(html: String, outputPath: String) = withContext(Dispatchers.Main) {
        WebViews.prepare()
        val density = context.resources.displayMetrics.density
        val widthPx = (PAGE_CSS_WIDTH * density).toInt()
        val webView = WebView(context)
        try {
            webView.setBackgroundColor(Color.WHITE)
            // Solo para medir dónde se puede cortar; la plantilla no trae scripts propios.
            webView.settings.javaScriptEnabled = true
            webView.settings.allowFileAccess = false
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

            val loaded = CompletableDeferred<Unit>()
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    loaded.complete(Unit)
                }
            }
            layout(webView, widthPx, (PAGE_CSS_HEIGHT * density).toInt())
            webView.loadDataWithBaseURL(WebViews.BASE_URL, html, "text/html", "utf-8", null)
            withTimeout(LOAD_TIMEOUT_MS) { loaded.await() }

            // Alto real del documento y bordes inferiores de filas y bloques, en px CSS.
            val metrics = evaluate(webView, MEASURE_JS)
            // evaluateJavascript devuelve el resultado como JSON: el texto de JSON.stringify llega entre comillas.
            val json = JSONArray(JSONTokener(metrics).nextValue() as? String ?: "[0]")
            val heightCss = json.getDouble(0)
            val breaks = (1 until json.length()).map { (json.getDouble(it) * density).toInt() }.sorted()
            val heightPx = (heightCss * density).toInt().coerceAtLeast(1)

            layout(webView, widthPx, heightPx)
            awaitVisualState(webView)

            val scale = PAGE_W_PT / widthPx.toFloat()
            val pageHeightPx = (PAGE_H_PT / scale).toInt()
            val topMarginPx = (CONTINUATION_TOP_CSS * density).toInt()
            val bottomMarginPx = (BOTTOM_MARGIN_CSS * density).toInt()

            val document = PdfDocument()
            try {
                var start = 0
                var pageNumber = 1
                while (start < heightPx) {
                    val top = if (pageNumber == 1) 0 else topMarginPx
                    val room = pageHeightPx - top - bottomMarginPx
                    var end = (start + room).coerceAtMost(heightPx)
                    if (end < heightPx) {
                        // El último borde que cabe, si no deja la página casi vacía.
                        breaks.lastOrNull { it in (start + room / 3)..end }?.let { end = it }
                    }
                    val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W_PT, PAGE_H_PT, pageNumber).create())
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    canvas.scale(scale, scale)
                    canvas.translate(0f, (top - start).toFloat())
                    canvas.clipRect(0, start, widthPx, end)
                    webView.draw(canvas)
                    document.finishPage(page)
                    start = end
                    pageNumber++
                    if (pageNumber > MAX_PAGES) break
                }
                withContext(Dispatchers.IO) {
                    val target = File(outputPath)
                    target.parentFile?.mkdirs()
                    val temp = File("$outputPath.part")
                    temp.outputStream().use { document.writeTo(it) }
                    if (!temp.renameTo(target)) {
                        target.delete()
                        check(temp.renameTo(target)) { "No se pudo guardar el PDF." }
                    }
                }
            } finally {
                document.close()
            }
        } finally {
            webView.destroy()
        }
    }

    private fun layout(view: WebView, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    private suspend fun evaluate(view: WebView, script: String): String {
        val result = CompletableDeferred<String>()
        view.evaluateJavascript(script) { result.complete(it ?: "[]") }
        return withTimeout(LOAD_TIMEOUT_MS) { result.await() }
    }

    /** Espera a que el WebView haya pintado lo último que se le pidió (tras el nuevo alto). */
    private suspend fun awaitVisualState(view: WebView) {
        val ready = CompletableDeferred<Unit>()
        view.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                ready.complete(Unit)
            }
        })
        withTimeout(LOAD_TIMEOUT_MS) { ready.await() }
    }

    private companion object {
        const val PAGE_CSS_WIDTH = 794
        const val PAGE_CSS_HEIGHT = 1123
        const val PAGE_W_PT = 595 // A4 en puntos
        const val PAGE_H_PT = 842
        const val CONTINUATION_TOP_CSS = 36
        const val BOTTOM_MARGIN_CSS = 24
        const val MAX_PAGES = 30
        const val LOAD_TIMEOUT_MS = 15_000L

        /** `[altoDelDocumento, borde, borde, …]` en px CSS. */
        val MEASURE_JS = """
            (function () {
              // El alto del CONTENIDO (body), no el del documento: este nunca baja del alto de la ventana.
              var out = [Math.ceil(document.body.getBoundingClientRect().bottom + window.scrollY)];
              var els = document.querySelectorAll('tr, .blk');
              for (var i = 0; i < els.length; i++) {
                var r = els[i].getBoundingClientRect();
                out.push(Math.ceil(r.bottom + window.scrollY));
              }
              return JSON.stringify(out);
            })();
        """.trimIndent()
    }
}
