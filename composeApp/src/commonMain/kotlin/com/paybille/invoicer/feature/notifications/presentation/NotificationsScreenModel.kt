package com.paybille.invoicer.feature.notifications.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.ui.SyncState
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.data.signedIn
import com.paybille.invoicer.feature.detail.data.ReceivablesRepository
import com.paybille.invoicer.feature.invoice.data.InvoiceRepository
import com.paybille.invoicer.feature.invoice.data.InvoiceSender
import com.paybille.invoicer.feature.notifications.data.NoticesRepository
import com.paybille.invoicer.feature.notifications.domain.Notice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val sync: SyncState = SyncState(),
    val timeZone: String = DEFAULT_TIME_ZONE,
    val todayIso: String = "",
)

/**
 * La campana de Facturas: facturas vencidas o por vencer y documentos que no llegaron al
 * servidor. Se pinta de Room; al abrir se traen los vencimientos al día si hay red.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsScreenModel(
    private val sessions: SessionRepository,
    private val notices: NoticesRepository,
    private val receivables: ReceivablesRepository,
    private val invoices: InvoiceRepository,
    private val sender: InvoiceSender,
) : StateScreenModel<NotificationsUiState>(NotificationsUiState()) {

    val items: StateFlow<List<Notice>> = sessions.signedIn()
        .flatMapLatest { session -> notices.observe(session.idMarket, session.store?.timeZone ?: DEFAULT_TIME_ZONE) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        screenModelScope.launch {
            val zone = sessions.signedIn().first().store?.timeZone ?: DEFAULT_TIME_ZONE
            mutableState.update { it.copy(timeZone = zone, todayIso = notices.todayIso(zone)) }
        }
        refresh()
    }

    fun refresh() {
        if (state.value.sync.syncing) return
        mutableState.update { it.copy(sync = it.sync.started()) }
        // De paso, lo que espera conexión sale ya si la hay.
        sender.trigger()
        screenModelScope.launch {
            try {
                receivables.refresh(sessions.signedIn().first().idMarket)
                mutableState.update { it.copy(sync = it.sync.succeeded()) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(sync = it.sync.failed(e, "No se pudieron traer los vencimientos.")) }
            }
        }
    }

    /** Un documento rechazado vuelve a la cola y se intenta enviar ya. */
    fun retry(localId: String) {
        screenModelScope.launch {
            invoices.retry(localId)
            sender.trigger()
        }
    }
}
