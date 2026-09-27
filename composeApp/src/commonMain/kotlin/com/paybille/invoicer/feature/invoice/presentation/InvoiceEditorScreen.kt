package com.paybille.invoicer.feature.invoice.presentation

import com.paybille.invoicer.core.ui.BankLogo
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.paybille.invoicer.core.billing.TaxType
import com.paybille.invoicer.core.format.formatQuantity
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbChipGroup
import com.paybille.invoicer.core.designsystem.components.PbIconButton
import com.paybille.invoicer.core.designsystem.components.PbListRow
import com.paybille.invoicer.core.designsystem.components.PbNumberField
import com.paybille.invoicer.core.designsystem.components.PbSheet
import com.paybille.invoicer.core.designsystem.components.PbSpinner
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.components.PbTopBar
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.formatPercent
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.domain.DraftLine
import com.paybille.invoicer.feature.invoice.domain.InvoiceDraft
import com.paybille.invoicer.feature.banks.presentation.BankAccountEditorScreen
import com.paybille.invoicer.feature.detail.presentation.SaleDetailScreen
import org.koin.core.parameter.parametersOf

/**
 * Editor de factura o cotización: una sola pantalla con scroll (guía 07).
 * Cliente → Productos → Totales (impuesto y moneda) → Cobro → Comprobante, y el botón de
 * guardar fijo abajo con el total escrito dentro.
 */
data class InvoiceEditorScreen(val kind: DocumentKind) : Screen {
    override val key: String = "invoice-editor-${kind.name}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<InvoiceEditorScreenModel> { parametersOf(kind) }
        val state by model.state.collectAsState()

        LaunchedEffect(state.finished) { if (state.finished) navigator.pop() }
        // Guardado: el editor se cambia por el detalle, que enseña el PDF en cuanto llega.
        LaunchedEffect(state.savedLocalId) {
            state.savedLocalId?.let { navigator.replace(SaleDetailScreen(localId = it)) }
        }

        val draft = state.draft
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = state.sheet == null && draft != null && draft.lines.isNotEmpty(),
            onBackCompleted = { model.onBack() },
        )

        Box(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
            Column(Modifier.fillMaxSize()) {
                EditorTopBar(
                    // Desde el primer frame: el título no cambia delante del usuario.
                    title = kind.title,
                    onBack = { if (model.onBack()) navigator.pop() },
                )
                if (draft == null) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { PbSpinner() }
                } else {
                    EditorBody(
                        draft = draft,
                        state = state,
                        model = model,
                        onPickClient = { navigator.push(ClientPickerScreen(kind)) },
                        onAddProduct = { navigator.push(ProductPickerScreen(kind)) },
                        onAddAccount = { navigator.push(BankAccountEditorScreen()) },
                        modifier = Modifier.weight(1f),
                    )
                    SaveBar(draft = draft, state = state, onSave = model::save)
                }
            }

            if (draft != null) EditorSheets(draft = draft, state = state, model = model)
        }
    }
}

@Composable
private fun EditorTopBar(title: String, onBack: () -> Unit) {
    Column(
        Modifier
            .background(PbTheme.colors.island)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        PbTopBar(
            title = title,
            navigation = { PbIconButton(icon = PbSymbols.ArrowBack, contentDescription = "Volver", onClick = onBack) },
        )
        Divider()
    }
}

