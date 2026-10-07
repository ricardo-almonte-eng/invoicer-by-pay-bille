package com.paybille.invoicer.feature.detail.presentation

import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbChipGroup
import com.paybille.invoicer.core.designsystem.components.PbIcon
import com.paybille.invoicer.core.designsystem.components.PbIconButton
import com.paybille.invoicer.core.designsystem.components.PbNumberField
import com.paybille.invoicer.core.designsystem.components.PbSection
import com.paybille.invoicer.core.designsystem.components.PbSheet
import com.paybille.invoicer.core.designsystem.components.PbSpinner
import com.paybille.invoicer.core.designsystem.components.PbTag
import com.paybille.invoicer.core.designsystem.components.PbTagTone
import com.paybille.invoicer.core.designsystem.components.PbOverline
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.components.PbTotalBox
import com.paybille.invoicer.core.designsystem.components.PbTextField
import com.paybille.invoicer.core.designsystem.components.PbTopBar
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.formatIsoDate
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.format.formatQuantity
import com.paybille.invoicer.core.format.formatShortDate
import com.paybille.invoicer.core.format.parseApiTimestamp
import com.paybille.invoicer.core.platform.HtmlView
import com.paybille.invoicer.feature.detail.domain.PaymentMethod
import com.paybille.invoicer.feature.detail.domain.SaleDetail
import com.paybille.invoicer.feature.detail.domain.dueState
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.domain.DraftAccount
import com.paybille.invoicer.feature.invoice.domain.PendingDocument
import com.paybille.invoicer.feature.sales.domain.SaleStatus
import com.paybille.invoicer.feature.sales.presentation.label
import org.koin.core.parameter.parametersOf

/**
 * Detalle de una venta. `saleId` para una que ya está en el servidor; `localId` para la que
 * se acaba de guardar y todavía se está enviando (la pantalla espera a que llegue).
 */
data class SaleDetailScreen(val saleId: Int? = null, val localId: String? = null) : Screen {
    override val key: String = "sale-detail-${saleId ?: localId}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<SaleDetailScreenModel> { parametersOf(DetailTarget(saleId, localId)) }
        val state by model.state.collectAsState()

