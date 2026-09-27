package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.Dp
import com.paybille.invoicer.core.designsystem.theme.PbIconSize
import com.paybille.invoicer.core.designsystem.theme.PbSymbol
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.resources.Res
import com.paybille.invoicer.resources.material_symbols_rounded
import org.jetbrains.compose.resources.Font

/**
 * Icono de Material Symbols Rounded, dibujado con la fuente (`material_symbols_rounded.ttf`,
 * con el eje FILL variable) → guía 13. `filled` es la variante rellena: la del destino activo.
 * `contentDescription = null` para los decorativos: el lector de pantalla ya lee el texto de
 * al lado.
 *
 * El tamaño va en dp y **no** crece con la letra grande del sistema, igual que un vector. En
 * Android 7 (API 24–25) no hay ejes variables: el relleno no se ve, el color sí.
 */
@Composable
fun PbIcon(
    icon: PbSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = PbTheme.colors.ink2,
    size: Dp = PbIconSize.lg,
    filled: Boolean = false,
) {
    val family = symbolFamily(filled)
    // Dp.toSp() ya divide por fontScale: el icono mide `size` con cualquier tamaño de letra.
    val fontSize = with(LocalDensity.current) { size.toSp() }
    val glyph = remember(icon) { icon.text() }
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                    role = Role.Image
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // La caja de línea de la fuente mide 1.2 em con el glifo centrado: se deja desbordar
        // en vertical y queda centrado en `size`.
        BasicText(
            text = glyph,
            modifier = Modifier.wrapContentSize(unbounded = true),
            style = TextStyle(color = tint, fontFamily = family, fontSize = fontSize),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun symbolFamily(filled: Boolean): FontFamily {
    val fill = if (filled) 1f else 0f
    val font = Font(
        resource = Res.font.material_symbols_rounded,
        variationSettings = FontVariation.Settings(FontVariation.Setting("FILL", fill)),
    )
    return remember(font) { FontFamily(font) }
}

/** El codepoint como texto (con par sustituto si está fuera del plano básico). */
private fun PbSymbol.text(): String =
    if (codepoint <= 0xFFFF) {
        codepoint.toChar().toString()
    } else {
        val v = codepoint - 0x10000
        charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
    }
