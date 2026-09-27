package com.paybille.invoicer.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbIcon
import com.paybille.invoicer.core.designsystem.components.PbOverline
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbIconSize
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.platform.ImageSource
import com.paybille.invoicer.core.platform.PickedImage
import com.paybille.invoicer.core.platform.rememberImagePicker
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Imagen de un formulario (producto, logo de la tienda): la vista previa y, al lado, "Tomar
 * foto", "Galería" y "Quitar". Botones a la vista en vez de un menú: con una mano y de pie, un
 * toque menos.
 *
 * - `url`: la imagen que ya está en el servidor (`null` = ninguna o quitada).
 * - `picked`: la nueva, todavía en el teléfono; se sube al guardar. Manda sobre `url`.
 *
 * `contentScale`: `Crop` para fotos de producto; `Fit` para un logo, que no se recorta.
 */
@Composable
fun ImageField(
    label: String,
    url: String?,
    picked: PickedImage?,
    onPicked: (PickedImage) -> Unit,
    onRemove: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    contentScale: ContentScale = ContentScale.Crop,
    enabled: Boolean = true,
) {
    val colors = PbTheme.colors
    val picker = rememberImagePicker(onPicked = onPicked, onFailed = onError)
    val preview: ImageBitmap? = remember(picked) {
        picked?.let { runCatching { it.bytes.decodeToImageBitmap() }.getOrNull() }
    }
    val hasImage = picked != null || !url.isNullOrBlank()
    val shape = RoundedCornerShape(PbRadius.lg)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PbSpace.s2)) {
        PbOverline(text = label, modifier = Modifier.padding(start = PbSpace.s1))
        Row(horizontalArrangement = Arrangement.spacedBy(PbSpace.s5), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(shape)
                    .background(colors.surface2, shape)
                    .border(PbControl.border, colors.outlineStrong, shape)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Tomar foto") { picker.launch(ImageSource.Camera) },
                contentAlignment = Alignment.Center,
            ) {
                val empty: @Composable () -> Unit = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PbSpace.s1)) {
                        PbIcon(icon = PbSymbols.AddAPhoto, contentDescription = null, tint = colors.muted, size = PbIconSize.xl)
                        PbText(text = "Sin imagen", style = PbTheme.typography.caption, color = colors.muted, textAlign = TextAlign.Center)
                    }
                }
                when {
                    preview != null -> Image(bitmap = preview, contentDescription = label, contentScale = contentScale, modifier = Modifier.matchParentSize())
                    !url.isNullOrBlank() -> RemoteImage(
                        url = url,
                        contentDescription = label,
                        contentScale = contentScale,
                        modifier = Modifier.matchParentSize(),
                        placeholder = { PbIcon(icon = PbSymbols.Image, contentDescription = null, tint = colors.muted2, size = PbIconSize.xl) },
                    )
                    else -> empty()
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PbSpace.s2)) {
                PbButton(
                    text = "Tomar foto",
                    onClick = { picker.launch(ImageSource.Camera) },
                    variant = PbButtonVariant.Outline,
                    leadingIcon = PbSymbols.PhotoCamera,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                PbButton(
                    text = "Galería",
                    onClick = { picker.launch(ImageSource.Gallery) },
                    variant = PbButtonVariant.Outline,
                    leadingIcon = PbSymbols.PhotoLibrary,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hasImage) {
                    PbButton(
                        text = "Quitar",
                        onClick = onRemove,
                        variant = PbButtonVariant.Ghost,
                        leadingIcon = PbSymbols.HideImage,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
