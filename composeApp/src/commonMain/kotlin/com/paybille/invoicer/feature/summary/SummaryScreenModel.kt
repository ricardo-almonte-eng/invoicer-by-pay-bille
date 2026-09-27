package com.paybille.invoicer.feature.summary

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.database.Cached
import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.core.format.DateRange
import com.paybille.invoicer.core.format.Period
import com.paybille.invoicer.core.format.todayIn
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.ui.SyncState
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.data.signedIn
import com.paybille.invoicer.feature.detail.data.ReceivablesRepository
import com.paybille.invoicer.feature.summary.data.DashboardSummaryDto
import com.paybille.invoicer.feature.summary.data.SummaryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.datetime.LocalDate

data class SummaryUiState(
    val period: Period = Period.Today,
    val today: LocalDate? = null,
    val timeZone: String = DEFAULT_TIME_ZONE,
    val firstName: String = "",
    /** Estado de cada rango pedido (la clave es [DateRange.key]). */
    val sync: Map<String, SyncState> = emptyMap(),
) {
    val range: DateRange? get() = today?.let { period.range(it) }

    fun syncOf(range: DateRange?): SyncState = range?.let { sync[it.key] } ?: SyncState()
}

/**
 * Resumen: el dashboard del POS. Offline first: pinta lo último que se guardó de ese periodo y
 * lo pone al día al mostrarse o al cambiar de periodo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SummaryScreenModel(
    sessions: SessionRepository,
    private val repository: SummaryRepository,
    receivables: ReceivablesRepository,
) : StateScreenModel<SummaryUiState>(SummaryUiState()) {

    private val session = sessions.signedIn()
    private val idMarket = session.map { it.idMarket }.distinctUntilChanged()

    val summary: StateFlow<Cached<DashboardSummaryDto>?> =
        combine(idMarket, state.map { it.range }.distinctUntilChanged()) { market, range -> market to range }
            .flatMapLatest { (market, range) ->
                if (range == null) flowOf(null) else repository.observe(market, range)
            }
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "Te deben": lo que ya está en Room de las cuentas por cobrar (sin llamada nueva). */
    val owed: StateFlow<Pair<Double, Int>> = idMarket
        .flatMapLatest { receivables.observeOwed(it) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), 0.0 to 0)

    /** El destino se mostró al menos una vez: antes de eso no se llama a la red. */
    private var visible = false

    init {
        screenModelScope.launch {
            session.collect { s ->
                val zone = s.store?.timeZone ?: DEFAULT_TIME_ZONE
                mutableState.update {
                    it.copy(timeZone = zone, today = todayIn(zone), firstName = s.firstName?.trim().orEmpty())
                }
                // Llegó la sesión (o cambió): si el destino ya está a la vista, se pone al día.
                if (visible) refreshIfStale()
            }
        }
    }

    /** Se mostró el destino: el día puede haber cambiado desde la última vez. */
    fun onVisible() {
        visible = true
        mutableState.update { it.copy(today = todayIn(it.timeZone)) }
        refreshIfStale()
    }

    fun selectPeriod(period: Period) {
        mutableState.update { it.copy(period = period) }
        refreshIfStale()
    }

    private fun refreshIfStale() {
        if (state.value.syncOf(state.value.range).isStale(STALE_AFTER_MS)) refresh()
    }

    fun refresh() {
        val range = state.value.range ?: return
        if (state.value.syncOf(range).syncing) return
        updateSync(range) { it.started() }
        screenModelScope.launch {
            try {
                repository.refresh(idMarket.first(), range)
                updateSync(range) { it.succeeded() }
            } catch (e: ApiException) {
                updateSync(range) { it.failed(e, "No se pudo cargar el resumen.") }
            }
        }
    }

    private fun updateSync(range: DateRange, change: (SyncState) -> SyncState) =
        mutableState.update { it.copy(sync = it.sync + (range.key to change(it.syncOf(range)))) }

    private companion object {
        const val STALE_AFTER_MS = 60_000L
    }
}
