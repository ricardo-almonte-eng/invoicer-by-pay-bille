package com.paybille.invoicer.feature.profile

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.invoice.data.InvoiceRepository
import com.paybille.invoicer.feature.auth.domain.ProfileSyncResult
import com.paybille.invoicer.feature.auth.domain.Session
import com.paybille.invoicer.feature.auth.domain.SessionState
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data object Offline : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

data class ProfileUiState(
    val session: Session? = null,
    val sync: SyncStatus = SyncStatus.Idle,
    val confirmingLogout: Boolean = false,
    val loggingOut: Boolean = false,
    /** Documentos sin enviar: se pierden al cerrar sesión. */
    val pendingCount: Int = 0,
)

class ProfileScreenModel(
    private val sessions: SessionRepository,
    private val invoices: InvoiceRepository,
) : StateScreenModel<ProfileUiState>(ProfileUiState()) {

    init {
        screenModelScope.launch {
            invoices.observePendingCount().collect { count -> mutableState.update { it.copy(pendingCount = count) } }
        }
        screenModelScope.launch {
            sessions.state.filterIsInstance<SessionState.SignedIn>().collect { signedIn ->
                mutableState.update { it.copy(session = signedIn.session) }
            }
        }
        // Al abrir el perfil se refresca la tienda. La pantalla ya se pintó con lo local:
        // esto solo la pone al día si hay red.
        refresh()
    }

    fun refresh() {
        if (state.value.sync == SyncStatus.Syncing) return
        mutableState.update { it.copy(sync = SyncStatus.Syncing) }
        screenModelScope.launch {
            val status = when (val result = sessions.refreshProfile()) {
                ProfileSyncResult.Synced -> SyncStatus.Idle
                ProfileSyncResult.Offline -> SyncStatus.Offline
                is ProfileSyncResult.Failed -> SyncStatus.Failed(result.message)
            }
            mutableState.update { it.copy(sync = status) }
        }
    }

    fun askLogout() = mutableState.update { it.copy(confirmingLogout = true) }

    fun cancelLogout() = mutableState.update { it.copy(confirmingLogout = false) }

    fun confirmLogout() {
        if (state.value.loggingOut) return
        mutableState.update { it.copy(loggingOut = true) }
        // Al borrarse la sesión, `App` vuelve al login por su cuenta.
        screenModelScope.launch { sessions.logout() }
    }
}
