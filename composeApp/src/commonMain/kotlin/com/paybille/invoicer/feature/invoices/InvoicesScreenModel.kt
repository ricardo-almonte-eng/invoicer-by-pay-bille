package com.paybille.invoicer.feature.invoices

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.domain.SessionState
import com.paybille.invoicer.feature.detail.data.ReceivablesRepository
import com.paybille.invoicer.feature.detail.data.local.ReceivableEntity
import com.paybille.invoicer.feature.invoice.data.InvoiceRepository
import com.paybille.invoicer.feature.invoice.data.InvoiceSender
import com.paybille.invoicer.feature.invoice.domain.PendingDocument
import com.paybille.invoicer.feature.sales.data.SalesRepository
import com.paybille.invoicer.feature.sales.domain.SaleSummary
import com.paybille.invoicer.feature.sales.domain.SalesFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Pestañas de Facturas, en orden. */
val INVOICE_TABS = listOf(SalesFilter.All, SalesFilter.Sales, SalesFilter.Quotes)

data class SalesTabState(
    /** Descargando la primera página. */
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    /** Ya se intentó al menos una vez (con o sin éxito). */
    val attempted: Boolean = false,
    val lastSuccessAt: Long? = null,
    val nextPage: Int = 2,
    val hasMore: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
)

data class InvoicesUiState(
    val timeZone: String = DEFAULT_TIME_ZONE,
    /** Hoy en la zona del negocio (`YYYY-MM-DD`), para "vence en N días". */
    val todayIso: String = "",
    val tabs: Map<SalesFilter, SalesTabState> = INVOICE_TABS.associateWith { SalesTabState() },
) {
    fun tab(filter: SalesFilter): SalesTabState = tabs.getValue(filter)
}

/**
 * Facturas: las listas de ventas. Offline first: cada pestaña pinta lo que hay en Room y
 * pide a la API la primera página al mostrarse; al llegar al final, la siguiente.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class InvoicesScreenModel(
    private val sessions: SessionRepository,
    private val sales: SalesRepository,
    private val invoices: InvoiceRepository,
    private val sender: InvoiceSender,
    private val receivableRepository: ReceivablesRepository,
) : StateScreenModel<InvoicesUiState>(InvoicesUiState()) {

    private val session = sessions.state.filterIsInstance<SessionState.SignedIn>().map { it.session }

    private val idMarket = session.map { it.idMarket }.distinctUntilChanged()

    /** Filas de cada pestaña, leídas de Room. */
    val items: Map<SalesFilter, StateFlow<List<SaleSummary>>> = INVOICE_TABS.associateWith { filter ->
        idMarket
            .flatMapLatest { sales.observe(it, filter) }
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    /** Documentos guardados en el teléfono que todavía no llegaron al servidor. */
    val pending: StateFlow<List<PendingDocument>> = idMarket
        .flatMapLatest { invoices.observePending(it) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cuentas por cobrar por venta: el vencimiento de cada fila. */
    val receivables: StateFlow<Map<Int, ReceivableEntity>> = idMarket
        .flatMapLatest { receivableRepository.observeBySale(it) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun retryPending(localId: String) {
        screenModelScope.launch {
            invoices.retry(localId)
            sender.trigger()
        }
    }

    init {
        // Si la tienda no se descargó al entrar (sin red en ese momento), se reintenta aquí,
        // en silencio: el aviso de sincronización vive en Mi perfil.
        screenModelScope.launch {
            if (session.first().profileSyncedAt == null) sessions.refreshProfile()
        }
        screenModelScope.launch {
            session.collect { s ->
                val zone = s.store?.timeZone ?: DEFAULT_TIME_ZONE
                val tz = runCatching { TimeZone.of(zone) }.getOrElse { TimeZone.of(DEFAULT_TIME_ZONE) }
                mutableState.update {
                    it.copy(timeZone = zone, todayIso = Clock.System.now().toLocalDateTime(tz).date.toString())
                }
            }
        }
    }

    /** La pestaña pasó a verse: se refresca si nunca se cargó o si lo último tiene más de un minuto. */
    fun onTabVisible(filter: SalesFilter) {
        val tab = state.value.tab(filter)
        if (tab.refreshing) return
        val last = tab.lastSuccessAt
        val stale = last == null || Clock.System.now().toEpochMilliseconds() - last > STALE_AFTER_MS
        if (!tab.attempted || (stale && !tab.offline && tab.error == null)) refresh(filter)
    }

    fun refresh(filter: SalesFilter) {
        if (state.value.tab(filter).refreshing) return
        updateTab(filter) { it.copy(refreshing = true, error = null, offline = false) }
        screenModelScope.launch {
            try {
                // Si hay red para refrescar, también la hay para vaciar la cola y poner al día
                // los vencimientos (y con ellos los avisos del teléfono).
                sender.trigger()
                if (filter == SalesFilter.All) {
                    screenModelScope.launch { runCatching { receivableRepository.refresh(idMarket.first()) } }
                }
                val hasMore = sales.loadPage(idMarket.first(), filter, page = 1)
                updateTab(filter) {
                    it.copy(
                        hasMore = hasMore,
                        nextPage = 2,
                        lastSuccessAt = Clock.System.now().toEpochMilliseconds(),
                    )
                }
            } catch (e: ApiException) {
                updateTab(filter) { it.withError(e) }
            } finally {
                updateTab(filter) { it.copy(refreshing = false, attempted = true) }
            }
        }
    }

    /** Llegó al final de la lista. */
    fun loadMore(filter: SalesFilter) {
        val tab = state.value.tab(filter)
        if (!tab.hasMore || tab.loadingMore || tab.refreshing || tab.error != null || tab.offline) return
        updateTab(filter) { it.copy(loadingMore = true) }
        screenModelScope.launch {
            try {
                val hasMore = sales.loadPage(idMarket.first(), filter, page = tab.nextPage)
                updateTab(filter) { it.copy(hasMore = hasMore, nextPage = tab.nextPage + 1) }
            } catch (e: ApiException) {
                updateTab(filter) { it.withError(e) }
            } finally {
                updateTab(filter) { it.copy(loadingMore = false) }
            }
        }
    }

    private fun SalesTabState.withError(e: ApiException) =
        if (e.isConnectivity) copy(offline = true) else copy(error = e.message ?: "No se pudieron cargar las ventas.")

    private fun updateTab(filter: SalesFilter, change: (SalesTabState) -> SalesTabState) =
        mutableState.update { it.copy(tabs = it.tabs + (filter to change(it.tab(filter)))) }

    private companion object {
        const val STALE_AFTER_MS = 60_000L
    }
}
