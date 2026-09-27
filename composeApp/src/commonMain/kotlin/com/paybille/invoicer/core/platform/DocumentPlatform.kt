package com.paybille.invoicer.core.platform

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Lo que el PDF de una factura necesita de cada sistema operativo. La implementación de
 * cada plataforma la registra `platformModule` (expect/actual de Koin).
 */
interface DocumentPlatform {

    /** Carpeta PRIVADA de la app donde se guardan los PDF descargados. */
    val documentsDir: String

    /**
     * Dibuja las páginas del PDF para la vista previa. `widthPx` es el ancho deseado; el alto
     * sale de la proporción de la página. El fondo es blanco siempre: es papel, no tema.
     */
    suspend fun renderPdf(path: String, widthPx: Int, maxPages: Int = MAX_PREVIEW_PAGES): List<ImageBitmap>

    /** Abre la hoja de compartir del sistema (WhatsApp, correo…). */
    fun share(path: String, mimeType: String, title: String)

    /** La hoja de compartir con un texto (p. ej. el enlace del catálogo), sin archivo. */
    fun shareText(text: String, title: String)

    /**
     * Guarda una copia fuera de la app: en Android, en Descargas; en iOS, abre el selector
     * de Archivos para elegir dónde.
     */
    suspend fun saveCopy(path: String, fileName: String, mimeType: String): SaveResult

    companion object {
        const val MAX_PREVIEW_PAGES = 10
    }
}

sealed interface SaveResult {
    /** Guardado; `where` es el sitio en palabras del usuario ("Descargas"). */
    data class Saved(val where: String) : SaveResult

    /** El sistema mostró su propio selector: el usuario decide dónde guardarlo. */
    data object PickerShown : SaveResult

    /** Este teléfono no permite guardar directamente: hay que usar Compartir → Guardar. */
    data object UseShare : SaveResult

    data class Failed(val message: String) : SaveResult
}
