package com.paybille.invoicer.feature.reports.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.database.Cached
import com.paybille.invoicer.core.designsystem.theme.PbSymbol
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.core.format.DateRange
import com.paybille.invoicer.core.format.Period
import com.paybille.invoicer.core.format.todayIn
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.ui.SyncState
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.data.idMarketFlow
import com.paybille.invoicer.feature.auth.data.signedIn
import com.paybille.invoicer.feature.clients.data.ClientsRepository
import com.paybille.invoicer.feature.clients.data.remote.PartyBalanceDto
import com.paybille.invoicer.feature.reports.data.InventoryHistoryDto
import com.paybille.invoicer.feature.reports.data.ReportsRepository
import com.paybille.invoicer.feature.reports.data.SalesReportDto
import com.paybille.invoicer.feature.reports.data.SoldProductsReportDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Los cuatro reportes del POS que entran en la app (decisión del usuario, 2026-09-18). */
enum class ReportKind(val title: String, val description: String, val icon: PbSymbol, val hasRange: Boolean) {
    Sales("Ventas por fecha", "Lo vendido, cobrado e impuestos de un periodo", PbSymbols.ReceiptLong, true),
    SoldProducts("Productos vendidos", "Cantidades, costo y ganancia por producto", PbSymbols.Inventory2, true),
    Balances("Saldos pendientes", "Quién te debe y cuánto", PbSymbols.RequestQuote, false),
    InventoryHistory("Histórico del inventario", "Entradas y salidas de existencia", PbSymbols.History, true),
}

/** Los periodos del Resumen y, además, el mes anterior (el cierre de mes es lo más pedido). */
enum class ReportPeriod(val label: String) {
    Today(Period.Today.label),
    Yesterday(Period.Yesterday.label),
    Week(Period.Week.label),
    Month(Period.Month.label),
    PreviousMonth("Mes anterior"),
    ;

    fun range(today: LocalDate): DateRange = when (this) {
        Today -> Period.Today.range(today)
        Yesterday -> Period.Yesterday.range(today)
        Week -> Period.Week.range(today)
        Month -> Period.Month.range(today)
        PreviousMonth -> {
            val first = LocalDate(today.year, today.month, 1)
            val last = first.minus(DatePeriod(days = 1))
            DateRange(LocalDate(last.year, last.month, 1).toString(), last.toString())
        }
    }
}

data class ReportUiState(
    val period: ReportPeriod = ReportPeriod.Today,
    val today: LocalDate? = null,
    val timeZone: String = DEFAULT_TIME_ZONE,
    val sync: Map<String, SyncState> = emptyMap(),
) {
    val range: DateRange? get() = today?.let { period.range(it) }

    /** Clave del estado de carga: el rango, o "all" en lo que no tiene rango. */
    fun syncKey(kind: ReportKind): String = if (kind.hasRange) range?.key ?: "" else "all"

    fun syncOf(kind: ReportKind): SyncState = sync[syncKey(kind)] ?: SyncState()
}

/** Un reporte: pinta lo último guardado de ese rango y lo pone al día al abrirse o al cambiarlo. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportScreenModel(
    private val kind: ReportKind,
    private val sessions: SessionRepository,
    private val reports: ReportsRepository,
    private val clients: ClientsRepository,
) : StateScreenModel<ReportUiState>(ReportUiState()) {

    private val idMarket = sessions.idMarketFlow()

    private fun <T> observe(source: (Int, DateRange) -> Flow<Cached<T>?>): StateFlow<Cached<T>?> =
        combine(idMarket, state.map { it.range }.distinctUntilChanged()) { market, range -> market to range }
            .flatMapLatest { (market, range) -> if (range == null) flowOf(null) else source(market, range) }
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sales: StateFlow<Cached<SalesReportDto>?> = observe(reports::observeSales)
    val soldProducts: StateFlow<Cached<SoldProductsReportDto>?> = observe(reports::observeSoldProducts)
    val inventory: StateFlow<Cached<InventoryHistoryDto>?> = observe(reports::observeInventoryHistory)

    /** Saldos pendientes: la misma copia que usa Clientes. */
    val balances: StateFlow<Cached<List<PartyBalanceDto>>?> = idMarket
        .flatMapLatest { clients.observeBalances(it) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        screenModelScope.launch {
            val zone = sessions.signedIn().first().store?.timeZone ?: DEFAULT_TIME_ZONE
            mutableState.update { it.copy(timeZone = zone, today = todayIn(zone)) }
            refresh()
        }
    }

    fun selectPeriod(period: ReportPeriod) {
        mutableState.update { it.copy(period = period, today = todayIn(it.timeZone)) }
        if (state.value.syncOf(kind).isStale(STALE_AFTER_MS)) refresh()
    }

    fun refresh() {
        val current = state.value
        val range = current.range ?: return
        val key = current.syncKey(kind)
        if (current.sync[key]?.syncing == true) return
        updateSync(key) { it.started() }
        screenModelScope.launch {
            try {
                val market = idMarket.first()
                when (kind) {
                    ReportKind.Sales -> reports.refreshSales(market, range)
                    ReportKind.SoldProducts -> reports.refreshSoldProducts(market, range)
                    ReportKind.Balances -> clients.refreshBalances(market)
                    ReportKind.InventoryHistory -> reports.refreshInventoryHistory(market, range)
                }
                updateSync(key) { it.succeeded() }
            } catch (e: ApiException) {
                updateSync(key) { it.failed(e, "No se pudo cargar el reporte.") }
            }
        }
    }

    private fun updateSync(key: String, change: (SyncState) -> SyncState) =
        mutableState.update { it.copy(sync = it.sync + (key to change(it.sync[key] ?: SyncState()))) }

    private companion object {
        const val STALE_AFTER_MS = 60_000L
    }
}
