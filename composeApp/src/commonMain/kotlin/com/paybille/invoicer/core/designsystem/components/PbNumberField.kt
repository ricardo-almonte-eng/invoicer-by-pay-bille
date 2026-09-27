package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * Campo numérico con teclado decimal. Guarda su propio texto mientras se escribe (para que
 * "12." o "0.0" no se reescriban solos) y entrega el número ya interpretado.
 *
 * Solo acepta dígitos y un punto; la coma se toma como punto (algunos teclados en español
 * la ponen). Vacío = 0.
 */
@Composable
fun PbNumberField(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimals: Int = 2,
    prefix: String? = null,
    suffix: String? = null,
    placeholder: String? = "0",
    error: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var text by remember { mutableStateOf(formatNumberInput(value, decimals)) }

    // Si el valor cambia desde fuera (otro campo, un botón), se refleja; si es el mismo
    // número que ya está escrito, no se toca el texto.
    LaunchedEffect(value) {
        if (parseNumberInput(text) != value) text = formatNumberInput(value, decimals)
    }

    PbTextField(
        value = text,
        onValueChange = { raw ->
            val clean = sanitizeNumberInput(raw, decimals) ?: return@PbTextField
            text = clean
            onValueChange(parseNumberInput(clean))
        },
        label = label,
        modifier = modifier,
        placeholder = placeholder,
        error = error,
        enabled = enabled,
        prefix = prefix,
        suffix = suffix,
        numeric = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = keyboardActions,
    )
}

/** `null` si el texto no es un número válido en construcción (se ignora la tecla). */
internal fun sanitizeNumberInput(raw: String, decimals: Int): String? {
    val text = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
    if (text.count { it == '.' } > 1) return null
    if (decimals == 0 && text.contains('.')) return null
    val dot = text.indexOf('.')
    if (dot >= 0 && text.length - dot - 1 > decimals) return null
    return if (text.length > MAX_LENGTH) null else text
}

internal fun parseNumberInput(text: String): Double = text.toDoubleOrNull() ?: 0.0

internal fun formatNumberInput(value: Double, decimals: Int): String {
    if (value == 0.0) return ""
    val whole = value.toLong()
    if (value == whole.toDouble()) return whole.toString()
    // Sin notación científica y sin ceros de más: 12.50 → "12.5".
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = kotlin.math.round(value * factor).toLong()
    val intPart = scaled / factor
    val frac = (scaled % factor).toString().padStart(decimals, '0').trimEnd('0')
    return if (frac.isEmpty()) intPart.toString() else "$intPart.$frac"
}

private const val MAX_LENGTH = 12
