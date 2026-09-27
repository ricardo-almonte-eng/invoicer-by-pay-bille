package com.paybille.invoicer.core.designsystem.components

import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbIconSize
import com.paybille.invoicer.core.designsystem.theme.PbMotion
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.designsystem.theme.PbSymbol

/**
 * Campo de texto (equivale a `<CustomInput>` del POS). La etiqueta va FUERA y encima
 * del campo, no flotando dentro: con el teclado abierto y mala luz se lee mejor. Es un
 * rótulo del diseño ("USUARIO"): mayúsculas, espaciado ancho y color apagado.
 *
 * `isPassword` añade el botón de mostrar/ocultar y desactiva el autocorrector.
 */
@Composable
fun PbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    placeholder: String? = null,
    leadingIcon: PbSymbol? = null,
    error: String? = null,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    /** Texto fijo antes del valor (`$`, `USD`). */
    prefix: String? = null,
    /** Texto fijo después del valor (`%`). */
    suffix: String? = null,
    /** Cifras tabulares, para importes y cantidades. */
    numeric: Boolean = false,
    singleLine: Boolean = true,
    /** Se aplica al campo editable (p. ej. `focusRequester`), no a la etiqueta. */
    fieldModifier: Modifier = Modifier,
) {
    val colors = PbTheme.colors
    val typography = PbTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var revealed by rememberSaveable { mutableStateOf(false) }

    // Mismo comportamiento que `.CustomInput` del POS: en foco el borde pasa a 2 dp
    // del acento y el fondo se tiñe con `primaryTint`.
    val borderTarget = when {
        error != null -> colors.error
        focused -> colors.primary
        else -> colors.outlineStrong
    }
    val border by animateColorAsState(
        targetValue = borderTarget,
        animationSpec = tween(PbMotion.CONTROL_MS, easing = PbMotion.ease),
        label = "border",
    )
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> colors.surface2
            focused -> colors.primaryTint
            else -> colors.island
        },
        animationSpec = tween(PbMotion.CONTROL_MS, easing = PbMotion.ease),
        label = "container",
    )
    val borderWidth = if (focused || error != null) PbControl.borderFocus else PbControl.border
    val shape = RoundedCornerShape(PbRadius.lg)

    val transformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None
    val options = if (isPassword) {
        keyboardOptions.copy(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
    } else {
        keyboardOptions
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PbSpace.s2)) {
        FieldLabel(label = label, required = required)

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier
                .fillMaxWidth()
                .semantics { if (error != null) error(error) },
            enabled = enabled,
            singleLine = singleLine,
            textStyle = typography.input.copy(
                color = if (enabled) colors.ink else colors.disabledFg,
                fontFeatureSettings = if (numeric) "tnum" else null,
            ),
            cursorBrush = SolidColor(colors.primary),
            visualTransformation = transformation,
            keyboardOptions = options,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PbControl.field)
                        .background(container, shape)
                        .border(borderWidth, border, shape)
                        .padding(start = PbSpace.s5, end = if (isPassword) PbSpace.s1 else PbSpace.s5),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PbSpace.s4),
                ) {
                    if (leadingIcon != null) {
                        PbIcon(
                            icon = leadingIcon,
                            contentDescription = null,
                            tint = if (focused) colors.primary else colors.muted,
                        )
                    }
                    if (prefix != null) {
                        PbText(text = prefix, style = typography.input, color = colors.muted)
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            PbText(text = placeholder, style = typography.input, color = colors.muted2, maxLines = 1)
                        }
                        inner()
                    }
                    if (suffix != null) {
                        PbText(text = suffix, style = typography.input, color = colors.muted)
                    }
                    if (isPassword) {
                        RevealToggle(revealed = revealed, onToggle = { revealed = !revealed })
                    }
                }
            },
        )

        if (error != null) {
            PbText(text = error, style = typography.caption, color = colors.error)
        }
    }
}

@Composable
private fun FieldLabel(label: String, required: Boolean) {
    val colors = PbTheme.colors
    val text = buildAnnotatedString {
        append(label.uppercase())
        if (required) withStyle(SpanStyle(color = colors.error)) { append(" *") }
    }
    BasicText(
        text = text,
        modifier = Modifier.padding(start = PbSpace.s1),
        style = PbTheme.typography.overline.copy(color = colors.muted),
    )
}

@Composable
private fun RevealToggle(revealed: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(PbControl.minTouch)
            .clip(RoundedCornerShape(PbRadius.sm))
            .clickable(role = Role.Button, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        PbIcon(
            icon = if (revealed) PbSymbols.VisibilityOff else PbSymbols.Visibility,
            contentDescription = if (revealed) "Ocultar contraseña" else "Mostrar contraseña",
            tint = PbTheme.colors.muted,
            size = PbIconSize.lg,
        )
    }
}
