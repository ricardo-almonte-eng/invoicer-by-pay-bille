package com.paybille.invoicer.feature.detail.presentation

import androidx.compose.ui.graphics.ImageBitmap
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
import com.paybille.invoicer.feature.invoice.data.InvoiceRepository
import com.paybille.invoicer.feature.invoice.data.InvoiceSender
import com.paybille.invoicer.feature.invoice.data.remote.CatalogRemoteDataSource
import com.paybille.invoicer.feature.invoice.domain.DraftAccount
import com.paybille.invoicer.feature.invoice.domain.PendingDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

sealed interface PdfState {
    data object Idle : PdfState
    data object Loading : PdfState
    data class Ready(val path: String, val pages: List<ImageBitmap>) : PdfState

    /** Sin red y sin copia en el teléfono. */
    data object Offline : PdfState
    data class Failed(val message: String) : PdfState
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
    val pdf: PdfState = PdfState.Idle,
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
 * Detalle de una factura o cotización: la "factura grande" en PDF como protagonista, su
 * estado de cobro, vencimiento y abonos. Offline first: pinta lo guardado y refresca.
 */
@OptIn(ExperimentalTime::class)
class SaleDetailScreenModel(
    target: DetailTarget,
    private val sessions: SessionRepository,
    private val details: SaleDetailRepository,
    private val pdfs: InvoicePdfStore,
    private val platform: DocumentPlatform,
    private val invoices: InvoiceRepository,
    private val sender: InvoiceSender,
    private val catalog: CatalogRemoteDataSource,
) : StateScreenModel<DetailUiState>(DetailUiState()) {

    private var session: Session? = null
    private var previewWidthPx: Int = 0
    private var pdfJob: Job? = null

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
        refresh()
        if (previewWidthPx > 0) loadPdf(refresh = false)
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
    }

    /** La vista previa ya sabe su ancho: se descarga (si hace falta) y se dibuja. */
    fun onPreviewWidth(widthPx: Int) {
        if (widthPx <= 0 || widthPx == previewWidthPx) return
        previewWidthPx = widthPx
        if (state.value.saleId != null) loadPdf(refresh = false)
    }

    fun loadPdf(refresh: Boolean = true) {
        val saleId = state.value.saleId ?: return
        pdfJob?.cancel()
        mutableState.update { it.copy(pdf = PdfState.Loading) }
        pdfJob = screenModelScope.launch {
            try {
                val path = pdfs.ensure(saleId, refresh = refresh)
                val pages = platform.renderPdf(path, previewWidthPx.coerceAtLeast(MIN_RENDER_PX))
                mutableState.update {
                    it.copy(pdf = if (pages.isEmpty()) PdfState.Failed("El PDF no se pudo abrir.") else PdfState.Ready(path, pages))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                mutableState.update { it.copy(pdf = if (e.isConnectivity) PdfState.Offline else PdfState.Failed(e.message ?: "")) }
            } catch (e: Exception) {
                mutableState.update { it.copy(pdf = PdfState.Failed("El PDF no se pudo abrir.")) }
            }
        }
    }

    fun share() {
        val ready = state.value.pdf as? PdfState.Ready ?: return
        runCatching { platform.share(ready.path, PDF_MIME, documentTitle()) }
            .onFailure { e -> mutableState.update { it.copy(notice = "No se pudo compartir: ${e.message}") } }
    }

    fun download() {
        val ready = state.value.pdf as? PdfState.Ready ?: return
        screenModelScope.launch {
            val notice = when (val result = platform.saveCopy(ready.path, "${documentTitle().replace(" ", "-")}.pdf", PDF_MIME)) {
                is SaveResult.Saved -> "PDF guardado en ${result.where}."
                SaveResult.PickerShown -> null
                SaveResult.UseShare -> {
                    // Android 9 o menos: se guarda desde la hoja de compartir.
                    platform.share(ready.path, PDF_MIME, documentTitle())
                    "Elige \"Guardar\" o \"Archivos\" en el menú para descargarlo."
                }
                is SaveResult.Failed -> result.message
            }
            mutableState.update { it.copy(notice = notice) }
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
                mutableState.update {
                    it.copy(
                        paymentOpen = false,
                        payment = PaymentForm(),
                        notice = "Pago registrado. El PDF del servidor puede tardar en mostrar el nuevo saldo.",
                    )
                }
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
        const val MIN_RENDER_PX = 600
        const val MAX_REFERENCE = 120
    }
}
