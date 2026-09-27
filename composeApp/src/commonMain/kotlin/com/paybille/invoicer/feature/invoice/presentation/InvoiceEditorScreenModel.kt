package com.paybille.invoicer.feature.invoice.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.billing.Currency
import com.paybille.invoicer.core.billing.TaxType
import com.paybille.invoicer.core.billing.isValidTaxRate
import com.paybille.invoicer.core.billing.round2
import com.paybille.invoicer.core.billing.toBaseCurrency
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.domain.Session
import com.paybille.invoicer.feature.auth.domain.SessionState
import com.paybille.invoicer.feature.banks.data.BanksRepository
import com.paybille.invoicer.feature.invoice.data.InvoiceRepository
import com.paybille.invoicer.feature.invoice.data.InvoiceSender
import com.paybille.invoicer.feature.invoice.data.remote.CatalogRemoteDataSource
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.domain.DraftAccount
import com.paybille.invoicer.feature.invoice.domain.DraftPayment
import com.paybille.invoicer.feature.invoice.domain.InvoiceDraft
import com.paybille.invoicer.feature.invoice.domain.NcfType
import com.paybille.invoicer.feature.invoice.domain.PayTo
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Hoja abierta sobre el editor. */
sealed interface EditorSheet {
    data class Line(val key: String) : EditorSheet
    data object Tax : EditorSheet
    data object Account : EditorSheet
    data object Exit : EditorSheet
}

data class AccountsState(
    val loading: Boolean = false,
    val items: List<DraftAccount> = emptyList(),
    val error: String? = null,
)

data class EditorUiState(
    val draft: InvoiceDraft? = null,
    val taxLabel: String = "ITBIS",
    val timeZone: String? = null,
    val sheet: EditorSheet? = null,
    val accounts: AccountsState = AccountsState(),
    /** Cuentas de banco con número de la tienda (Room): las que se pueden ofrecer en "Dónde pagar". */
    val transferAccounts: List<DraftAccount> = emptyList(),
    val saving: Boolean = false,
    /** Motivo por el que no se pudo guardar. */
    val message: String? = null,
    /** Se guardó: la pantalla se cierra. */
    val finished: Boolean = false,
    /** Id local del documento recién guardado: la pantalla abre su detalle. */
    val savedLocalId: String? = null,
)

/**
 * Editor de factura y cotización (guía 07, "El editor de factura").
 *
 * El borrador vive en Room: cada acción lo modifica allí y la pantalla lo observa. Los
 * importes del borrador están SIEMPRE en moneda base; lo que el usuario teclea en otra
 * moneda se convierte aquí, con `core/billing/Money.kt`, antes de guardarlo.
 */
