package com.paybille.invoicer.feature.document.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.data.signedIn
import com.paybille.invoicer.feature.auth.domain.Session
import com.paybille.invoicer.feature.document.data.InvoiceDocumentRepository
import com.paybille.invoicer.feature.document.domain.InvoiceClient
import com.paybille.invoicer.feature.document.domain.InvoiceConfig
import com.paybille.invoicer.feature.document.domain.InvoiceData
import com.paybille.invoicer.feature.document.domain.InvoiceItem
import com.paybille.invoicer.feature.document.domain.InvoiceMarket
import com.paybille.invoicer.feature.document.domain.InvoicePayTo
import com.paybille.invoicer.feature.document.domain.InvoicePayment
import com.paybille.invoicer.feature.document.domain.InvoiceReceivable
import com.paybille.invoicer.feature.document.domain.InvoiceSale
import com.paybille.invoicer.feature.document.domain.SignaturePoint
import com.paybille.invoicer.feature.document.domain.SignatureSvg
import com.paybille.invoicer.feature.sales.domain.SaleStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class InvoiceSettingsUiState(
    val loaded: Boolean = false,
    /** Sin red al abrir: se ve lo guardado y no se deja guardar. */
    val offline: Boolean = false,
    val config: InvoiceConfig = InvoiceConfig(),
    val original: InvoiceConfig = InvoiceConfig(),
    /** Trazos de la firma en el lienzo ([SIGNATURE_W] × [SIGNATURE_H]). */
    val strokes: List<List<SignaturePoint>> = emptyList(),
    /** La factura de ejemplo con la configuración de ahora. */
    val previewHtml: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
) {
    val dirty: Boolean get() = config != original
}

/** Lienzo de la firma: el SVG se guarda con este `viewBox`. */
const val SIGNATURE_W = 600f
const val SIGNATURE_H = 200f

/**
 * Diseño de la factura de la tienda (`invoiceconfig`): qué sale, con qué textos y la firma.
 * Se ve al momento en una factura de ejemplo. Guardar necesita red, como la configuración de la
 * tienda.
 */
