package com.paybille.invoicer.feature.detail.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.billing.round2
import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.platform.DocumentPlatform
import com.paybille.invoicer.core.platform.SaveResult
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.domain.Session
import com.paybille.invoicer.feature.detail.data.InvoicePdfStore
import com.paybille.invoicer.feature.detail.data.SaleDetailRepository
import com.paybille.invoicer.feature.detail.domain.PaymentMethod
import com.paybille.invoicer.feature.detail.domain.SaleDetail
import com.paybille.invoicer.feature.document.data.InvoiceDocumentRepository
import com.paybille.invoicer.feature.invoice.data.InvoiceRepository
import com.paybille.invoicer.feature.invoice.data.InvoiceSender
import com.paybille.invoicer.feature.invoice.data.remote.CatalogRemoteDataSource
import com.paybille.invoicer.feature.invoice.domain.DraftAccount
import com.paybille.invoicer.feature.invoice.domain.PendingDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Qué venta muestra el detalle: una del servidor, o una recién guardada que aún se envía. */
data class DetailTarget(val saleId: Int?, val localId: String?)

/** La factura que genera el teléfono (HTML), no un PDF del servidor. */
sealed interface DocumentState {
    data object Loading : DocumentState
    data class Ready(val html: String) : DocumentState

    /** Sin red y todavía sin datos guardados de esta factura. */
    data object Offline : DocumentState
    data class Failed(val message: String) : DocumentState
}

data class PaymentForm(
    val amount: Double = 0.0,
    val method: PaymentMethod = PaymentMethod.Cash,
    val account: DraftAccount? = null,
    val reference: String = "",
    val saving: Boolean = false,
    val error: String? = null,
)

data class DetailUiState(
    val saleId: Int? = null,
    /** Mientras el documento está en la cola de envíos. */
    val pending: PendingDocument? = null,
    val detail: SaleDetail? = null,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val document: DocumentState = DocumentState.Loading,
    /** Generando el PDF para compartir o descargar. */
    val exporting: Boolean = false,
    val todayIso: String = "",
    val timeZone: String = DEFAULT_TIME_ZONE,
    val paymentOpen: Boolean = false,
    val payment: PaymentForm = PaymentForm(),
    val accounts: List<DraftAccount> = emptyList(),
    val accountsError: String? = null,
    /** Aviso corto tras una acción ("Guardado en Descargas"). */
    val notice: String? = null,
)

/**
 * Detalle de una factura o cotización: la factura (HTML que genera el teléfono con los datos de
 * `ventas/factura/{id}/data` y la configuración de la tienda) como protagonista, su estado de
 * cobro, vencimiento y abonos. Offline first: pinta lo guardado y refresca.
 */
