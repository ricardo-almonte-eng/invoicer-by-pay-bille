package com.paybille.invoicer.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.math.max

@Composable
actual fun rememberImagePicker(
    onPicked: (PickedImage) -> Unit,
    onFailed: (String) -> Unit,
): ImagePickerLauncher {
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onFailed)
    // `delegate` es una referencia DÉBIL en UIKit: si nadie más la retiene, se libera mientras
    // el selector está abierto y la foto nunca llega. `remember` la mantiene viva.
    val delegate = remember { PickerDelegate() }
    delegate.onPicked = { picked(it) }
    delegate.onFailed = { failed(it) }

    return remember {
        ImagePickerLauncher { source ->
            val type = when (source) {
                ImageSource.Camera -> UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                ImageSource.Gallery -> UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            }
            if (!UIImagePickerController.isSourceTypeAvailable(type)) {
                delegate.onFailed(if (source == ImageSource.Camera) "La cámara no está disponible." else "No hay galería de fotos.")
                return@ImagePickerLauncher
            }
            val picker = UIImagePickerController()
            picker.sourceType = type
            picker.delegate = delegate
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PickerDelegate : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    var onPicked: (PickedImage) -> Unit = {}
    var onFailed: (String) -> Unit = {}

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        val prepared = image?.let(::prepare)
        if (prepared != null) onPicked(prepared) else onFailed("No se pudo leer la imagen. Prueba con otra.")
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

/**
 * Reduce y endereza: redibujar la `UIImage` aplica su `imageOrientation` (la foto de la cámara
 * viene "acostada" con el giro anotado). PNG solo si la imagen tiene transparencia.
 */
@OptIn(ExperimentalForeignApi::class)
private fun prepare(image: UIImage): PickedImage? {
    val (width, height) = image.size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val scale = minOf(1.0, MAX_IMAGE_SIDE / max(width, height))
    val alpha = CGImageGetAlphaInfo(image.CGImage)
    val hasAlpha = alpha != CGImageAlphaInfo.kCGImageAlphaNone &&
        alpha != CGImageAlphaInfo.kCGImageAlphaNoneSkipLast &&
        alpha != CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width * scale, height * scale), !hasAlpha, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
    val ready = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    ready ?: return null

    return if (hasAlpha) {
        UIImagePNGRepresentation(ready)?.toBytes()?.let { PickedImage(it, "image/png", "png") }
    } else {
        UIImageJPEGRepresentation(ready, JPEG_QUALITY / 100.0)?.toBytes()?.let { PickedImage(it, "image/jpeg", "jpg") }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toBytes(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

private fun topViewController(): UIViewController? {
    @Suppress("DEPRECATION")
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
