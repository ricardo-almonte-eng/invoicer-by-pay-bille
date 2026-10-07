package com.paybille.invoicer.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.CoreGraphics.CGRectInset
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSMakeRange
import platform.Foundation.NSMutableData
import platform.Foundation.NSValue
import platform.Foundation.setValue
import platform.Foundation.writeToFile
import platform.UIKit.UIGraphicsBeginPDFContextToData
import platform.UIKit.UIGraphicsBeginPDFPage
import platform.UIKit.UIGraphicsEndPDFContext
import platform.UIKit.UIGraphicsGetPDFContextBounds
import platform.UIKit.UIPrintPageRenderer
import platform.UIKit.valueWithCGRect
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * HTML → PDF A4 con `UIPrintPageRenderer` sobre un `WKWebView` fuera de pantalla: el
 * motor de impresión de WebKit pagina él mismo y respeta los cortes del CSS.
 *
 * ⚠️ Pendiente de verificar en Xcode (no compila en Windows).
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosHtmlPdf {

    suspend fun write(html: String, outputPath: String) = withContext(Dispatchers.Main) {
        val webView = WKWebView(frame = CGRectMake(0.0, 0.0, PAGE_W, PAGE_H), configuration = WKWebViewConfiguration())
        val loaded = CompletableDeferred<Unit>()
        val delegate = object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                loaded.complete(Unit)
            }
        }
        // WKWebView guarda el delegado como referencia débil: se retiene aquí mientras dura.
        activeDelegate = delegate
        webView.navigationDelegate = delegate
        webView.loadHTMLString(html, baseURL = null)
        withTimeout(LOAD_TIMEOUT_MS) { loaded.await() }

        val renderer = UIPrintPageRenderer()
        renderer.addPrintFormatter(webView.viewPrintFormatter(), startingAtPageAtIndex = 0)
        val paper = CGRectMake(0.0, 0.0, PAGE_W, PAGE_H)
        // Los márgenes laterales los pone la plantilla; arriba y abajo, el papel.
        val printable = CGRectInset(paper, 0.0, MARGIN_V)
        renderer.setValue(NSValue.valueWithCGRect(paper), forKey = "paperRect")
        renderer.setValue(NSValue.valueWithCGRect(printable), forKey = "printableRect")

        val data = NSMutableData()
        UIGraphicsBeginPDFContextToData(data, paper, null)
        val pages = renderer.numberOfPages
        renderer.prepareForDrawingPages(NSMakeRange(0u, pages.toULong()))
        val bounds = UIGraphicsGetPDFContextBounds()
        for (index in 0 until pages) {
            UIGraphicsBeginPDFPage()
            renderer.drawPageAtIndex(index, inRect = bounds)
        }
        UIGraphicsEndPDFContext()
        webView.navigationDelegate = null
        activeDelegate = null
        check(data.writeToFile(outputPath, atomically = true)) { "No se pudo guardar el PDF." }
    }

    private var activeDelegate: NSObject? = null

    private const val PAGE_W = 595.2 // A4 en puntos
    private const val PAGE_H = 841.8
    private const val MARGIN_V = 28.0
    private const val LOAD_TIMEOUT_MS = 15_000L
}
