package com.paybille.invoicer.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.paybille.invoicer.resources.Res
import com.paybille.invoicer.resources.google_sans_flex_bold
import com.paybille.invoicer.resources.google_sans_flex_medium
import com.paybille.invoicer.resources.google_sans_flex_regular
import com.paybille.invoicer.resources.google_sans_flex_semibold
import org.jetbrains.compose.resources.Font

/**
 * Google Sans Flex (SIL OFL 1.1). El POS la sirve como woff2 VARIABLE; aquí van cuatro TTF
 * estáticos instanciados del mismo archivo (receta en la guía 13).
 *
 * Cada peso tiene su archivo, así que `FontWeight` elige el TTF real y nunca se sintetiza
 * una negrita. Aun así, los componentes no inventan pesos: usan los estilos de abajo.
 */
@Composable
fun googleSansFlex(): FontFamily = FontFamily(
    Font(Res.font.google_sans_flex_regular, FontWeight.Normal),
    Font(Res.font.google_sans_flex_medium, FontWeight.Medium),
    Font(Res.font.google_sans_flex_semibold, FontWeight.SemiBold),
    Font(Res.font.google_sans_flex_bold, FontWeight.Bold),
)

/** Cifras de ancho fijo: las columnas de dinero no bailan. */
private const val TABULAR = "tnum"

/**
 * Tamaños del POS subidos un punto para móvil: 14 de base y 16 en campos (por debajo
 * de 16 el texto de un input se percibe pequeño en el teléfono).
 */
@Immutable
data class PbTypography(
    val display: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    /** Título de hoja o de tarjeta (17/800 del diseño; 700 es el peso más alto que hay). */
    val heading: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val input: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    /**
     * Rótulo de campo o de isla, SIEMPRE en mayúsculas (`PbOverline`): "USUARIO", "TOTAL".
     * Del diseño: 10.5/800 con +0.12 em; en móvil sube a 11.
     */
    val overline: TextStyle,
    val button: TextStyle,
    /** Todo importe, cantidad u hora. */
    val amount: TextStyle,
    /** Total de una fila de lista. */
    val amountTitle: TextStyle,
    val amountLarge: TextStyle,
)

fun pbTypography(family: FontFamily) = PbTypography(
    display = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    title = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    subtitle = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    heading = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    body = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodyStrong = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    input = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    label = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    caption = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    overline = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp,
        letterSpacing = 0.12.em,
    ),
    button = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    amount = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp,
        fontFeatureSettings = TABULAR,
    ),
    amountTitle = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp,
        fontFeatureSettings = TABULAR,
    ),
    amountLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp,
        fontFeatureSettings = TABULAR,
    ),
)
