package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbIconSize
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme

@Immutable
data class PbOption(val id: Int, val label: String)

/**
 * Hoja para elegir UNA opción de un catálogo (categoría, marca, color…), con buscador y, si
 * `onCreate` no es `null`, alta rápida de lo escrito (el "+" junto al select del POS).
 *
 * `noneLabel`: fila para dejar el campo vacío; `null` si no se permite.
 * `message`: aviso encima de la lista (sin red, error al crear).
 */
@Composable
fun PbOptionSheet(
    visible: Boolean,
    title: String,
    options: List<PbOption>,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
    noneLabel: String? = null,
    onCreate: ((String) -> Unit)? = null,
    creating: Boolean = false,
    loading: Boolean = false,
    message: String? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(visible) { if (!visible) query = "" }

    PbSheet(visible = visible, title = title, onDismiss = onDismiss) {
        PbTextField(
            value = query,
            onValueChange = { query = it },
            label = if (onCreate != null) "Buscar o crear" else "Buscar",
            placeholder = "Escribe un nombre",
            leadingIcon = PbSymbols.Search,
            modifier = Modifier.fillMaxWidth(),
        )
        message?.let { PbBanner(message = it, tone = PbBannerTone.Offline) }

        val needle = query.trim()
        val visibleOptions = if (needle.isEmpty()) options else options.filter { it.label.contains(needle, ignoreCase = true) }
        val exact = options.any { it.label.equals(needle, ignoreCase = true) }

        if (onCreate != null && needle.isNotEmpty() && !exact) {
            PbButton(
                text = "Crear “$needle”",
                onClick = { onCreate(needle) },
                variant = PbButtonVariant.Outline,
                leadingIcon = PbSymbols.Add,
                loading = creating,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (loading && options.isEmpty()) PbSpinnerRow()

        Column(verticalArrangement = Arrangement.spacedBy(PbSpace.s2)) {
            if (noneLabel != null && needle.isEmpty()) {
                OptionRow(label = noneLabel, selected = selectedId == null, muted = true, onClick = { onSelect(null) })
            }
            visibleOptions.forEach { option ->
                OptionRow(label = option.label, selected = option.id == selectedId, onClick = { onSelect(option.id) })
            }
        }
        if (!loading && visibleOptions.isEmpty() && needle.isNotEmpty() && onCreate == null) {
            PbText(text = "Nada coincide con “$needle”.", style = PbTheme.typography.body, color = PbTheme.colors.muted)
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit, muted: Boolean = false) {
    val colors = PbTheme.colors
    val shape = RoundedCornerShape(PbRadius.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PbControl.h)
            .clip(shape)
            .background(if (selected) colors.primaryTint else colors.island, shape)
            .border(PbControl.border, if (selected) colors.primary else colors.outline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = PbSpace.s5, vertical = PbSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PbSpace.s4),
    ) {
        PbText(
            text = label,
            style = PbTheme.typography.bodyStrong,
            color = if (muted && !selected) colors.muted else colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) PbIcon(icon = PbSymbols.Check, contentDescription = null, tint = colors.primary, size = PbIconSize.lg)
    }
}
