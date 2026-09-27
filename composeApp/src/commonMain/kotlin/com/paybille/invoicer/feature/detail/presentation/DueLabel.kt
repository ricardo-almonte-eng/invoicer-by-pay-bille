package com.paybille.invoicer.feature.detail.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.feature.detail.domain.DueState

/** Texto y color del vencimiento, iguales en el detalle y en las filas del Inicio. */
data class DueText(val text: String, val color: Color)

@Composable
fun DueState.toText(): DueText {
    val colors = PbTheme.colors
    return when (this) {
        DueState.NoDate -> DueText("Sin fecha de vencimiento", colors.muted)
        DueState.Today -> DueText("Vence hoy", colors.accentText)
        is DueState.InDays -> DueText(if (days == 1) "Vence mañana" else "Vence en $days días", colors.muted)
        is DueState.Overdue -> DueText(if (days == 1) "Vencida hace 1 día" else "Vencida hace $days días", colors.error)
    }
}
