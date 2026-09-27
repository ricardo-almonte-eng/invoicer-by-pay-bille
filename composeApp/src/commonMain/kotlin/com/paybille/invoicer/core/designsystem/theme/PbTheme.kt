package com.paybille.invoicer.core.designsystem.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle

private val LocalPbColors = staticCompositionLocalOf { LightPbColors }
private val LocalPbTypography = staticCompositionLocalOf<PbTypography> {
    error("PbTypography sin proveer: envuelve la UI en PbTheme { }")
}

/** Estilo de texto heredado por `PbText` cuando no recibe uno explícito. */
internal val LocalPbTextStyle = staticCompositionLocalOf { TextStyle.Default }

/**
 * Tema de PayBille. Sin Material: la identidad es propia (islas, bordes de 1 dp, cero sombras)
 * y envolverla en Material obligaría a pelear con sus elevaciones y rellenos.
 *
 * Modo `system` por defecto; el selector manual llegará con la pantalla de Ajustes.
 */
@Composable
fun PbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkPbColors else LightPbColors
    val family = googleSansFlex()
    val typography = remember(family) { pbTypography(family) }
    val selection = remember(colors) {
        TextSelectionColors(handleColor = colors.primary, backgroundColor = colors.primary10)
    }
    val indication = remember(colors) { PbPressIndication(colors.ink) }

    CompositionLocalProvider(
        LocalPbColors provides colors,
        LocalPbTypography provides typography,
        LocalPbTextStyle provides typography.body.copy(color = colors.ink),
        LocalTextSelectionColors provides selection,
        LocalIndication provides indication,
        content = content,
    )
}

object PbTheme {
    val colors: PbColors
        @Composable @ReadOnlyComposable get() = LocalPbColors.current

    val typography: PbTypography
        @Composable @ReadOnlyComposable get() = LocalPbTypography.current
}