@OptIn(ExperimentalTime::class)
class InvoiceSettingsScreenModel(
    private val sessions: SessionRepository,
    private val documents: InvoiceDocumentRepository,
) : StateScreenModel<InvoiceSettingsUiState>(InvoiceSettingsUiState()) {

    private var session: Session? = null
    private var previewJob: Job? = null

    init {
        load()
    }

    fun load() {
        screenModelScope.launch {
            val current = sessions.signedIn().first()
            session = current
            // Primero lo guardado en el teléfono; luego lo del servidor.
            documents.observeConfig(current.idMarket).first()?.let { applyLoaded(it, offline = false) }
            try {
                applyLoaded(documents.refreshConfig(current.idMarket), offline = false)
            } catch (e: ApiException) {
                mutableState.update { it.copy(offline = e.isConnectivity, error = if (e.isConnectivity) null else e.message) }
                if (!state.value.loaded) applyLoaded(InvoiceConfig(), offline = e.isConnectivity)
            }
        }
    }

    private fun applyLoaded(config: InvoiceConfig, offline: Boolean) {
        val previous = state.value
        // Lo que el usuario ya cambió no se pisa con lo que llega del servidor.
        if (previous.loaded && previous.dirty) return
        mutableState.update {
            it.copy(
                loaded = true,
                offline = offline,
                config = config,
                original = config,
                strokes = strokesOf(config.signature),
            )
        }
        schedulePreview(immediate = true)
    }

    fun update(change: (InvoiceConfig) -> InvoiceConfig) {
        mutableState.update { it.copy(config = change(it.config), error = null) }
        schedulePreview()
    }

    fun setStrokes(strokes: List<List<SignaturePoint>>) {
        val svg = SignatureSvg.fromStrokes(strokes, SIGNATURE_W, SIGNATURE_H)
        mutableState.update { it.copy(strokes = strokes, config = it.config.copy(signature = svg)) }
        schedulePreview()
    }

    fun clearSignature() = setStrokes(emptyList())

    fun save() {
        val current = session ?: return
        val s = state.value
        if (s.saving || !s.loaded) return
        mutableState.update { it.copy(saving = true, error = null) }
        screenModelScope.launch {
            try {
                val saved = documents.saveConfig(current.idMarket, s.config.normalized())
                mutableState.update { it.copy(saving = false, config = saved, original = saved, done = true) }
            } catch (e: ApiException) {
                val message = if (e.isConnectivity) "Sin conexión: no se guardó. Inténtalo con red." else e.message
                mutableState.update { it.copy(saving = false, error = message) }
            }
        }
    }

    /** La vista previa se rehace un momento después de dejar de teclear. */
    private fun schedulePreview(immediate: Boolean = false) {
        val current = session ?: return
        previewJob?.cancel()
        previewJob = screenModelScope.launch {
            if (!immediate) delay(PREVIEW_DEBOUNCE_MS)
            val zone = current.store?.timeZone ?: DEFAULT_TIME_ZONE
            val today = Clock.System.now().toLocalDateTime(zoneOf(zone)).date
            val html = documents.html(
                idMarket = current.idMarket,
                data = sample(current, today.toString(), today.plus(15, DateTimeUnit.DAY).toString()),
                config = state.value.config,
                timeZone = zone,
                todayIso = today.toString(),
            )
            mutableState.update { it.copy(previewHtml = html) }
        }
    }

    /** Una factura a crédito con un abono: así se ven todas las secciones a la vez. */
    private fun sample(session: Session, todayIso: String, dueIso: String): InvoiceData {
        val store = session.store
        return InvoiceData(
            sale = InvoiceSale(
                id = 0,
                number = "0001",
                status = SaleStatus.PENDING,
                createdAt = "${todayIso}T16:00:00.000Z",
                taxType = store?.taxType ?: "included",
                subtotal = 2118.64,
                tax = 381.36,
                total = 2500.0,
            ),
            items = listOf(
                InvoiceItem(name = "Producto de ejemplo", quantity = 2.0, price = 750.0, total = 1500.0),
                InvoiceItem(name = "Servicio de ejemplo", quantity = 1.0, price = 1000.0, total = 1000.0),
            ),
            client = InvoiceClient(
                name = "Cliente de ejemplo",
                identify = "001-0000000-0",
                phone = "809-555-0100",
                email = "cliente@ejemplo.com",
                address = "Calle Primera 1, Santo Domingo",
            ),
            market = InvoiceMarket(
                id = session.idMarket,
                name = store?.name.orEmpty(),
                address = store?.address,
                phone = store?.phone,
                rnc = store?.rnc,
                logo = store?.image,
                taxLabel = store?.taxLabel,
            ),
            receivable = InvoiceReceivable(
                total = 2500.0,
                paid = 1000.0,
                balance = 1500.0,
                dueDate = dueIso,
                status = "Parcial",
                payments = listOf(InvoicePayment(date = "${todayIso}T16:00:00.000Z", method = "Efectivo", amount = 1000.0)),
            ),
            paymentAccounts = listOf(
                InvoicePayTo(bank = "Tu banco", type = "Cuenta de ahorros", number = "000-000000-0", holderName = store?.name.orEmpty()),
            ),
        )
    }

    private fun strokesOf(svg: String?): List<List<SignaturePoint>> {
        val (w, h) = SignatureSvg.size(svg) ?: return emptyList()
        if (w <= 0f || h <= 0f) return emptyList()
        return SignatureSvg.toStrokes(svg).map { stroke ->
            stroke.map { SignaturePoint(it.x * SIGNATURE_W / w, it.y * SIGNATURE_H / h) }
        }
    }

    private fun zoneOf(zone: String) = runCatching { TimeZone.of(zone) }.getOrElse { TimeZone.of(DEFAULT_TIME_ZONE) }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 300L
    }
}

/** Textos sin espacios de más y vacíos como `null`: así se guarda lo mismo que se ve. */
internal fun InvoiceConfig.normalized() = copy(
    title = title?.trim()?.takeIf { it.isNotEmpty() },
    defaultPaymentNote = defaultPaymentNote?.trim()?.takeIf { it.isNotEmpty() },
    terms = terms?.trim()?.takeIf { it.isNotEmpty() },
    signatureName = signatureName?.trim()?.takeIf { it.isNotEmpty() },
    footerMessage = footerMessage?.trim()?.takeIf { it.isNotEmpty() },
)
