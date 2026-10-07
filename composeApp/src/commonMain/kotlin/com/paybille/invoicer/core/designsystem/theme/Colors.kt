package com.paybille.invoicer.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Paleta de PayBille, copia literal de `PayBille_POS/css/Colors.css`.
 * Si allí cambia un valor, aquí también: es la misma marca.
 *
 * ÚNICO archivo del proyecto con colores literales. Los componentes leen
 * `PbTheme.colors`, nunca un `Color(0x…)`.
 */
@Immutable
data class PbColors(
    // Texto, de más a menos peso
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val muted2: Color,
    val disabledFg: Color,

    // Acento: selección, foco, enlaces. NO es el relleno del botón.
    val primary: Color,
    val primaryHover: Color,
    val primaryActive: Color,
    val primary10: Color,
    val primaryBorder: Color,
    val primaryTint: Color,
    /** Texto sobre un relleno `primary` (chip seleccionado). En oscuro el primario es claro. */
    val onPrimary: Color,

    // Relleno de botón primario. Más oscuro que `primary` a propósito y nunca como color de texto.
    val btnFill: Color,
    val btnFillHover: Color,
    val btnText: Color,

    // Naranja RESERVADO: en el diseño original solo lo usan Imprimir y Salir.
    val accent: Color,
    val accentHover: Color,
    val accentActive: Color,
    val accent10: Color,
    val accentText: Color,
    val accentBorder: Color,

    val success: Color,
    val successHover: Color,
    val success10: Color,
    val successBorder: Color,
    val error: Color,
    val errorHover: Color,
    val error10: Color,
    val errorBorder: Color,
    val warning: Color,

    // Superficies: islas (`island`) sobre lienzo (`canvas`)
    val canvas: Color,
    val island: Color,
    val surface2: Color,
    val surface3: Color,
    val surfaceSubtle: Color,
    val rowHover: Color,

    val outline: Color,
    val outlineStrong: Color,
    val outlineStronger: Color,
    val inputContainer: Color,
    val backdrop: Color,
    /** Fondo de los logos de bancos: son JPG/PNG sobre blanco y en oscuro no se pueden invertir. */
    val logoPlate: Color,
    /** Tinta sobre papel (la firma): oscura también en tema oscuro, porque el papel es blanco. */
    val paperInk: Color,

    val isDark: Boolean,
)

val LightPbColors = PbColors(
    ink = Color(0xFF101A42), ink2 = Color(0xFF414D75), muted = Color(0xFF5B6689),
    muted2 = Color(0xFF8A94B2), disabledFg = Color(0xFFAEB8D0),

    primary = Color(0xFF16426F), primaryHover = Color(0xFF1B5088), primaryActive = Color(0xFF0F3053),
    primary10 = Color(0xFFE4ECF3), primaryBorder = Color(0xFFBFD2E3), primaryTint = Color(0xFFF4F8FB),
    onPrimary = Color(0xFFFFFFFF),

    btnFill = Color(0xFF101A42), btnFillHover = Color(0xFF1C2A5E), btnText = Color(0xFFFFFFFF),

    accent = Color(0xFFF97A2B), accentHover = Color(0xFFE56A1D), accentActive = Color(0xFFCC5C15),
    accent10 = Color(0xFFFFEEE2), accentText = Color(0xFFD2601A), accentBorder = Color(0xFFF2D2C0),

    success = Color(0xFF0B7A54), successHover = Color(0xFF096A48),
    success10 = Color(0xFFEAF7F1), successBorder = Color(0xFFC6E7D8),
    error = Color(0xFFE83B3B), errorHover = Color(0xFFC82F2F),
    error10 = Color(0xFFFFF5F5), errorBorder = Color(0xFFFFCACA),
    warning = Color(0xFFFFEB3B),

    canvas = Color(0xFFEEF2F9), island = Color(0xFFFFFFFF), surface2 = Color(0xFFF5F7FC),
    surface3 = Color(0xFFF0F3F9), surfaceSubtle = Color(0xFFFBFCFE), rowHover = Color(0xFFF7F9FD),

    outline = Color(0xFFC9D6E3), outlineStrong = Color(0xFFCFD7EA), outlineStronger = Color(0xFFC9D2E4),
    inputContainer = Color(0xFFE5E9F3),
    backdrop = Color(0x59101A42), // rgba(16, 26, 66, 0.35)
    logoPlate = Color(0xFFFFFFFF),
    paperInk = Color(0xFF101A42),

    isDark = false,
)

// En oscuro el lienzo NO puede ser marino: el relleno de botón lo es y se fundirían.
// Por eso el lienzo baja a #0B0F1C y el relleno sube a #2C4A73. No los unifiques.
val DarkPbColors = PbColors(
    ink = Color(0xFFE6EAF5), ink2 = Color(0xFFC3CBE0), muted = Color(0xFFA3AEC9),
    muted2 = Color(0xFF7D88A6), disabledFg = Color(0xFF5C678A),

    primary = Color(0xFF649CD8), primaryHover = Color(0xFF7DAEE2), primaryActive = Color(0xFF4E86C4),
    primary10 = Color(0xFF16283C), primaryBorder = Color(0xFF2A4460), primaryTint = Color(0xFF121D2B),
    onPrimary = Color(0xFF0B0F1C),

    btnFill = Color(0xFF2C4A73), btnFillHover = Color(0xFF375C8E), btnText = Color(0xFFFFFFFF),

    accent = Color(0xFFF97A2B), accentHover = Color(0xFFE56A1D), accentActive = Color(0xFFCC5C15),
    accent10 = Color(0xFF3A2416), accentText = Color(0xFFF9A76B), accentBorder = Color(0xFF5C3A22),

    success = Color(0xFF12A06E), successHover = Color(0xFF0E8C5F),
    success10 = Color(0xFF14301F), successBorder = Color(0xFF1E4B33),
    error = Color(0xFFF0605F), errorHover = Color(0xFFD64B4A),
    error10 = Color(0xFF3A1C1C), errorBorder = Color(0xFF5C2A2A),
    warning = Color(0xFFFFD54F),

    canvas = Color(0xFF0B0F1C), island = Color(0xFF141A2C), surface2 = Color(0xFF1B2237),
    surface3 = Color(0xFF212942), surfaceSubtle = Color(0xFF101524), rowHover = Color(0xFF1C2439),

    outline = Color(0xFF262E45), outlineStrong = Color(0xFF354061), outlineStronger = Color(0xFF414D73),
    inputContainer = Color(0xFF1B2237),
    backdrop = Color(0x99040710), // rgba(4, 7, 16, 0.6)
    logoPlate = Color(0xFFFFFFFF),
    paperInk = Color(0xFF101A42),

    isDark = true,
)

/**
 * Colores de acento que se pueden elegir para la factura (`invoiceconfig.AccentColor`). Son del
 * papel, no del tema: iguales en claro y oscuro. El hex es lo que viaja a la plantilla.
 */
val InvoiceAccentOptions: List<Pair<String, Color>> = listOf(
    "#16426F" to Color(0xFF16426F), // azul PayBille (por defecto)
    "#101A42" to Color(0xFF101A42),
    "#0E7490" to Color(0xFF0E7490),
    "#0B7A54" to Color(0xFF0B7A54),
    "#D2601A" to Color(0xFFD2601A),
    "#C82F2F" to Color(0xFFC82F2F),
    "#6B3FA0" to Color(0xFF6B3FA0),
)
