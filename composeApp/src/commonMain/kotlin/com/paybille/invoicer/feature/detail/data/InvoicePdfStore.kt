package com.paybille.invoicer.feature.detail.data

import com.paybille.invoicer.core.platform.DocumentPlatform
import com.paybille.invoicer.feature.detail.data.remote.DetailRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * PDF de la "factura grande" (`GET ventas/factura/{id}`, formato A4 del servidor), guardado
 * en la carpeta privada de la app para verlo y compartirlo sin red.
 *
 * ⚠️ El servidor GUARDA el PDF la primera vez (`pdf.js → savePDF`: si el archivo existe, lo
 * devuelve sin regenerar). Si la factura cambia después —un abono, por ejemplo— el PDF del
 * servidor sigue diciendo lo de antes. Descargarlo otra vez no lo arregla: es la petición 7
 * al backend (guía 11).
 */
class InvoicePdfStore(
    private val remote: DetailRemoteDataSource,
    private val platform: DocumentPlatform,
) {
    private val lock = Mutex()

    fun fileName(saleId: Int) = "factura_$saleId.pdf"

    fun pathFor(saleId: Int): String = "${platform.documentsDir}/${fileName(saleId)}"

    fun exists(saleId: Int): Boolean = SystemFileSystem.exists(Path(pathFor(saleId)))

    /**
     * Devuelve la ruta local del PDF, descargándolo si no está (o si `refresh`).
     * @throws com.paybille.invoicer.core.network.ApiException sin red o si el servidor falla.
     */
    suspend fun ensure(saleId: Int, refresh: Boolean = false): String = lock.withLock {
        val path = pathFor(saleId)
        if (!refresh && exists(saleId)) return@withLock path
        val bytes = remote.invoicePdf(saleId)
        withContext(Dispatchers.IO) {
            // Se escribe a un temporal y se renombra: un PDF a medio escribir no se ve nunca.
            val temp = Path("$path.part")
            SystemFileSystem.sink(temp).buffered().use { it.write(bytes) }
            SystemFileSystem.atomicMove(temp, Path(path))
        }
        path
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val dir = Path(platform.documentsDir)
        if (!SystemFileSystem.exists(dir)) return@withContext
        SystemFileSystem.list(dir).forEach { runCatching { SystemFileSystem.delete(it) } }
    }
}
