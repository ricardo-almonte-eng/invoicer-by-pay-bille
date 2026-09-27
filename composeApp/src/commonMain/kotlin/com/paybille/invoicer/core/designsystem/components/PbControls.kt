package com.paybille.invoicer.core.designsystem.components

import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbIconSize
import com.paybille.invoicer.core.designsystem.theme.PbMotion
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.designsystem.theme.PbSymbol

/**
 * Sección del editor: una isla con cabecera (icono, rótulo y algo a la derecha). El título es
 * un rótulo del diseño ("CLIENTE", "COBRO"): la isla se nombra, no se grita.
 */
@Composable
fun PbSection(
    title: String,
    modifier: Modifier = Modifier,
    icon: PbSymbol? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    PbCard(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PbSpace.s5)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PbSpace.s4)) {
            if (icon != null) PbIcon(icon = icon, contentDescription = null, tint = PbTheme.colors.muted)
            PbOverline(text = title, modifier = Modifier.weight(1f).semantics { heading() })
            trailing()
        }
        content()
    }
}

/**
 * Opción seleccionable en forma de píldora (moneda, tipo de NCF, vencimiento…). Como los
 * chips del diseño: la seleccionada va rellena del primario; el resto, contorno.
 */
@Composable
fun PbChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PbTheme.colors
    val spec = tween<Color>(PbMotion.CONTROL_MS, easing = PbMotion.ease)
    val container by animateColorAsState(if (selected) colors.primary else colors.island, spec, label = "chipBg")
    val border by animateColorAsState(if (selected) colors.primary else colors.outlineStrong, spec, label = "chipBorder")
    val content by animateColorAsState(
        when {
            !enabled -> colors.disabledFg
            selected -> colors.onPrimary
            else -> colors.ink2
        },
        spec,
        label = "chipText",
    )
    val shape = RoundedCornerShape(PbRadius.pill)
    Box(
        modifier = modifier
            .heightIn(min = PbControl.minTouch)
            .clip(shape)
            .background(container, shape)
            .border(PbControl.border, border, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = PbSpace.s6),
        contentAlignment = Alignment.Center,
    ) {
        PbText(text = text, style = PbTheme.typography.bodyStrong, color = content, maxLines = 1)
    }
}

/** Grupo de chips que baja de línea si no cabe (360 dp, letra grande). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> PbChipGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PbSpace.s3),
        verticalArrangement = Arrangement.spacedBy(PbSpace.s3),
    ) {
        options.forEach { option ->
            PbChip(text = label(option), selected = option == selected, onClick = { onSelect(option) })
        }
    }
}

/** Interruptor con su texto. Toda la fila es pulsable. */
@Composable
fun PbSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colors = PbTheme.colors
    val spec = tween<Color>(PbMotion.CONTROL_MS, easing = PbMotion.ease)
    val track by animateColorAsState(if (checked) colors.primary else colors.surface3, spec, label = "track")
    val trackBorder by animateColorAsState(if (checked) colors.primary else colors.outlineStronger, spec, label = "trackBorder")
    val thumbColor by animateColorAsState(if (checked) colors.btnText else colors.muted, spec, label = "thumb")
    // 20 dp de recorrido: es el propio control, no un desplazamiento de contenido.
    val thumbOffset by animateDpAsState(if (checked) 22.dp else 4.dp, tween(PbMotion.CONTROL_MS, easing = PbMotion.ease), label = "offset")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PbControl.h)
            .clip(RoundedCornerShape(PbRadius.md))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PbSpace.s5),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PbText(text = title, style = PbTheme.typography.bodyStrong)
            if (subtitle != null) PbText(text = subtitle, style = PbTheme.typography.caption, color = colors.muted)
        }
        Box(
            Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(CircleShape)
                .background(track, CircleShape)
                .border(PbControl.border, trackBorder, CircleShape),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .size(20.dp)
                    .background(thumbColor, CircleShape),
            )
        }
    }
}

/**
 * Fila con casilla, para elegir **varios** de una lista (cuentas de "Dónde pagar", productos
 * del catálogo). Toda la fila es pulsable; la casilla elegida va rellena del primario.
 */
@Composable
fun PbCheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = PbTheme.colors
    val spec = tween<Color>(PbMotion.CONTROL_MS, easing = PbMotion.ease)
    val box by animateColorAsState(if (checked) colors.primary else colors.island, spec, label = "checkBg")
    val boxBorder by animateColorAsState(if (checked) colors.primary else colors.outlineStronger, spec, label = "checkBorder")
    val boxShape = RoundedCornerShape(6.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PbControl.h)
            .clip(RoundedCornerShape(PbRadius.md))
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = PbSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PbSpace.s5),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PbText(
                text = title,
                style = PbTheme.typography.bodyStrong,
                color = if (enabled) colors.ink else colors.disabledFg,
                maxLines = 2,
            )
            if (subtitle != null) PbText(text = subtitle, style = PbTheme.typography.caption, color = colors.muted, maxLines = 2)
        }
        Box(
            Modifier
                .size(24.dp)
                .clip(boxShape)
                .background(box, boxShape)
                .border(PbControl.border, boxBorder, boxShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) PbIcon(icon = PbSymbols.Check, contentDescription = null, tint = colors.onPrimary, size = PbIconSize.md)
        }
    }
}

/** Cantidad con − y +. Por debajo de `min` no baja: para quitar la línea hay un botón aparte. */
@Composable
fun PbStepper(
    value: Double,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Cantidad",
    min: Double = 1.0,
    enabled: Boolean = true,
) {
    val colors = PbTheme.colors
    val shape = RoundedCornerShape(PbRadius.md)
    Row(
        modifier = modifier
            .clip(shape)
            .border(PbControl.border, colors.outlineStrong, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(PbSymbols.Remove, "Quitar uno de $label", enabled && value > min, onDecrease)
        PbText(
            text = formatNumberInput(value, 3).ifEmpty { "0" },
            style = PbTheme.typography.amount,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 32.dp).padding(horizontal = PbSpace.s1),
        )
        StepButton(PbSymbols.Add, "Agregar uno a $label", enabled, onIncrease)
    }
}

@Composable
private fun StepButton(icon: PbSymbol, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(PbControl.minTouch)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PbIcon(
            icon = icon,
            contentDescription = description,
            tint = if (enabled) PbTheme.colors.primary else PbTheme.colors.disabledFg,
            size = PbIconSize.lg,
        )
    }
}
