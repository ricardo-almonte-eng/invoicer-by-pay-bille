package com.paybille.invoicer.feature.sales.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.components.PbListCard
import com.paybille.invoicer.core.designsystem.components.PbOverline
import com.paybille.invoicer.core.format.formatDayMonth
import com.paybille.invoicer.feature.detail.domain.DueState
import com.paybille.invoicer.feature.detail.domain.DueUrgency
import com.paybille.invoicer.feature.detail.domain.dueState
import com.paybille.invoicer.feature.detail.domain.urgency
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.paybille.invoicer.core.designsystem.components.PbTag
import com.paybille.invoicer.core.designsystem.components.PbTagTone
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.format.formatShortDate
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.domain.PendingDocument
import com.paybille.invoicer.feature.sales.domain.SaleStatus
import com.paybille.invoicer.feature.sales.domain.SaleSummary

/** Etiqueta y tono de cada estatus. Mismos colores que la guía 06 (`EstadoFactura`). */
fun SaleStatus.label(): Pair<String, PbTagTone> = when (this) {
    SaleStatus.Paid -> "Pagada" to PbTagTone.Success
    SaleStatus.Pending -> "Pendiente" to PbTagTone.Warning
    SaleStatus.Quote -> "Cotización" to PbTagTone.Neutral
    SaleStatus.Cancelled -> "Anulada" to PbTagTone.Danger
    is SaleStatus.Other -> raw to PbTagTone.Neutral
}

/** Lo que falta por cobrar de una factura (de `receivables`), para su fila. */
data class RowDebt(
    val balance: Double,
    /** `YYYY-MM-DD` del próximo vencimiento, o null. */
    val dueDate: String?,
)

/**
 * Fila de venta, en su propia tarjeta:
 * ```
 * ▌Tony Stark                       $ 1,250.50
 * ▌#0009 · 5 may. 2026               [Pendiente]
 * ▌─────────────────────────────────────────────
 * ▌DEBE                              [Por vencer]
 * ▌$ 850.00             Vence en 2 días · 29 sep.
 * ```
 * El bloque de abajo solo sale si la factura tiene saldo (`debt`). La franja de la izquierda
 * (roja vencida, naranja por vencer) va siempre con su etiqueta y su texto: el color no es el
 * único aviso.
 */
@Composable
fun SaleRow(
    sale: SaleSummary,
    timeZone: String,
    modifier: Modifier = Modifier,
    debt: RowDebt? = null,
    /** Hoy en la zona del negocio (`YYYY-MM-DD`); hace falta si hay `debt`. */
    todayIso: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = PbTheme.colors
    val (statusText, statusTone) = sale.status.label()
    val date = sale.createdAt?.let { formatShortDate(it, timeZone) } ?: sale.displayDate?.take(10)
    val due = if (debt != null && todayIso != null) dueState(debt.dueDate, todayIso) else null
    val urgency = due?.urgency() ?: DueUrgency.None

    PbListCard(
        // Un solo anuncio por fila para el lector de pantalla.
        modifier = modifier.semantics(mergeDescendants = true) {},
        onClick = onClick,
        accent = when (urgency) {
            DueUrgency.Overdue -> colors.error
            DueUrgency.Soon -> colors.accent
            DueUrgency.None -> null
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PbSpace.s5)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PbSpace.s1)) {
                PbText(
                    text = sale.clientName,
                    style = PbTheme.typography.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PbText(
                    text = listOfNotNull("#${sale.number}", date?.takeIf { it.isNotEmpty() }, sale.ncf).joinToString(" · "),
                    style = PbTheme.typography.caption.copy(fontFeatureSettings = "tnum"),
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(PbSpace.s2)) {
                PbText(
                    text = formatMoney(sale.total),
                    style = PbTheme.typography.amountTitle,
                    // Una anulada no suma: se ve, pero apagada.
                    color = if (sale.status == SaleStatus.Cancelled) colors.muted2 else colors.ink,
                    // Sin maxLines: con letra grande un importe largo baja de línea, pero nunca se corta.
                    textAlign = TextAlign.End,
                )
                PbTag(text = statusText, tone = statusTone)
            }
        }

        if (debt != null && due != null) {
            Box(Modifier.fillMaxWidth().height(PbControl.border).background(colors.outline))
            DebtLine(debt, due, urgency)
        }
    }
}

@Composable
private fun DebtLine(debt: RowDebt, due: DueState, urgency: DueUrgency) {
    val colors = PbTheme.colors
    val tone = when (urgency) {
        DueUrgency.Overdue -> colors.error
        DueUrgency.Soon -> colors.accentText
        DueUrgency.None -> colors.muted
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PbSpace.s5)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PbOverline(text = "Debe")
            PbText(
                text = formatMoney(debt.balance),
                style = PbTheme.typography.amountTitle,
                // Naranja = falta dinero (guía 06); rojo si además ya venció.
                color = if (urgency == DueUrgency.Overdue) colors.error else colors.accentText,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(PbSpace.s1)) {
            when (urgency) {
                DueUrgency.Overdue -> PbTag(text = "Vencida", tone = PbTagTone.Danger)
                DueUrgency.Soon -> PbTag(text = if (due == DueState.Today) "Vence hoy" else "Por vencer", tone = PbTagTone.Warning)
                DueUrgency.None -> Unit
            }
            PbText(
                text = dueLine(due, debt.dueDate),
                style = PbTheme.typography.caption,
                color = tone,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** "Vence en 3 días · 30 sep." · "Venció hace 7 días · 20 sep." · "Vence hoy". */
internal fun dueLine(due: DueState, dueDate: String?): String {
    val day = dueDate?.let(::formatDayMonth)
    val text = when (due) {
        DueState.NoDate -> return "Sin fecha de vencimiento"
        DueState.Today -> return "Vence hoy"
        is DueState.InDays -> if (due.days == 1) "Vence mañana" else "Vence en ${due.days} días"
        is DueState.Overdue -> if (due.days == 1) "Venció ayer" else "Venció hace ${due.days} días"
    }
    return if (day != null) "$text · $day" else text
}

/**
 * Documento guardado en el teléfono que todavía no llegó al servidor. Todavía no tiene
 * número: la secuencia la da el servidor al enviarlo.
 */
@Composable
fun PendingRow(doc: PendingDocument, timeZone: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PbTheme.colors
    val (statusText, _) = SaleStatus.from(doc.status).label()
    PbListCard(modifier = modifier, background = colors.surfaceSubtle) {
        Row(horizontalArrangement = Arrangement.spacedBy(PbSpace.s5)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PbSpace.s1)) {
                PbText(text = doc.clientName, style = PbTheme.typography.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                PbText(
                    text = listOf(
                        if (doc.kind == DocumentKind.Quote) "Cotización" else "Factura",
                        statusText,
                        formatShortDate(doc.createdAt, timeZone),
                    ).joinToString(" · "),
                    style = PbTheme.typography.caption,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(PbSpace.s2)) {
                PbText(text = formatMoney(doc.total), style = PbTheme.typography.amountTitle, textAlign = TextAlign.End)
                when {
                    doc.failed -> PbTag(text = "No se pudo enviar", tone = PbTagTone.Danger)
                    doc.sending -> PbTag(text = "Enviando…", tone = PbTagTone.Info)
                    else -> PbTag(text = "Por enviar", tone = PbTagTone.Info)
                }
            }
        }
        if (doc.failed) {
            PbBanner(message = doc.error ?: "El servidor rechazó el documento.", tone = PbBannerTone.Error)
            PbButton(
                text = "Reintentar envío",
                onClick = onRetry,
                variant = PbButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
