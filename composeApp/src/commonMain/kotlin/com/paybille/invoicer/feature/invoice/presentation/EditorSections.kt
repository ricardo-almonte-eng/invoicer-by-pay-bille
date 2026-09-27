package com.paybille.invoicer.feature.invoice.presentation

import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.billing.Currency
import com.paybille.invoicer.core.billing.TaxType
import com.paybille.invoicer.core.billing.toInvoiceCurrency
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbCheckRow
import com.paybille.invoicer.core.designsystem.components.PbChipGroup
import com.paybille.invoicer.core.designsystem.components.PbIcon
import com.paybille.invoicer.core.designsystem.components.PbIconButton
import com.paybille.invoicer.core.designsystem.components.PbNumberField
import com.paybille.invoicer.core.designsystem.components.PbSection
import com.paybille.invoicer.core.designsystem.components.PbStepper
import com.paybille.invoicer.core.designsystem.components.PbSwitchRow
import com.paybille.invoicer.core.designsystem.components.PbTag
import com.paybille.invoicer.core.designsystem.components.PbTagTone
import com.paybille.invoicer.core.designsystem.components.PbOverline
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.components.PbTotalBox
import com.paybille.invoicer.core.designsystem.components.PbTextField
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbIconSize
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.format.formatQuantity
import com.paybille.invoicer.core.format.formatPercent
import com.paybille.invoicer.core.ui.BankLogo
import com.paybille.invoicer.feature.invoice.domain.DraftAccount
import com.paybille.invoicer.feature.invoice.domain.DraftLine
import com.paybille.invoicer.feature.invoice.domain.InvoiceDraft
import com.paybille.invoicer.feature.invoice.domain.NcfType

/**
 * Cómo se enseña un importe del borrador. El borrador guarda moneda base; aquí, y solo
 * aquí en la UI, se pasa a la moneda del documento (con `core/billing/Money.kt`).
 */
internal class Amounts(private val draft: InvoiceDraft) {
    val symbol: String get() = draft.currency.symbol

    fun inDocument(base: Double): Double =
        if (draft.currency.isBase) base else toInvoiceCurrency(base, draft.exchangeRate)

    fun show(base: Double): String = formatMoney(inDocument(base), symbol)

    fun showBase(base: Double): String = formatMoney(base)
}

// Cliente ------------------------------------------------------------------------

@Composable
internal fun ClientSection(draft: InvoiceDraft, onPick: () -> Unit, onRemove: () -> Unit) {
    PbSection(title = "Cliente", icon = PbSymbols.Person) {
        val client = draft.client
        TappableRow(onClick = onPick, label = if (client == null) "Elegir cliente" else "Cambiar cliente") {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PbText(
                    text = client?.name ?: "Consumidor final",
                    style = PbTheme.typography.bodyStrong,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                PbText(
                    text = if (client == null) {
                        "Toca para elegir un cliente"
                    } else {
                        listOfNotNull(client.phone, client.identify).joinToString(" · ").ifEmpty { "Cliente registrado" }
                    },
                    style = PbTheme.typography.caption,
                    color = PbTheme.colors.muted,
                )
            }
            if (client != null) {
                PbIconButton(icon = PbSymbols.Close, contentDescription = "Quitar cliente", onClick = onRemove)
            } else {
                PbIcon(icon = PbSymbols.ChevronRight, contentDescription = null, tint = PbTheme.colors.muted2)
            }
        }
    }
}

// Líneas -------------------------------------------------------------------------