class InvoiceEditorScreenModel(
    val kind: DocumentKind,
    private val sessions: SessionRepository,
    private val invoices: InvoiceRepository,
    private val catalog: CatalogRemoteDataSource,
    private val sender: InvoiceSender,
    private val banks: BanksRepository,
) : StateScreenModel<EditorUiState>(EditorUiState()) {

    private var session: Session? = null

    init {
        screenModelScope.launch {
            val current = sessions.state.filterIsInstance<SessionState.SignedIn>().first().session
            session = current
            mutableState.update {
                it.copy(taxLabel = current.store?.taxLabel ?: "ITBIS", timeZone = current.store?.timeZone)
            }
            invoices.ensureDraft(kind, current)
            watchTransferAccounts(current.idMarket)
            invoices.observeDraft(kind).collect { draft ->
                mutableState.update { it.copy(draft = draft, message = null) }
            }
        }
    }

    private fun edit(change: (InvoiceDraft) -> InvoiceDraft) {
        screenModelScope.launch { invoices.update(kind, change) }
    }

    private fun InvoiceDraft.toBase(value: Double): Double =
        if (currency.isBase) round2(value) else toBaseCurrency(value, exchangeRate)

    // Cliente ---------------------------------------------------------------

    fun removeClient() = edit { it.copy(client = null) }

    // Líneas -----------------------------------------------------------------

    fun changeQuantity(key: String, delta: Double) = edit { draft ->
        draft.copy(
            lines = draft.lines.map { line ->
                if (line.key != key || line.unique) line else line.copy(quantity = (line.quantity + delta).coerceAtLeast(1.0))
            },
        )
    }

    fun setQuantity(key: String, quantity: Double) = edit { draft ->
        draft.copy(
            lines = draft.lines.map { line ->
                if (line.key != key || line.unique || quantity <= 0.0) line else line.copy(quantity = quantity)
            },
        )
    }

    /** Precio tecleado en la moneda del documento. */
    fun setUnitPrice(key: String, price: Double) = edit { draft ->
        draft.copy(lines = draft.lines.map { if (it.key == key) it.copy(unitPrice = draft.toBase(price)) else it })
    }

    /** Descuento de la línea, en la moneda del documento. Nunca mayor que la línea. */
    fun setDiscount(key: String, discount: Double) = edit { draft ->
        draft.copy(
            lines = draft.lines.map { line ->
                if (line.key != key) {
                    line
                } else {
                    val max = round2(line.quantity * line.unitPrice)
                    line.copy(discount = draft.toBase(discount).coerceIn(0.0, max))
                }
            },
        )
    }

    fun removeLine(key: String) {
        closeSheet()
        edit { draft -> draft.copy(lines = draft.lines.filterNot { it.key == key }) }
    }

    // Impuesto y moneda --------------------------------------------------------

    fun setTaxType(type: TaxType) = edit { it.copy(taxType = type) }

    /** Tasa en porcentaje (18 → 0.18). Fuera de 0–100 % se ignora. */
    fun setTaxPercent(percent: Double) {
        val rate = round2(percent) / 100.0
        if (isValidTaxRate(rate)) edit { it.copy(taxRate = rate) }
    }

    fun setCurrency(currency: Currency) = edit { draft ->
        when {
            currency == draft.currency -> draft
            // Volver a la base: la tasa deja de existir.
            currency.isBase -> draft.copy(currency = currency, exchangeRate = 1.0)
            // A otra moneda: hay que teclear la tasa. 0 = "falta", y no deja guardar.
            else -> draft.copy(currency = currency, exchangeRate = if (draft.currency.isBase) 0.0 else draft.exchangeRate)
        }
    }

    fun setExchangeRate(rate: Double) = edit { it.copy(exchangeRate = rate.coerceAtLeast(0.0)) }

    // Cobro -----------------------------------------------------------------

    fun setCash(value: Double) = edit { it.copy(payment = it.payment.copy(cash = it.toBase(value))) }

    fun setTransfer(value: Double) = edit { it.copy(payment = it.payment.copy(transfer = it.toBase(value))) }

    fun setCard(value: Double) = edit { it.copy(payment = it.payment.copy(card = it.toBase(value))) }

    /** "Cobrar todo": lo que falta, en efectivo. */
    fun payRemainingInCash() = edit { draft ->
        val others = round2(draft.payment.transfer + draft.payment.card)
        draft.copy(payment = draft.payment.copy(cash = round2(draft.totals.total - others).coerceAtLeast(0.0)))
    }

    fun clearPayment() = edit { it.copy(payment = DraftPayment()) }

    fun setDueInDays(days: Int?) = edit { it.copy(dueInDays = days) }

    fun openAccounts() {
        mutableState.update { it.copy(sheet = EditorSheet.Account) }
        if (state.value.accounts.items.isEmpty()) loadAccounts()
    }

    fun loadAccounts() {
        val current = session ?: return
        mutableState.update { it.copy(accounts = it.accounts.copy(loading = true, error = null)) }
        screenModelScope.launch {
            try {
                val items = catalog.accounts(current.idMarket).map { DraftAccount(it.id, it.name, it.bankName, it.type) }
                mutableState.update { it.copy(accounts = AccountsState(items = items)) }
            } catch (e: ApiException) {
                val message = if (e.isConnectivity) "Sin conexión: no se pueden cargar las cuentas." else e.message
                mutableState.update { it.copy(accounts = it.accounts.copy(loading = false, error = message)) }
            }
        }
    }

    fun selectAccount(account: DraftAccount?) {
        closeSheet()
        edit { it.copy(account = account) }
    }

    // Dónde pagar ----------------------------------------------------------------

    /** Lee de Room (offline first) y, en segundo plano, trae las cuentas por si cambiaron en el POS. */
    private fun watchTransferAccounts(idMarket: Int) {
        screenModelScope.launch {
            banks.observeAccounts(idMarket).collect { rows ->
                val items = rows.filter { it.canReceiveTransfers }
                    .map { DraftAccount(it.id, it.name, it.bankName, it.type, it.accountNumber) }
                mutableState.update { it.copy(transferAccounts = items) }
            }
        }
        screenModelScope.launch {
            try {
                banks.syncAccounts(idMarket)
            } catch (_: ApiException) {
                // Sin red: se queda lo guardado.
            }
        }
    }

    fun togglePayTo(account: DraftAccount) = edit { it.copy(payTo = it.payTo.toggle(account)) }

    fun setPaymentNote(note: String) = edit { it.copy(payTo = it.payTo.copy(note = note.take(PayTo.MAX_NOTE))) }

    // Comprobante fiscal ------------------------------------------------------

    fun setWithNcf(enabled: Boolean) = edit { draft ->
        // Al activarlo se precarga el RNC con la cédula del cliente, como el POS.
        val rnc = if (enabled && draft.rnc.isBlank()) draft.client?.identify.orEmpty() else draft.rnc
        draft.copy(withNcf = enabled, rnc = rnc)
    }

    fun setNcfType(type: NcfType) = edit { it.copy(ncfType = type) }

    fun setRnc(rnc: String) = edit { it.copy(rnc = rnc.filter { c -> c.isDigit() }.take(MAX_RNC)) }

    // Hojas y salida --------------------------------------------------------

    fun openLine(key: String) = mutableState.update { it.copy(sheet = EditorSheet.Line(key)) }

    fun openTax() = mutableState.update { it.copy(sheet = EditorSheet.Tax) }

    fun closeSheet() = mutableState.update { it.copy(sheet = null) }

    /** *Atrás*: si hay algo escrito, se pregunta; el borrador se guarda igual. */
    fun onBack(): Boolean {
        val draft = state.value.draft
        if (draft != null && draft.lines.isNotEmpty()) {
            mutableState.update { it.copy(sheet = EditorSheet.Exit) }
            return false
        }
        return true
    }

    fun discardAndExit() {
        screenModelScope.launch {
            invoices.discard(kind)
            mutableState.update { it.copy(sheet = null, finished = true) }
        }
    }

    fun keepAndExit() = mutableState.update { it.copy(sheet = null, finished = true) }

    fun save() {
        val draft = state.value.draft ?: return
        val current = session ?: return
        if (state.value.saving) return
        problemOf(draft)?.let { problem ->
            mutableState.update { it.copy(message = problem) }
            return
        }
        mutableState.update { it.copy(saving = true, message = null) }
        screenModelScope.launch {
            val id = invoices.enqueue(kind, current.idMarket)
            if (id == null) {
                mutableState.update { it.copy(saving = false, message = "No se pudo guardar. Revisa el documento.") }
                return@launch
            }
            // El envío sigue aunque se cierre el editor; sin red, espera en la cola.
            sender.trigger()
            mutableState.update { it.copy(saving = false, savedLocalId = id) }
        }
    }

    private fun problemOf(draft: InvoiceDraft): String? = when {
        draft.lines.isEmpty() -> "Agrega al menos un producto."
        draft.totals.total <= 0.0 -> "El total tiene que ser mayor que cero."
        !draft.currency.isBase && draft.exchangeRate <= 0.0 -> "Escribe la tasa de cambio."
        draft.overpaidWithoutCash -> "Lo cobrado por transferencia o tarjeta pasa del total. Corrígelo: eso no se devuelve."
        !draft.isQuote && draft.withNcf && draft.ncfType == NcfType.B01 && draft.rnc.isBlank() ->
            "El crédito fiscal (B01) necesita el RNC o la cédula del cliente."
        else -> null
    }

    private companion object {
        // RNC: 9 dígitos; cédula: 11.
        const val MAX_RNC = 11
    }
}
