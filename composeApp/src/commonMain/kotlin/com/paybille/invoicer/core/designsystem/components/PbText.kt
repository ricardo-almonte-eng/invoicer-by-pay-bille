package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.paybille.invoicer.core.designsystem.theme.LocalPbTextStyle
import com.paybille.invoicer.core.designsystem.theme.PbTheme

/**
 * Texto de la app. Sin `style` hereda el cuerpo del tema; sin `color`, la tinta.
 * Para importes usa `PbTheme.typography.amount`: lleva cifras tabulares.
 */
@Composable
fun PbText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalPbTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val base = LocalPbTextStyle.current
    val resolvedColor = when {
        color != Color.Unspecified -> color
        style.color != Color.Unspecified -> style.color
        else -> base.color
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(color = resolvedColor, textAlign = textAlign),
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * Rótulo del diseño ("USUARIO", "TOTAL", "LOTERÍAS"): mayúsculas, espaciado ancho y color
 * apagado. Es la etiqueta de campo y el título de una isla. Se escribe normal en el código y
 * se pone en mayúsculas aquí, con las reglas de `es-DO` (la "ñ" y las tildes se conservan).
 */
@Composable
fun PbOverline(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PbTheme.colors.muted,
    maxLines: Int = 1,
) {
    PbText(
        text = text.uppercase(),
        modifier = modifier,
        style = PbTheme.typography.overline,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