@Composable
internal fun LinesSection(
    draft: InvoiceDraft,
    amounts: Amounts,
    onOpenLine: (String) -> Unit,
    onQuantity: (String, Double) -> Unit,
    onAdd: () -> Unit,
) {
    PbSection(
        title = "Productos",
        icon = PbSymbols.Inventory2,
        trailing = {
            if (draft.lines.isNotEmpty()) {
                PbText(text = "${draft.lines.size}", style = PbTheme.typography.amount, color = PbTheme.colors.muted)
            }
        },
    ) {
        if (draft.lines.isEmpty()) {
            PbText(
                text = "Todavía no hay productos en este documento.",
                style = PbTheme.typography.body,
                color = PbTheme.colors.muted,
            )
        }
        draft.lines.forEachIndexed { index, line ->
            if (index > 0) Divider()
            LineRow(
                line = line,
                amounts = amounts,
                warnStock = !draft.isQuote,
                onOpen = { onOpenLine(line.key) },
                onQuantity = { delta -> onQuantity(line.key, delta) },
            )
        }
        PbButton(
            text = "Agregar producto",
            onClick = onAdd,
            variant = PbButtonVariant.Outline,
            leadingIcon = PbSymbols.Add,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LineRow(
    line: DraftLine,
    amounts: Amounts,
    warnStock: Boolean,
    onOpen: () -> Unit,
    onQuantity: (Double) -> Unit,
) {
    val colors = PbTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(PbSpace.s3)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PbRadius.sm))
                .clickable(role = Role.Button, onClickLabel = "Editar ${line.name}", onClick = onOpen)
                .heightIn(min = PbControl.minTouch),
            horizontalArrangement = Arrangement.spacedBy(PbSpace.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PbText(text = line.name, style = PbTheme.typography.bodyStrong, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val each = "${amounts.show(line.unitPrice)} c/u"
                val discount = if (line.discount > 0) " · desc. ${amounts.show(line.discount)}" else ""
                PbText(text = each + discount, style = PbTheme.typography.caption.withTabular(), color = colors.muted)
            }
            PbText(text = amounts.show(line.gross()), style = PbTheme.typography.amount, textAlign = TextAlign.End)
            PbIcon(icon = PbSymbols.Edit, contentDescription = null, tint = colors.muted2, size = PbIconSize.md)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PbSpace.s4)) {
            if (line.unique) {
                PbTag(text = "Único", tone = PbTagTone.Info)
            } else {
                PbStepper(
                    value = line.quantity,
                    onDecrease = { onQuantity(-1.0) },
                    onIncrease = { onQuantity(1.0) },
                    label = line.name,
                )
            }
            if (warnStock && line.exceedsStock) {
                // Aviso, no bloqueo (decisión 2026-09-06): la existencia puede quedar negativa.
                PbTag(text = "Hay ${formatQuantity(line.stock ?: 0.0)}", tone = PbTagTone.Warning)
            }
        }
    }
}

// Totales, impuesto y moneda ------------------------------------------------------

@Composable
internal fun TotalsSection(
    draft: InvoiceDraft,
    amounts: Amounts,
    taxLabel: String,
    onEditTax: () -> Unit,
    onCurrency: (Currency) -> Unit,
    onExchangeRate: (Double) -> Unit,
) {
    val colors = PbTheme.colors
    val totals = draft.totals
    PbSection(title = "Totales", icon = PbSymbols.ReceiptLong) {
        AmountRow("Subtotal", amounts.show(totals.subtotal))
        // La tasa se ve SIEMPRE junto al importe y se toca aquí mismo (guía 07).
        val taxText = when (draft.taxType) {
            TaxType.NoTax -> "Sin $taxLabel"
            TaxType.Included -> "$taxLabel ${formatPercent(draft.taxRate)} incl."
            TaxType.WithTax -> "$taxLabel ${formatPercent(draft.taxRate)}"
        }
        TappableRow(onClick = onEditTax, label = "Cambiar impuesto") {
            PbText(text = taxText, style = PbTheme.typography.body, color = colors.primary, modifier = Modifier.weight(1f))
            PbIcon(icon = PbSymbols.Edit, contentDescription = null, tint = colors.primary, size = PbIconSize.md)
            PbText(text = amounts.show(totals.tax), style = PbTheme.typography.amount)
        }
        if (totals.discount > 0) AmountRow("Descuento", "−${amounts.show(totals.discount)}")
        PbTotalBox(label = "Total", amount = amounts.show(totals.total))
        if (!draft.currency.isBase) {
            PbText(
                text = "Se guarda como ${amounts.showBase(totals.total)}",
                style = PbTheme.typography.caption.withTabular(),
                color = colors.muted,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Divider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PbSpace.s4)) {
            PbIcon(icon = PbSymbols.CurrencyExchange, contentDescription = null, tint = colors.primary)
            PbText(text = "Moneda", style = PbTheme.typography.bodyStrong)
        }
        PbChipGroup(
            options = Currency.entries,
            selected = draft.currency,
            label = { if (it.isBase) "$ Pesos" else it.code },
            onSelect = onCurrency,
        )
        if (!draft.currency.isBase) {
            PbNumberField(
                value = draft.exchangeRate,
                onValueChange = onExchangeRate,
                label = "Tasa: 1 ${draft.currency.code} = ? pesos",
                decimals = 4,
                prefix = "$",
                error = if (draft.exchangeRate <= 0.0) "Escribe a cuánto está el ${draft.currency.label.lowercase().removeSuffix("s")}." else null,
                modifier = Modifier.fillMaxWidth(),
            )
            PbBanner(
                message = "El POS no guarda la moneda: verá esta venta en pesos. El PDF es el único " +
                    "registro de la moneda, compártelo al momento.",
                tone = PbBannerTone.Offline,
            )
        }
    }
}