        Box(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
            Column(Modifier.fillMaxSize()) {
                DetailTopBar(
                    title = title(state),
                    documentReady = state.document is DocumentState.Ready && !state.exporting,
                    onBack = { navigator.pop() },
                    onShare = model::share,
                    onDownload = model::download,
                )
                DetailBody(
                    state = state,
                    model = model,
                    onOpenDocument = { html ->
                        state.saleId?.let { navigator.push(InvoiceViewerScreen(it, html, title(state))) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            PaymentSheet(state = state, model = model)
        }
    }
}

private fun title(state: DetailUiState): String {
    val detail = state.detail
    val pending = state.pending
    return when {
        detail != null -> "${if (detail.status == SaleStatus.QUOTE) "Cotización" else "Factura"} #${detail.number}"
        pending != null -> if (pending.kind == DocumentKind.Quote) "Nueva cotización" else "Nueva factura"
        else -> "Factura"
    }
}

@Composable
private fun DetailTopBar(
    title: String,
    documentReady: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(
        Modifier
            .background(PbTheme.colors.island)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        PbTopBar(
            title = title,
            navigation = { PbIconButton(icon = PbSymbols.ArrowBack, contentDescription = "Volver", onClick = onBack) },
            actions = {
                if (documentReady) {
                    PbIconButton(icon = PbSymbols.Download, contentDescription = "Descargar PDF", onClick = onDownload)
                    PbIconButton(icon = PbSymbols.Share, contentDescription = "Compartir PDF", onClick = onShare)
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(PbControl.border).background(PbTheme.colors.outline))
    }
}

@Composable
private fun DetailBody(
    state: DetailUiState,
    model: SaleDetailScreenModel,
    onOpenDocument: (String) -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(PbSpace.s6),
            verticalArrangement = Arrangement.spacedBy(PbSpace.s5),
        ) {
            state.notice?.let { notice ->
                LaunchedEffect(notice) {
                    kotlinx.coroutines.delay(NOTICE_MS)
                    model.dismissNotice()
                }
                PbBanner(message = notice, tone = PbBannerTone.Success)
            }

            val pending = state.pending
            if (state.saleId == null) {
                PendingHero(pending = pending, onRetry = model::retrySend)
                return@Column
            }

            DocumentHero(state = state, onRetry = model::refreshDocument, onOpen = onOpenDocument)
            if (state.document is DocumentState.Ready) {
                PbButton(
                    text = "Compartir",
                    onClick = model::share,
                    loading = state.exporting,
                    leadingIcon = PbSymbols.Share,
                    modifier = Modifier.fillMaxWidth(),
                )
                PbButton(
                    text = "Descargar PDF",
                    onClick = model::download,
                    variant = PbButtonVariant.Outline,
                    enabled = !state.exporting,
                    leadingIcon = PbSymbols.Download,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when {
                state.offline -> PbBanner("Sin conexión. Mostrando lo guardado en el teléfono.", PbBannerTone.Offline)
                state.error != null -> PbBanner(state.error, PbBannerTone.Error)
            }

            val detail = state.detail
            if (detail == null) {
                if (state.refreshing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { PbSpinner() }
                return@Column
            }
            CollectionSection(detail = detail, todayIso = state.todayIso, onPay = model::openPayment)
            detail.receivable?.takeIf { it.payments.isNotEmpty() }?.let { PaymentsSection(detail, state.timeZone) }
            ContentSection(detail = detail, timeZone = state.timeZone)
        }
    }
}

// La factura como protagonista ------------------------------------------------------------

/**
 * La factura que genera el teléfono, en un visor HTML con proporción de hoja A4. Aquí no hace
 * scroll ni zoom: tocarla abre el visor completo.
 */
@Composable
private fun DocumentHero(
    state: DetailUiState,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val colors = PbTheme.colors
    val shape = RoundedCornerShape(PbRadius.md)
    val frame = Modifier
        .widthIn(max = 420.dp)
        .fillMaxWidth()
        .aspectRatio(A4_RATIO)
        .clip(shape)
        .border(PbControl.border, colors.outlineStrong, shape)

    val document = state.document
    if (document is DocumentState.Ready) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PbSpace.s3),
        ) {
            Box(frame) {
                HtmlView(html = document.html, modifier = Modifier.fillMaxSize(), interactive = false)
                // Encima del visor: el toque es de Compose, no del WebView.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(role = Role.Button, onClickLabel = "Ver la factura completa") { onOpen(document.html) }
                        .semantics { contentDescription = "Vista previa de la factura. Toca para verla completa." },
                )
            }
            PbText(text = "Toca para verla completa", style = PbTheme.typography.caption, color = colors.muted)
        }
        return
    }

    // Mientras no hay datos: una hoja vacía con el estado en el centro.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = frame.background(colors.surfaceSubtle).padding(PbSpace.s8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PbSpace.s4, Alignment.CenterVertically),
        ) {
            when (document) {
                DocumentState.Loading, is DocumentState.Ready -> {
                    PbSpinner(size = 32.dp)
                    PbText(
                        text = "Preparando la factura…",
                        style = PbTheme.typography.bodyStrong,
                        color = colors.ink2,
                        textAlign = TextAlign.Center,
                    )
                }
                DocumentState.Offline -> {
                    PbIcon(icon = PbSymbols.CloudOff, contentDescription = null, tint = colors.muted, size = 40.dp)
                    PbText(
                        text = "Sin conexión: la factura se verá cuando vuelva la red. Después queda guardada en el teléfono.",
                        style = PbTheme.typography.body,
                        color = colors.ink2,
                        textAlign = TextAlign.Center,
                    )
                    PbButton(text = "Reintentar", onClick = onRetry, variant = PbButtonVariant.Outline, leadingIcon = PbSymbols.Sync)
                }
                is DocumentState.Failed -> {
                    PbIcon(icon = PbSymbols.Error, contentDescription = null, tint = colors.error, size = 40.dp)
                    PbText(
                        text = document.message.ifBlank { "No se pudo obtener la factura." },
                        style = PbTheme.typography.body,
                        color = colors.ink2,
                        textAlign = TextAlign.Center,
                    )
                    PbButton(text = "Reintentar", onClick = onRetry, variant = PbButtonVariant.Outline, leadingIcon = PbSymbols.Sync)
                }
            }
        }
    }
}

