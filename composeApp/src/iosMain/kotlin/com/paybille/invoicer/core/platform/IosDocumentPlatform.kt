package com.paybille.invoicer.core.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIScreen
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
class IosDocumentPlatform : DocumentPlatform {

    override val documentsDir: String
        get() {
            val base = NSFileManager.defaultManager.URLForDirectory(
                directory = NSApplicationSupportDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            )?.path ?: error("No se pudo abrir Application Support")
            val dir = "$base/invoices"
            NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
            return dir
        }

    override suspend fun renderPdf(path: String, widthPx: Int, maxPages: Int): List<ImageBitmap> =
        withContext(Dispatchers.IO) {
            val document = PDFDocument(uRL = NSURL.fileURLWithPath(path)) ?: return@withContext emptyList()
            // PDFKit mide en puntos: se divide por la escala de pantalla para no pintar 3 veces
            // más píxeles de los que se van a ver.
            val screenScale = UIScreen.mainScreen.scale
            val widthPt = widthPx / screenScale
            val count = minOf(document.pageCount.toInt(), maxPages)
            (0 until count).mapNotNull { index ->
                val page = document.pageAtIndex(index.toULong()) ?: return@mapNotNull null
                val (pageW, pageH) = page.boundsForBox(kPDFDisplayBoxMediaBox).useContents { size.width to size.height }
                if (pageW <= 0.0) return@mapNotNull null
                val image = page.thumbnailOfSize(CGSizeMake(widthPt, widthPt * pageH / pageW), kPDFDisplayBoxMediaBox)
                val png = UIImagePNGRepresentation(image) ?: return@mapNotNull null
                Image.makeFromEncoded(png.toByteArray()).toComposeImageBitmap()
            }
        }

    override suspend fun htmlToPdf(html: String, outputPath: String) = IosHtmlPdf.write(html, outputPath)

    override fun share(path: String, mimeType: String, title: String) {
        val controller = UIActivityViewController(
            activityItems = listOf(NSURL.fileURLWithPath(path)),
            applicationActivities = null,
        )
        present(controller)
    }

    override fun shareText(text: String, title: String) {
        present(UIActivityViewController(activityItems = listOf(text), applicationActivities = null))
    }

    override suspend fun saveCopy(path: String, fileName: String, mimeType: String): SaveResult {
        // El selector de Archivos: el usuario elige carpeta (iCloud, En mi iPhone…).
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(path)), asCopy = true)
        present(picker)
        return SaveResult.PickerShown
    }

    private fun present(controller: UIViewController) {
        dispatch_async(dispatch_get_main_queue()) {
            val top = topViewController() ?: return@dispatch_async
            // En iPad la hoja de compartir es un popover y necesita de dónde colgar.
            controller.popoverPresentationController?.sourceView = top.view
            top.presentViewController(controller, animated = true, completion = null)
        }
    }

    private fun topViewController(): UIViewController? {
        @Suppress("DEPRECATION")
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
