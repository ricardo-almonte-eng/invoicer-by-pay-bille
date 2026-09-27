package com.paybille.invoicer.core.platform

import androidx.compose.runtime.Composable

enum class ImageSource { Camera, Gallery }

/**
 * Imagen lista para subir: ya reducida a [MAX_IMAGE_SIDE] px por el lado largo, derecha (la
 * rotación de la cámara ya aplicada) y comprimida. JPEG salvo que tenga transparencia (un logo
 * en PNG): ahí va PNG, o el fondo transparente saldría negro en la factura.
 */
class PickedImage(
    val bytes: ByteArray,
    val mimeType: String,
    /** Sin punto: `jpg` o `png`. */
    val extension: String,
)

/** Lado largo máximo. El servidor acepta 5 MB; a 1280 px una foto queda en ~200–400 KB. */
const val MAX_IMAGE_SIDE = 1280
const val JPEG_QUALITY = 85

fun interface ImagePickerLauncher {
    fun launch(source: ImageSource)
}

/**
 * Cámara o galería del sistema, sin pedir permisos: Android usa la app de cámara
 * (`TakePicture`, a un archivo de la caché compartido por FileProvider) y el selector de fotos
 * (`PickVisualMedia`); iOS, `UIImagePickerController`.
 *
 * `onPicked` y `onFailed` llegan en el hilo principal. Cancelar no llama a ninguno.
 */
@Composable
expect fun rememberImagePicker(
    onPicked: (PickedImage) -> Unit,
    onFailed: (String) -> Unit,
): ImagePickerLauncher