// Cobro --------------------------------------------------------------------------

@Composable
internal fun PaymentSection(
    draft: InvoiceDraft,
    amounts: Amounts,
    onCash: (Double) -> Unit,
    onTransfer: (Double) -> Unit,
    onCard: (Double) -> Unit,
    onPayAll: () -> Unit,
    onClear: () -> Unit,
    onAccount: () -> Unit,
    onDueInDays: (Int?) -> Unit,
) {
    val colors = PbTheme.colors
    val payment = draft.payment
    PbSection(title = "Cobro", icon = PbSymbols.Payments) {
        PbNumberField(
            value = amounts.inDocument(payment.cash),
            onValueChange = onCash,
            label = "Efectivo",
            prefix = amounts.symbol,
            modifier = Modifier.fillMaxWidth(),
        )
        PbNumberField(
            value = amounts.inDocument(payment.transfer),
            onValueChange = onTransfer,
            label = "Transferencia o depósito",
            prefix = amounts.symbol,
            modifier = Modifier.fillMaxWidth(),
        )
        PbNumberField(
            value = amounts.inDocument(payment.card),
            onValueChange = onCard,
            label = "Tarjeta",
            prefix = amounts.symbol,
            modifier = Modifier.fillMaxWidth(),
        )
        PbButton(
            text = "Cobrar todo en efectivo",
            onClick = onPayAll,
            variant = PbButtonVariant.Outline,
            enabled = draft.lines.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (payment.total > 0) {
            PbButton(text = "Borrar lo cobrado", onClick = onClear, variant = PbButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
        }

        Divider()
        AmountRow("Recibido", amounts.show(draft.paid))
        when {
            draft.missing > 0 -> AmountRow("Falta", amounts.show(draft.missing), valueColor = colors.accentText)
            draft.change > 0 -> AmountRow("Devuelta", amounts.show(draft.change), valueColor = colors.success)
        }
        if (draft.overpaidWithoutCash) {
            PbBanner(
                message = "Por transferencia o tarjeta se cobró más que el total, y eso no se devuelve. Corrígelo.",
                tone = PbBannerTone.Error,
            )
        }

        TappableRow(onClick = onAccount, label = "Elegir cuenta") {
            PbIcon(icon = PbSymbols.AccountBalance, contentDescription = null, tint = colors.primary)
            Column(Modifier.weight(1f)) {
                PbText(text = "Cuenta donde entra el dinero", style = PbTheme.typography.caption, color = colors.muted)
                PbText(text = draft.account?.name ?: "Ninguna", style = PbTheme.typography.bodyStrong)
            }
            PbIcon(icon = PbSymbols.ChevronRight, contentDescription = null, tint = colors.muted2)
        }

        if (draft.missing > 0) {
            PbBanner(
                message = "Queda debiendo ${amounts.show(draft.missing)}. La factura se guarda como pendiente " +
                    "y el saldo aparece en el POS, en cuentas por cobrar.",
                tone = PbBannerTone.Offline,
            )
            PbOverline(text = "Vence en")
            PbChipGroup(
                options = DUE_OPTIONS,
                selected = draft.dueInDays,
                label = { if (it == null) "Sin fecha" else "$it días" },
                onSelect = onDueInDays,
            )
        }
    }
}

private val DUE_OPTIONS: List<Int?> = listOf(null, 7, 15, 30)

// Dónde pagar ---------------------------------------------------------------------

/**
 * Cuentas donde el cliente puede transferir e instrucciones libres: salen en el PDF. Solo en
 * lo que se paga después (cotización o factura que queda debiendo, `InvoiceDraft.asksForPayment`).
 */
@Composable
internal fun PayToSection(
    draft: InvoiceDraft,
    accounts: List<DraftAccount>,
    onToggle: (DraftAccount) -> Unit,
    onNote: (String) -> Unit,
    onAddAccount: () -> Unit,
) {
    val colors = PbTheme.colors
    val chosen = draft.payTo.accounts.map { it.id }.toSet()
    PbSection(
        title = "Dónde pagar",
        icon = PbSymbols.AccountBalance,
        trailing = {
            val count = accounts.count { it.id in chosen }
            if (count > 0) PbText(text = "$count", style = PbTheme.typography.amount, color = colors.muted)
        },
    ) {
        PbText(
            text = "Sale en la factura para que el cliente sepa dónde transferir.",
            style = PbTheme.typography.caption,
            color = colors.muted,
        )
        if (accounts.isEmpty()) {
            PbText(
                text = "No tienes cuentas de banco con número de cuenta.",
                style = PbTheme.typography.body,
                color = colors.muted,
            )
        }
        accounts.forEach { account ->
            PbCheckRow(
                title = account.name,
                subtitle = listOfNotNull(account.bankName, account.accountNumber).joinToString(" · ").ifEmpty { null },
                checked = account.id in chosen,
                onCheckedChange = { onToggle(account) },
                leading = { BankLogo(account.bankName, fallback = PbSymbols.AccountBalance) },
            )
        }
        PbButton(
            text = "Agregar cuenta",
            onClick = onAddAccount,
            variant = PbButtonVariant.Ghost,
            leadingIcon = PbSymbols.Add,
            modifier = Modifier.fillMaxWidth(),
        )
        PbTextField(
            value = draft.payTo.note,
            onValueChange = onNote,
            label = "Instrucciones de pago",
            placeholder = "Envía el comprobante por WhatsApp al 809…",
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Comprobante fiscal ---------------------------------------------------------------

@Composable
internal fun FiscalSection(
    draft: InvoiceDraft,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onWithNcf: (Boolean) -> Unit,
    onNcfType: (NcfType) -> Unit,
    onRnc: (String) -> Unit,
) {
    // Plegado, salvo que ya traiga valor (regla del POS, guía 07).
    val open = expanded || draft.withNcf
    PbSection(
        title = "Comprobante fiscal",
        icon = PbSymbols.Description,
        trailing = {
            if (!draft.withNcf) {
                PbIconButton(
                    icon = if (open) PbSymbols.ExpandLess else PbSymbols.ExpandMore,
                    contentDescription = if (open) "Plegar" else "Desplegar",
                    onClick = onToggleExpanded,
                )
            }
        },
    ) {
        if (!open) {
            PbText(text = "Sin NCF", style = PbTheme.typography.body, color = PbTheme.colors.muted)
            return@PbSection
        }
        PbSwitchRow(
            title = "Llevar NCF",
            subtitle = if (draft.withNcf) "Se pide al enviar la factura" else "Esta venta no llevará comprobante fiscal",
            checked = draft.withNcf,
            onCheckedChange = onWithNcf,
        )
        if (draft.withNcf) {
            PbChipGroup(
                options = NcfType.entries,
                selected = draft.ncfType,
                label = { "${it.code} · ${it.label}" },
                onSelect = onNcfType,
            )
            PbTextField(
                value = draft.rnc,
                onValueChange = onRnc,
                label = "RNC o cédula del cliente",
                required = draft.ncfType == NcfType.B01,
                placeholder = "Solo números",
                numeric = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            PbText(
                text = "Un NCF usado no se devuelve: si la factura no llega a enviarse, ese número se pierde.",
                style = PbTheme.typography.caption,
                color = PbTheme.colors.muted,
            )
            if (!draft.currency.isBase) {
                PbBanner(
                    message = "Comprobante fiscal en moneda extranjera: confirma con tu contador si debe emitirse en pesos.",
                    tone = PbBannerTone.Error,
                )
            }
        }
    }
}

// Piezas comunes --------------------------------------------------------------------

@Composable
internal fun AmountRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        PbText(text = label, style = PbTheme.typography.body, color = PbTheme.colors.muted, modifier = Modifier.weight(1f))
        PbText(text = value, style = PbTheme.typography.amount, color = valueColor, textAlign = TextAlign.End)
    }
}

@Composable
internal fun TappableRow(
    onClick: () -> Unit,
    label: String,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PbControl.h)
            .clip(RoundedCornerShape(PbRadius.sm))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PbSpace.s4),
        content = content,
    )
}

@Composable
internal fun Divider() {
    Box(Modifier.fillMaxWidth().height(PbControl.border).background(PbTheme.colors.outline))
}

private fun TextStyle.withTabular() = copy(fontFeatureSettings = "tnum")
