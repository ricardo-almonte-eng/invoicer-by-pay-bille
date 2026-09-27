package com.paybille.invoicer.core.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

private const val CAPTURE_DIR = "captures"

@Composable
actual fun rememberImagePicker(
    onPicked: (PickedImage) -> Unit,
    onFailed: (String) -> Unit,
): ImagePickerLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onFailed)
    // La app de cámara puede sacar la nuestra de memoria: el destino de la foto sobrevive.
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun process(uri: Uri) {
        scope.launch {
            val image = try {
                withContext(Dispatchers.IO) { prepare(context, uri) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (image != null) picked(image) else failed("No se pudo leer la imagen. Prueba con otra.")
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = captureUri?.let(Uri::parse)
        if (saved && uri != null) process(uri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) process(uri)
    }

    return remember(context) {
        ImagePickerLauncher { source ->
            try {
                when (source) {
                    ImageSource.Camera -> {
                        val dir = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
                        val file = File(dir, "captura.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        captureUri = uri.toString()
                        camera.launch(uri)
                    }
                    ImageSource.Gallery -> gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            } catch (e: ActivityNotFoundException) {
                failed(if (source == ImageSource.Camera) "Este teléfono no tiene app de cámara." else "No hay galería de fotos.")
            }
        }
    }
}

/**
 * Lee, endereza y reduce. Se decodifica ya submuestreado (`inSampleSize`): una foto de 12 MP a
 * tamaño completo son ~48 MB de memoria y en teléfonos modestos no cabe.
 */
private fun prepare(context: Context, uri: Uri): PickedImage? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null

    // La cámara guarda la foto "acostada" y anota el giro en EXIF; BitmapFactory lo ignora.
    val rotation = resolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f

    val scale = minOf(1f, MAX_IMAGE_SIDE.toFloat() / max(decoded.width, decoded.height))
    val matrix = Matrix().apply {
        if (scale < 1f) postScale(scale, scale)
        if (rotation != 0f) postRotate(rotation)
    }
    val ready = if (matrix.isIdentity) {
        decoded
    } else {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    val png = ready.hasAlpha()
    val out = ByteArrayOutputStream()
    ready.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    if (ready !== decoded) decoded.recycle()
    ready.recycle()
    return if (png) PickedImage(out.toByteArray(), "image/png", "png") else PickedImage(out.toByteArray(), "image/jpeg", "jpg")
}