@Composable
private fun EditorBody(
    draft: InvoiceDraft,
    state: EditorUiState,
    model: InvoiceEditorScreenModel,
    onPickClient: () -> Unit,
    onAddProduct: () -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier,
) {
    val amounts = Amounts(draft)
    var fiscalExpanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = EDITOR_MAX_WIDTH)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(PbSpace.s6),
            verticalArrangement = Arrangement.spacedBy(PbSpace.s5),
        ) {
            ClientSection(draft = draft, onPick = onPickClient, onRemove = model::removeClient)
            LinesSection(
                draft = draft,
                amounts = amounts,
                onOpenLine = model::openLine,
                onQuantity = model::changeQuantity,
                onAdd = onAddProduct,
            )
            TotalsSection(
                draft = draft,
                amounts = amounts,
                taxLabel = state.taxLabel,
                onEditTax = model::openTax,
                onCurrency = model::setCurrency,
                onExchangeRate = model::setExchangeRate,
            )
            val payTo: @Composable () -> Unit = {
                PayToSection(
                    draft = draft,
                    accounts = state.transferAccounts,
                    onToggle = model::togglePayTo,
                    onNote = model::setPaymentNote,
                    onAddAccount = onAddAccount,
                )
            }
            if (!draft.isQuote) {
                PaymentSection(
                    draft = draft,
                    amounts = amounts,
                    onCash = model::setCash,
                    onTransfer = model::setTransfer,
                    onCard = model::setCard,
                    onPayAll = model::payRemainingInCash,
                    onClear = model::clearPayment,
                    onAccount = model::openAccounts,
                    onDueInDays = model::setDueInDays,
                )
                // Justo debajo del cobro: aparece en cuanto la factura queda debiendo.
                if (draft.asksForPayment) payTo()
                FiscalSection(
                    draft = draft,
                    expanded = fiscalExpanded,
                    onToggleExpanded = { fiscalExpanded = !fiscalExpanded },
                    onWithNcf = model::setWithNcf,
                    onNcfType = model::setNcfType,
                    onRnc = model::setRnc,
                )
            } else {
                payTo()
                PbText(
                    text = "Una cotización no descuenta inventario, no cobra y no lleva NCF.",
                    style = PbTheme.typography.caption,
                    color = PbTheme.colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PbText(
                text = "Se guarda en el teléfono y se envía en cuanto haya conexión.",
                style = PbTheme.typography.caption,
                color = PbTheme.colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Botón fijo abajo, siempre visible (también con el teclado abierto), con el total dentro. */
@Composable
private fun SaveBar(draft: InvoiceDraft, state: EditorUiState, onSave: () -> Unit) {
    val amounts = Amounts(draft)
    val total = amounts.show(draft.totals.total)
    val label = when {
        draft.isQuote -> "Guardar cotización · $total"
        draft.isOnCredit && draft.paid > 0 -> "Guardar con abono · $total"
        draft.isOnCredit -> "Guardar a crédito · $total"
        else -> "Cobrar $total"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(PbTheme.colors.island)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Divider()
        Column(
            Modifier.widthIn(max = EDITOR_MAX_WIDTH).fillMaxWidth().padding(PbSpace.s6),
            verticalArrangement = Arrangement.spacedBy(PbSpace.s3),
        ) {
            state.message?.let { PbBanner(message = it, tone = PbBannerTone.Error) }
            PbButton(
                text = label,
                onClick = onSave,
                loading = state.saving,
                enabled = draft.lines.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!draft.currency.isBase) {
                // Es la cifra que queda guardada: no se esconde (guía 07).
                PbText(
                    text = "Se guarda como ${amounts.showBase(draft.totals.total)}",
                    style = PbTheme.typography.caption,
                    color = PbTheme.colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EditorSheets(draft: InvoiceDraft, state: EditorUiState, model: InvoiceEditorScreenModel) {
    val sheet = state.sheet
    val line = (sheet as? EditorSheet.Line)?.let { s -> draft.lines.firstOrNull { it.key == s.key } }

    PbSheet(visible = line != null, title = line?.name ?: "", onDismiss = model::closeSheet) {
        if (line != null) LineSheetContent(draft, line, model)
    }
    PbSheet(visible = sheet == EditorSheet.Tax, title = "Impuesto de este documento", onDismiss = model::closeSheet) {
        TaxSheetContent(draft, state.taxLabel, model)
    }
    PbSheet(visible = sheet == EditorSheet.Account, title = "Cuenta donde entra el dinero", onDismiss = model::closeSheet) {
        AccountSheetContent(draft, state.accounts, model)
    }
    PbSheet(visible = sheet == EditorSheet.Exit, title = "¿Salir del editor?", onDismiss = model::closeSheet) {
        PbText(
            text = "Lo que escribiste queda guardado como borrador en el teléfono. Lo encontrarás al volver a tocar +.",
            style = PbTheme.typography.body,
            color = PbTheme.colors.ink2,
        )
        PbButton(text = "Seguir editando", onClick = model::closeSheet, modifier = Modifier.fillMaxWidth())
        PbButton(
            text = "Salir y guardar borrador",
            onClick = model::keepAndExit,
            variant = PbButtonVariant.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
        PbButton(
            text = "Descartar borrador",
            onClick = model::discardAndExit,
            variant = PbButtonVariant.Danger,
            leadingIcon = PbSymbols.Delete,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LineSheetContent(draft: InvoiceDraft, line: DraftLine, model: InvoiceEditorScreenModel) {
    val amounts = Amounts(draft)
    if (line.unique) {
        PbText(text = "Artículo único: se vende una sola unidad.", style = PbTheme.typography.body, color = PbTheme.colors.muted)
    } else {
        PbNumberField(
            value = line.quantity,
            onValueChange = { model.setQuantity(line.key, it) },
            label = "Cantidad",
            decimals = 3,
            error = if (line.quantity <= 0.0) "La cantidad tiene que ser mayor que cero." else null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PbNumberField(
        value = amounts.inDocument(line.unitPrice),
        onValueChange = { model.setUnitPrice(line.key, it) },
        label = "Precio por unidad",
        prefix = amounts.symbol,
        modifier = Modifier.fillMaxWidth(),
    )
    PbNumberField(
        value = amounts.inDocument(line.discount),
        onValueChange = { model.setDiscount(line.key, it) },
        label = "Descuento de la línea",
        prefix = amounts.symbol,
        modifier = Modifier.fillMaxWidth(),
    )
    if (line.tracksStock && line.stock != null && !draft.isQuote) {
        val stockText = "Hay ${formatQuantity(line.stock)} en inventario."
        PbText(
            text = if (line.exceedsStock) "$stockText Se puede vender igual: la existencia quedará en negativo." else stockText,
            style = PbTheme.typography.caption,
            color = if (line.exceedsStock) PbTheme.colors.accentText else PbTheme.colors.muted,
        )
    }
    AmountRow("Total de la línea", amounts.show(line.gross()))
    PbButton(text = "Listo", onClick = model::closeSheet, modifier = Modifier.fillMaxWidth())
    PbButton(
        text = "Quitar producto",
        onClick = { model.removeLine(line.key) },
        variant = PbButtonVariant.Danger,
        leadingIcon = PbSymbols.Delete,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TaxSheetContent(draft: InvoiceDraft, taxLabel: String, model: InvoiceEditorScreenModel) {
    PbText(
        text = "Solo cambia este documento. El siguiente vuelve a empezar con el de la tienda.",
        style = PbTheme.typography.caption,
        color = PbTheme.colors.muted,
    )
    PbChipGroup(
        options = TaxType.entries,
        selected = draft.taxType,
        label = { it.label },
        onSelect = model::setTaxType,
    )
    if (draft.taxType != TaxType.NoTax) {
        PbNumberField(
            value = draft.taxRate * 100,
            onValueChange = model::setTaxPercent,
            label = "Tasa de $taxLabel",
            suffix = "%",
            modifier = Modifier.fillMaxWidth(),
        )
        PbChipGroup(
            options = TAX_PRESETS,
            selected = TAX_PRESETS.firstOrNull { it != null && abs(it - draft.taxRate) < 0.00001 },
            label = { formatPercent(it ?: 0.0) },
            onSelect = { rate -> if (rate != null) model.setTaxPercent(rate * 100) },
        )
    }
    PbButton(text = "Listo", onClick = model::closeSheet, modifier = Modifier.fillMaxWidth())
}

private val TAX_PRESETS: List<Double?> = listOf(0.18, 0.16, 0.0)

@Composable
private fun AccountSheetContent(draft: InvoiceDraft, accounts: AccountsState, model: InvoiceEditorScreenModel) {
    PbListRow(
        title = "Ninguna",
        subtitle = "No se registra movimiento en ninguna cuenta",
        leadingIcon = PbSymbols.Close,
        onClick = { model.selectAccount(null) },
        trailingText = if (draft.account == null) "Elegida" else null,
    )
    when {
        accounts.loading -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { PbSpinner() }
        accounts.error != null -> {
            PbBanner(message = accounts.error, tone = PbBannerTone.Offline)
            PbButton(text = "Reintentar", onClick = model::loadAccounts, variant = PbButtonVariant.Outline, modifier = Modifier.fillMaxWidth())
        }
        accounts.items.isEmpty() -> PbText(
            text = "La tienda no tiene cuentas activas. Se crean en el POS.",
            style = PbTheme.typography.body,
            color = PbTheme.colors.muted,
        )
        else -> accounts.items.forEach { account ->
            PbListRow(
                title = account.name,
                subtitle = account.bankName?.takeIf { it.isNotBlank() },
                leading = {
                    BankLogo(account.bankName, fallback = if (account.type == "Caja") PbSymbols.Payments else PbSymbols.AccountBalance)
                },
                onClick = { model.selectAccount(account) },
                trailingText = if (draft.account?.id == account.id) "Elegida" else null,
            )
        }
    }
}

private val EDITOR_MAX_WIDTH = 640.dp