@OptIn(ExperimentalTime::class)
class SaleDetailScreenModel(
    target: DetailTarget,
    private val sessions: SessionRepository,
    private val details: SaleDetailRepository,
    private val pdfs: InvoicePdfStore,
    private val documents: InvoiceDocumentRepository,
    private val platform: DocumentPlatform,
    private val invoices: InvoiceRepository,
    private val sender: InvoiceSender,
    private val catalog: CatalogRemoteDataSource,
) : StateScreenModel<DetailUiState>(DetailUiState()) {

    private var session: Session? = null
    private var exportJob: Job? = null

    init {
        screenModelScope.launch {
            val current = sessions.currentSession() ?: return@launch
            session = current
            val zone = current.store?.timeZone ?: DEFAULT_TIME_ZONE
            mutableState.update { it.copy(timeZone = zone, todayIso = todayIso(zone)) }

            val saleId = target.saleId ?: target.localId?.let { waitForSent(it, current.idMarket) } ?: return@launch
            start(saleId)
        }
    }

    /** Documento recién guardado: se sigue su envío y, cuando llega, se carga como cualquier otro. */
    private suspend fun waitForSent(localId: String, idMarket: Int): Int {
        val watcher = screenModelScope.launch {
            invoices.observePending(idMarket).collect { rows ->
                mutableState.update { it.copy(pending = rows.firstOrNull { row -> row.localId == localId }) }
            }
        }
        val saleId = sender.sent.mapNotNull { it[localId] }.first()
        watcher.cancel()
        mutableState.update { it.copy(pending = null) }
        return saleId
    }

    private fun start(saleId: Int) {
        mutableState.update { it.copy(saleId = saleId) }
        screenModelScope.launch {
            details.observe(saleId).collect { detail -> mutableState.update { it.copy(detail = detail) } }
        }
        observeDocument(saleId)
        refresh()
    }

    /** Lo guardado de la factura + la configuración de la tienda → HTML. Se rehace solo si cambian. */
    private fun observeDocument(saleId: Int) {
        val current = session ?: return
        screenModelScope.launch {
            combine(documents.observe(current.idMarket, saleId), documents.observeConfig(current.idMarket)) { data, config -> data to config }
                .collectLatest { (data, config) ->
                    if (data == null) return@collectLatest
                    val html = documents.html(current.idMarket, data.value, config, state.value.timeZone, state.value.todayIso)
                    mutableState.update { it.copy(document = DocumentState.Ready(html)) }
                }
        }
    }

    fun refresh() {
        val saleId = state.value.saleId ?: return
        val current = session ?: return
        if (state.value.refreshing) return
        mutableState.update { it.copy(refreshing = true, error = null, offline = false) }
        screenModelScope.launch {
            try {
                details.refresh(saleId, current.idMarket)
            } catch (e: ApiException) {
                mutableState.update { if (e.isConnectivity) it.copy(offline = true) else it.copy(error = e.message) }
            } finally {
                mutableState.update { it.copy(refreshing = false) }
            }
        }
        refreshDocument()
    }

    /** Pide los datos de la factura. Con copia guardada, un fallo no la tapa. */
    fun refreshDocument() {
        val saleId = state.value.saleId ?: return
        val current = session ?: return
        screenModelScope.launch {
            if (state.value.document !is DocumentState.Ready) mutableState.update { it.copy(document = DocumentState.Loading) }
            try {
                documents.refresh(current.idMarket, saleId)
            } catch (e: ApiException) {
                mutableState.update {
                    if (it.document is DocumentState.Ready) it
                    else it.copy(document = if (e.isConnectivity) DocumentState.Offline else DocumentState.Failed(e.message ?: ""))
                }
            }
        }
    }

    fun share() = export { path ->
        platform.share(path, PDF_MIME, documentTitle())
        null
    }

    fun download() = export { path ->
        when (val result = platform.saveCopy(path, "${documentTitle().replace(" ", "-")}.pdf", PDF_MIME)) {
            is SaveResult.Saved -> "PDF guardado en ${result.where}."
            SaveResult.PickerShown -> null
            SaveResult.UseShare -> {
                // Android 9 o menos: se guarda desde la hoja de compartir.
                platform.share(path, PDF_MIME, documentTitle())
                "Elige \"Guardar\" o \"Archivos\" en el menú para descargarlo."
            }
            is SaveResult.Failed -> result.message
        }
    }

    /** Genera el PDF en el teléfono, con lo que se ve ahora, y se lo pasa a `then`. */
    private fun export(then: suspend (path: String) -> String?) {
        val saleId = state.value.saleId ?: return
        val ready = state.value.document as? DocumentState.Ready ?: return
        if (exportJob?.isActive == true) return
        mutableState.update { it.copy(exporting = true) }
        exportJob = screenModelScope.launch {
            val notice = try {
                then(pdfs.write(saleId, ready.html))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "No se pudo generar el PDF: ${e.message ?: "inténtalo otra vez"}."
            }
            mutableState.update { it.copy(exporting = false, notice = notice) }
        }
    }

    fun dismissNotice() = mutableState.update { it.copy(notice = null) }

    // Envío pendiente --------------------------------------------------------------

    fun retrySend() {
        val localId = state.value.pending?.localId ?: return
        screenModelScope.launch {
            invoices.retry(localId)
            sender.trigger()
        }
    }

    // Abonos ------------------------------------------------------------------------

    fun openPayment() {
        val balance = state.value.detail?.receivable?.balance ?: return
        mutableState.update { it.copy(paymentOpen = true, payment = PaymentForm(amount = balance)) }
        if (state.value.accounts.isEmpty()) loadAccounts()
    }

    fun closePayment() = mutableState.update { it.copy(paymentOpen = false) }

    fun setPaymentAmount(value: Double) = updateForm { it.copy(amount = value, error = null) }
    fun setPaymentMethod(method: PaymentMethod) = updateForm { it.copy(method = method) }
    fun setPaymentAccount(account: DraftAccount?) = updateForm { it.copy(account = account) }
    fun setPaymentReference(value: String) = updateForm { it.copy(reference = value.take(MAX_REFERENCE)) }

    fun loadAccounts() {
        val current = session ?: return
        screenModelScope.launch {
            try {
                val items = catalog.accounts(current.idMarket).map { DraftAccount(it.id, it.name, it.bankName, it.type) }
                mutableState.update { it.copy(accounts = items, accountsError = null) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(accountsError = if (e.isConnectivity) "Sin conexión." else e.message) }
            }
        }
    }

    fun submitPayment() {
        val detail = state.value.detail ?: return
        val balance = detail.receivable?.balance ?: return
        val form = state.value.payment
        if (form.saving) return
        val amount = round2(form.amount)
        val problem = when {
            amount <= 0.0 -> "Escribe cuánto te pagaron."
            // Mismo límite que el servidor (`applyPayment`): no se cobra más que el saldo.
            amount > round2(balance + 0.01) -> "Es más que lo que debe. El saldo es de ${com.paybille.invoicer.core.format.formatMoney(balance)}."
            else -> null
        }
        if (problem != null) {
            updateForm { it.copy(error = problem) }
            return
        }
        updateForm { it.copy(saving = true, error = null) }
        screenModelScope.launch {
            try {
                details.addPayment(
                    detail = detail,
                    amount = amount,
                    method = form.method,
                    paymentDateIso = Clock.System.now().toString(),
                    idCuenta = form.account?.id,
                    reference = form.reference,
                )
                mutableState.update { it.copy(paymentOpen = false, payment = PaymentForm(), notice = "Pago registrado.") }
                // La factura enseña el saldo nuevo en cuanto llegan los datos.
                refreshDocument()
            } catch (e: ApiException) {
                val message = if (e.isConnectivity) "Sin conexión: el pago no se registró. Inténtalo con red." else e.message
                updateForm { it.copy(saving = false, error = message) }
            }
        }
    }

    private fun updateForm(change: (PaymentForm) -> PaymentForm) =
        mutableState.update { it.copy(payment = change(it.payment)) }

    private fun documentTitle(): String {
        val detail = state.value.detail
        val kind = if (detail?.status == "Cotizacion") "Cotizacion" else "Factura"
        return "$kind ${detail?.number ?: state.value.saleId ?: ""}".trim()
    }

    private fun todayIso(zone: String): String {
        val tz = runCatching { TimeZone.of(zone) }.getOrElse { TimeZone.of(DEFAULT_TIME_ZONE) }
        return Clock.System.now().toLocalDateTime(tz).date.toString()
    }

    private companion object {
        const val PDF_MIME = "application/pdf"
        const val MAX_REFERENCE = 120
    }
}