/** Documento recién guardado que todavía no llegó al servidor (offline first). */
@Composable
private fun PendingHero(pending: PendingDocument?, onRetry: () -> Unit) {
    val colors = PbTheme.colors
    val shape = RoundedCornerShape(PbRadius.md)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .aspectRatio(A4_RATIO)
            .clip(shape)
            .background(colors.surfaceSubtle)
            .border(PbControl.border, colors.outlineStrong, shape)
            .padding(PbSpace.s8)
            .semantics { contentDescription = "Estado del envío" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PbSpace.s4, Alignment.CenterVertically),
    ) {
        when {
            pending == null || pending.sending -> {
                PbSpinner(size = 32.dp)
                PbText("Enviando…", style = PbTheme.typography.subtitle, textAlign = TextAlign.Center)
                PbText(
                    "En cuanto llegue al servidor verás aquí la factura.",
                    style = PbTheme.typography.body,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                )
            }
            pending.failed -> {
                PbIcon(icon = PbSymbols.Error, contentDescription = null, tint = colors.error, size = 40.dp)
                PbText("No se pudo enviar", style = PbTheme.typography.subtitle, textAlign = TextAlign.Center)
                PbText(pending.error ?: "El servidor rechazó el documento.", style = PbTheme.typography.body, color = colors.ink2, textAlign = TextAlign.Center)
                PbButton(text = "Reintentar envío", onClick = onRetry, leadingIcon = PbSymbols.Sync)
            }
            else -> {
                PbIcon(icon = PbSymbols.CloudOff, contentDescription = null, tint = colors.muted, size = 40.dp)
                PbText("Guardada en el teléfono", style = PbTheme.typography.subtitle, textAlign = TextAlign.Center)
                PbText(
                    "Sin conexión: se enviará sola cuando vuelva la red. No la vuelvas a crear.",
                    style = PbTheme.typography.body,
                    color = colors.ink2,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (pending != null) {
            PbText(
                text = "${pending.clientName} · ${formatMoney(pending.total)}",
                style = PbTheme.typography.amount,
                color = colors.muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Cobro y vencimiento -------------------------------------------------------------

@Composable
private fun CollectionSection(detail: SaleDetail, todayIso: String, onPay: () -> Unit) {
    val colors = PbTheme.colors
    val (statusText, statusTone) = SaleStatus.from(detail.status).label()
    val receivable = detail.receivable
    PbSection(
        title = if (detail.status == SaleStatus.QUOTE) "Cotización" else "Cobro",
        icon = PbSymbols.Payments,
        trailing = { PbTag(text = statusText, tone = statusTone) },
    ) {
        PbTotalBox(label = "Total", amount = formatMoney(detail.total))
        if (receivable != null) {
            AmountLine("Pagado", formatMoney(receivable.paid))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PbText("Saldo pendiente", style = PbTheme.typography.bodyStrong, modifier = Modifier.weight(1f))
                PbText(
                    formatMoney(receivable.balance),
                    style = PbTheme.typography.amountTitle,
                    color = if (receivable.isOpen) colors.accentText else colors.success,
                )
            }
            if (receivable.lateFeeOutstanding > 0.004) AmountLine("Mora pendiente", formatMoney(receivable.lateFeeOutstanding))

            if (receivable.isOpen) {
                val due = receivable.effectiveDueDate
                val dueText = dueState(due, todayIso).toText()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PbSpace.s4)) {
                    PbIcon(icon = PbSymbols.Event, contentDescription = null, tint = dueText.color)
                    Column(Modifier.weight(1f)) {
                        PbText(dueText.text, style = PbTheme.typography.bodyStrong, color = dueText.color)
                        if (due != null) PbText("Fecha: ${formatIsoDate(due)}", style = PbTheme.typography.caption, color = colors.muted)
                    }
                }
                PbButton(text = "Registrar pago", onClick = onPay, leadingIcon = PbSymbols.Payments, modifier = Modifier.fillMaxWidth())
            }

            if (receivable.installments.isNotEmpty()) {
                PbOverline(text = "Cuotas")
                receivable.installments.forEach { inst ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PbText("${inst.number}. ${formatIsoDate(inst.dueDate)}", style = PbTheme.typography.body, modifier = Modifier.weight(1f))
                        PbText(
                            if (inst.balance > 0.004) formatMoney(inst.balance) else "Pagada",
                            style = PbTheme.typography.amount,
                            color = if (inst.balance > 0.004) colors.ink else colors.success,
                        )
                    }
                }
            }
        } else if (detail.status == SaleStatus.COMPLETE) {
            PbText("Pagada por completo.", style = PbTheme.typography.body, color = colors.success)
        } else if (detail.status == SaleStatus.QUOTE) {
            PbText("Una cotización no se cobra: se convierte en factura.", style = PbTheme.typography.body, color = colors.muted)
        }
    }
}

@Composable
private fun PaymentsSection(detail: SaleDetail, timeZone: String) {
    val colors = PbTheme.colors
    val payments = detail.receivable?.payments.orEmpty()
    PbSection(title = "Abonos", icon = PbSymbols.AccountBalance) {
        payments.forEachIndexed { index, payment ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(PbControl.border).background(colors.outline))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PbSpace.s4)) {
                Column(Modifier.weight(1f)) {
                    val date = parseApiTimestamp(payment.date)?.let { formatShortDate(it, timeZone) } ?: payment.date?.take(10).orEmpty()
                    PbText(methodLabel(payment.method), style = PbTheme.typography.bodyStrong)
                    PbText(
                        listOfNotNull(date, payment.reference).joinToString(" · "),
                        style = PbTheme.typography.caption,
                        color = colors.muted,
                    )
                }
                if (payment.voided) PbTag("Anulado", PbTagTone.Danger)
                PbText(
                    formatMoney(payment.amount + payment.lateFee),
                    style = PbTheme.typography.amount,
                    color = if (payment.voided) colors.muted2 else colors.ink,
                )
            }
        }
    }
}

