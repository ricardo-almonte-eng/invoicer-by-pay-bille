package com.paybille.invoicer.core.platform

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidDocumentPlatform(private val context: Context) : DocumentPlatform {

    override val documentsDir: String
        get() = File(context.filesDir, DIR).apply { mkdirs() }.absolutePath

    override suspend fun renderPdf(path: String, widthPx: Int, maxPages: Int): List<ImageBitmap> =
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists() || widthPx <= 0) return@withContext emptyList()
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    (0 until minOf(renderer.pageCount, maxPages)).map { index ->
                        renderer.openPage(index).use { page ->
                            val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                            // El PDF es papel: fondo blanco siempre, también en tema oscuro.
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap.asImageBitmap()
                        }
                    }
                }
            }
        }

    private val htmlPdf = AndroidHtmlPdf(context)

    override suspend fun htmlToPdf(html: String, outputPath: String) = htmlPdf.write(html, outputPath)

    override fun share(path: String, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, title).apply {
            // Se lanza desde el contexto de la aplicación, fuera de una Activity.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }

    override fun shareText(text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        // Se lanza desde el contexto de la aplicación, fuera de una Activity.
        context.startActivity(Intent.createChooser(send, title).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }

    override suspend fun saveCopy(path: String, fileName: String, mimeType: String): SaveResult =
        withContext(Dispatchers.IO) {
            // Antes de Android 10, escribir en Descargas exige un permiso de almacenamiento
            // que la app no pide: ahí se guarda desde Compartir.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext SaveResult.UseShare
            runCatching {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("No se pudo crear el archivo en Descargas.")
                resolver.openOutputStream(uri)?.use { out -> File(path).inputStream().use { it.copyTo(out) } }
                    ?: error("No se pudo escribir el archivo.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                SaveResult.Saved("Descargas")
            }.getOrElse { SaveResult.Failed(it.message ?: "No se pudo guardar el PDF.") }
        }

    private companion object {
        // Debe coincidir con res/xml/file_paths.xml.
        const val DIR = "invoices"
    }
}
