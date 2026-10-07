package com.paybille.invoicer.feature.detail.data

import com.paybille.invoicer.core.platform.DocumentPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * PDF de la factura para compartir o descargar. Lo GENERA el teléfono a partir del HTML de la
 * factura (`DocumentPlatform.htmlToPdf`), así que siempre dice lo de ahora (el abono de hace un
 * minuto incluido) y no necesita red. Antes lo armaba el servidor con Puppeteer y lo dejaba
 * cacheado en disco: tardaba y se quedaba viejo.
 *
 * Se regenera en cada exportación (cuesta menos de un segundo) en la carpeta privada de la app.
 */
class InvoicePdfStore(private val platform: DocumentPlatform) {
    private val lock = Mutex()

    fun pathFor(saleId: Int): String = "${platform.documentsDir}/factura_$saleId.pdf"

    /** Escribe el PDF de `html` y devuelve su ruta. */
    suspend fun write(saleId: Int, html: String): String = lock.withLock {
        val path = pathFor(saleId)
        platform.htmlToPdf(html, path)
        path
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val dir = Path(platform.documentsDir)
        if (!SystemFileSystem.exists(dir)) return@withContext
        SystemFileSystem.list(dir).forEach { runCatching { SystemFileSystem.delete(it) } }
    }
}