private fun methodLabel(apiValue: String): String =
    PaymentMethod.entries.firstOrNull { it.apiValue == apiValue }?.label ?: apiValue

// Contenido de la factura --------------------------------------------------------------

@Composable
private fun ContentSection(detail: SaleDetail, timeZone: String) {
    val colors = PbTheme.colors
    PbSection(title = "Detalle", icon = PbSymbols.ReceiptLong) {
        InfoLine("Cliente", detail.clientName)
        val date = detail.createdAt?.let { formatShortDate(it, timeZone) } ?: detail.dateText
        date?.let { InfoLine("Fecha", it) }
        detail.ncf?.let { InfoLine("NCF", it) }
        detail.rnc?.let { InfoLine("RNC", it) }

        Box(Modifier.fillMaxWidth().height(PbControl.border).background(colors.outline))
        detail.lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(PbSpace.s4)) {
                Column(Modifier.weight(1f)) {
                    PbText(line.name, style = PbTheme.typography.bodyStrong, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    PbText(
                        "${formatQuantity(line.quantity)} × ${formatMoney(line.price)}" +
                            if (line.discount > 0) " · desc. ${formatMoney(line.discount)}" else "",
                        style = PbTheme.typography.caption,
                        color = colors.muted,
                    )
                }
                PbText(formatMoney(line.total), style = PbTheme.typography.amount)
            }
        }
        Box(Modifier.fillMaxWidth().height(PbControl.border).background(colors.outline))
        AmountLine("Subtotal", formatMoney(detail.subtotal))
        AmountLine("Impuesto", formatMoney(detail.tax))
        if (detail.discount > 0) AmountLine("Descuento", "−${formatMoney(detail.discount)}")
        AmountLine("Total", formatMoney(detail.total))
        if (detail.status != SaleStatus.QUOTE) {
            if (detail.cash > 0) AmountLine("Efectivo", formatMoney(detail.cash))
            if (detail.transfer > 0) AmountLine("Transferencia", formatMoney(detail.transfer))
            if (detail.card > 0) AmountLine("Tarjeta", formatMoney(detail.card))
            if (detail.change > 0) AmountLine("Devuelta", formatMoney(detail.change))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(PbSpace.s5)) {
        PbText(label, style = PbTheme.typography.body, color = PbTheme.colors.muted)
        PbText(value, style = PbTheme.typography.bodyStrong, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AmountLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PbText(label, style = PbTheme.typography.body, color = PbTheme.colors.muted, modifier = Modifier.weight(1f))
        PbText(value, style = PbTheme.typography.amount, textAlign = TextAlign.End)
    }
}

// Registrar pago -------------------------------------------------------------------------

@Composable
private fun PaymentSheet(state: DetailUiState, model: SaleDetailScreenModel) {
    val form = state.payment
    val balance = state.detail?.receivable?.balance ?: 0.0
    PbSheet(visible = state.paymentOpen, title = "Registrar pago", onDismiss = model::closePayment) {
        PbText("Saldo pendiente: ${formatMoney(balance)}", style = PbTheme.typography.amount, color = PbTheme.colors.ink2)
        PbNumberField(
            value = form.amount,
            onValueChange = model::setPaymentAmount,
            label = "Monto recibido",
            prefix = "$",
            error = form.error,
            modifier = Modifier.fillMaxWidth(),
        )
        PbOverline(text = "Cómo pagó")
        PbChipGroup(
            options = PaymentMethod.entries,
            selected = form.method,
            label = { it.label },
            onSelect = model::setPaymentMethod,
        )
        PbOverline(text = "Cuenta donde entra el dinero", maxLines = 2)
        val accounts: List<DraftAccount?> = listOf<DraftAccount?>(null) + state.accounts
        PbChipGroup(
            options = accounts,
            selected = form.account,
            label = { it?.name ?: "Ninguna" },
            onSelect = model::setPaymentAccount,
        )
        state.accountsError?.let { PbText(it, style = PbTheme.typography.caption, color = PbTheme.colors.muted) }
        PbTextField(
            value = form.reference,
            onValueChange = model::setPaymentReference,
            label = "Referencia (opcional)",
            placeholder = "No. de transferencia, recibo…",
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth(),
        )
        PbText(
            "El servidor recalcula el saldo y, si eliges cuenta, registra él el ingreso.",
            style = PbTheme.typography.caption,
            color = PbTheme.colors.muted,
        )
        PbButton(
            text = "Registrar ${formatMoney(form.amount)}",
            onClick = model::submitPayment,
            loading = form.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Proporción de una hoja A4 (ancho / alto) para la vista previa de la factura. */
private const val A4_RATIO = 210f / 297f
private const val NOTICE_MS = 4_000L
