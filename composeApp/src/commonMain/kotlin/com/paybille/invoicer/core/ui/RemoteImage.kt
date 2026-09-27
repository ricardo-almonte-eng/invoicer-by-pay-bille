package com.paybille.invoicer.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.paybille.invoicer.core.network.RemoteImageLoader
import org.koin.compose.koinInject

/**
 * Imagen por URL (producto, logo de la tienda). Mientras carga, sin red o si la URL no sirve,
 * se ve `placeholder`: el hueco ya tiene su tamaño y la pantalla no salta al llegar la imagen.
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = {},
) {
    val loader = koinInject<RemoteImageLoader>()
    val image by produceState<ImageBitmap?>(initialValue = null, url) {
        value = url?.takeIf { it.isNotBlank() }?.let { loader.load(it) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val current = image
        if (current == null) {
            placeholder()
        } else {
            Image(bitmap = current, contentDescription = contentDescription, contentScale = contentScale, modifier = Modifier.matchParentSize())
        }
    }
}
