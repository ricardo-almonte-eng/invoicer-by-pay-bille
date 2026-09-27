package com.paybille.invoicer.feature.banks.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.core.format.DateRange
import com.paybille.invoicer.core.format.lastDays
import com.paybille.invoicer.core.format.todayIn
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.ui.SyncState
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.data.idMarketFlow
import com.paybille.invoicer.feature.auth.data.signedIn
import com.paybille.invoicer.feature.banks.data.ACCOUNT_TYPES
import com.paybille.invoicer.feature.banks.data.AccountForm
import com.paybille.invoicer.feature.banks.data.BanksRepository
import com.paybille.invoicer.feature.banks.data.local.AccountMovementEntity
import com.paybille.invoicer.feature.banks.data.local.BankAccountEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun ApiException.forSave(what: String): String =
    if (isConnectivity) "Sin conexión. $what necesita internet." else message ?: "No se pudo guardar."

// ---------------------------------------------------------------- Lista

data class BanksUiState(val sync: SyncState = SyncState(), val creatingCash: Boolean = false, val message: String? = null)

/** Destino Bancos: las cuentas de dinero y cuánto hay en cada una (`cuentasBancarias.vue`). */
@OptIn(ExperimentalCoroutinesApi::class)
class BanksScreenModel(
    private val sessions: SessionRepository,
    private val repository: BanksRepository,
) : StateScreenModel<BanksUiState>(BanksUiState()) {

    private val idMarket = sessions.idMarketFlow()

    val accounts: StateFlow<List<BankAccountEntity>> = idMarket
        .flatMapLatest { repository.observeAccounts(it) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onVisible() {
        if (state.value.sync.isStale(STALE_AFTER_MS)) refresh()
    }

    fun refresh() {
        if (state.value.sync.syncing) return
        mutableState.update { it.copy(sync = it.sync.started(), message = null) }
        screenModelScope.launch {
            try {
                repository.syncAccounts(idMarket.first())
                mutableState.update { it.copy(sync = it.sync.succeeded()) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(sync = it.sync.failed(e, "No se pudieron cargar las cuentas.")) }
            }
        }
    }

    /** No hay caja activa: se crea "Efectivo General", igual que ofrece el POS. */
    fun createCash() {
        if (state.value.creatingCash) return
        mutableState.update { it.copy(creatingCash = true, message = null) }
        screenModelScope.launch {
            try {
                val form = AccountForm("Efectivo General", "Caja", bankName = null, accountNumber = null, description = null, active = true)
                repository.save(idMarket.first(), null, form)
                mutableState.update { it.copy(creatingCash = false) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(creatingCash = false, message = e.forSave("Crear la cuenta")) }
            }
        }
    }

    private companion object {
        const val STALE_AFTER_MS = 60_000L
    }
}

// ---------------------------------------------------------------- Ficha

/** Rangos de movimientos: el POS abre en 30 días y no deja pasar de 60. */
val MOVEMENT_DAYS = listOf(7, 30, 60)

enum class MovementType(val apiValue: String, val label: String) { In("Ingreso", "Entra dinero"), Out("Egreso", "Sale dinero") }

data class BankDetailUiState(
    val timeZone: String = DEFAULT_TIME_ZONE,
    val days: Int = 30,
    val range: DateRange? = null,
    val sync: SyncState = SyncState(),
    // Hoja de movimiento manual.
    val sheetOpen: Boolean = false,
    val movementType: MovementType = MovementType.In,
    val amount: Double = 0.0,
    val description: String = "",
    val reference: String = "",
    val savingMovement: Boolean = false,
    val movementError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BankAccountDetailScreenModel(
    private val id: Int,
    private val sessions: SessionRepository,
    private val repository: BanksRepository,
) : StateScreenModel<BankDetailUiState>(BankDetailUiState()) {

    val account: StateFlow<BankAccountEntity?> = repository.observeAccount(id)
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val movements: StateFlow<List<AccountMovementEntity>> = state
        .map { it.range to it.timeZone }
        .flatMapLatest { (range, zone) -> if (range == null) flowOf(emptyList()) else repository.observeMovements(id, range, zone) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        screenModelScope.launch {
            val zone = sessions.signedIn().first().store?.timeZone ?: DEFAULT_TIME_ZONE
            mutableState.update { it.copy(timeZone = zone, range = lastDays(todayIn(zone), it.days)) }
            refresh()
        }
    }

    fun selectDays(days: Int) {
        mutableState.update { it.copy(days = days, range = lastDays(todayIn(it.timeZone), days)) }
        refresh()
    }

    fun refresh() {
        val current = state.value
        val range = current.range ?: return
        mutableState.update { it.copy(sync = it.sync.started()) }
        screenModelScope.launch {
            try {
                val market = sessions.idMarketFlow().first()
                repository.syncMovements(market, id, range, current.timeZone)
                runCatching { repository.syncAccounts(market) }
                mutableState.update { it.copy(sync = it.sync.succeeded()) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(sync = it.sync.failed(e, "No se pudieron cargar los movimientos.")) }
            }
        }
    }

    fun openMovement() = mutableState.update {
        it.copy(sheetOpen = true, movementType = MovementType.In, amount = 0.0, description = "", reference = "", movementError = null)
    }

    fun closeMovement() = mutableState.update { if (it.savingMovement) it else it.copy(sheetOpen = false) }

    fun updateMovement(change: (BankDetailUiState) -> BankDetailUiState) = mutableState.update { change(it).copy(movementError = null) }

    fun saveMovement() {
        val current = state.value
        if (current.savingMovement) return
        if (current.amount <= 0.0) {
            mutableState.update { it.copy(movementError = "Escribe el monto.") }
            return
        }
        mutableState.update { it.copy(savingMovement = true, movementError = null) }
        screenModelScope.launch {
            try {
                repository.addMovement(
                    session = sessions.signedIn().first(),
                    idCuenta = id,
                    type = current.movementType.apiValue,
                    amount = current.amount,
                    description = current.description,
                    reference = current.reference,
                )
                mutableState.update { it.copy(savingMovement = false, sheetOpen = false) }
                refresh()
            } catch (e: ApiException) {
                mutableState.update { it.copy(savingMovement = false, movementError = e.forSave("Registrar un movimiento")) }
            }
        }
    }
}

// ---------------------------------------------------------------- Editor

data class AccountFormState(
    val name: String = "",
    val type: String = ACCOUNT_TYPES[1].first,
    val bankName: String = "",
    val accountNumber: String = "",
    val holderName: String = "",
    val holderId: String = "",
    val description: String = "",
    val active: Boolean = true,
)

data class AccountEditorUiState(
    val loaded: Boolean = false,
    val form: AccountFormState = AccountFormState(),
    val original: AccountFormState = AccountFormState(),
    val saving: Boolean = false,
    val error: String? = null,
    val nameError: String? = null,
    val done: Boolean = false,
) {
    val dirty: Boolean get() = form != original
}

class BankAccountEditorScreenModel(
    private val id: Int?,
    private val sessions: SessionRepository,
    private val repository: BanksRepository,
) : StateScreenModel<AccountEditorUiState>(AccountEditorUiState(loaded = id == null)) {

    init {
        if (id != null) {
            screenModelScope.launch {
                val account = repository.observeAccount(id).first()
                val form = account?.let {
                    AccountFormState(
                        name = it.name,
                        type = it.type,
                        bankName = it.bankName.orEmpty(),
                        accountNumber = it.accountNumber.orEmpty(),
                        holderName = it.holderName.orEmpty(),
                        holderId = it.holderId.orEmpty(),
                        description = it.description.orEmpty(),
                        active = it.active,
                    )
                } ?: AccountFormState()
                mutableState.update { it.copy(loaded = true, form = form, original = form) }
            }
        }
    }

    fun update(change: (AccountFormState) -> AccountFormState) =
        mutableState.update { it.copy(form = change(it.form), error = null, nameError = null) }

    fun save() {
        val current = state.value
        if (current.saving || !current.loaded) return
        val f = current.form
        if (f.name.isBlank()) {
            mutableState.update { it.copy(nameError = "Escribe un nombre para la cuenta.") }
            return
        }
        mutableState.update { it.copy(saving = true, error = null) }
        screenModelScope.launch {
            try {
                val isCash = f.type == "Caja"
                repository.save(
                    sessions.idMarketFlow().first(),
                    id,
                    AccountForm(
                        name = f.name,
                        type = f.type,
                        bankName = f.bankName.takeUnless { isCash },
                        accountNumber = f.accountNumber.takeUnless { isCash },
                        description = f.description,
                        active = f.active,
                        holderName = f.holderName.takeUnless { isCash },
                        holderId = f.holderId.takeUnless { isCash },
                    ),
                )
                mutableState.update { it.copy(saving = false, done = true) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(saving = false, error = e.forSave("Guardar la cuenta")) }
            }
        }
    }
}
