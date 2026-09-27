package com.paybille.invoicer.feature.clients.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.database.Cached
import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.core.format.digitsOnly
import com.paybille.invoicer.core.format.formatCedula
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.ui.SyncState
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.data.signedIn
import com.paybille.invoicer.feature.clients.data.ClientsRepository
import com.paybille.invoicer.feature.clients.data.local.ClientEntity
import com.paybille.invoicer.feature.clients.data.remote.ClientActivityDto
import com.paybille.invoicer.feature.clients.data.remote.ClientForm
import com.paybille.invoicer.feature.invoice.data.InvoiceRepository
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.domain.DraftClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Lista

data class ClientsUiState(val query: String = "", val sync: SyncState = SyncState())

/** Destino Clientes: la copia local se busca al escribir; se pone al día al mostrarse. */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientsScreenModel(
    sessions: SessionRepository,
    private val repository: ClientsRepository,
) : StateScreenModel<ClientsUiState>(ClientsUiState()) {

    private val idMarket = sessions.signedIn().map { it.idMarket }.distinctUntilChanged()

    val clients: StateFlow<List<ClientEntity>> =
        combine(idMarket, state.map { it.query }.distinctUntilChanged()) { market, query -> market to query }
            .flatMapLatest { (market, query) -> repository.observe(market, query) }
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saldo por cobrar de cada cliente (`IdClient` → saldo). */
    val balances: StateFlow<Map<Int, Double>> = idMarket
        .flatMapLatest { repository.observeBalances(it) }
        .map { cached -> cached?.value.orEmpty().filter { (it.partyKey ?: 0) > 0 }.associate { it.partyKey!! to (it.balance ?: 0.0) } }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun onQueryChange(value: String) = mutableState.update { it.copy(query = value) }

    fun onVisible() {
        if (state.value.sync.isStale(STALE_AFTER_MS)) refresh()
    }

    fun refresh() {
        if (state.value.sync.syncing) return
        mutableState.update { it.copy(sync = it.sync.started()) }
        screenModelScope.launch {
            try {
                val market = idMarket.first()
                repository.sync(market)
                runCatching { repository.refreshBalances(market) }
                mutableState.update { it.copy(sync = it.sync.succeeded()) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(sync = it.sync.failed(e, "No se pudieron cargar los clientes.")) }
            }
        }
    }

    private companion object {
        const val STALE_AFTER_MS = 5 * 60_000L
    }
}

// ---------------------------------------------------------------- Ficha

data class ClientDetailUiState(val timeZone: String = DEFAULT_TIME_ZONE, val sync: SyncState = SyncState())

@OptIn(ExperimentalCoroutinesApi::class)
class ClientDetailScreenModel(
    private val id: Int,
    sessions: SessionRepository,
    private val repository: ClientsRepository,
    private val invoices: InvoiceRepository,
) : StateScreenModel<ClientDetailUiState>(ClientDetailUiState()) {

    private val session = sessions.signedIn()
    private val idMarket = session.map { it.idMarket }.distinctUntilChanged()

    val client: StateFlow<ClientEntity?> = repository.observeOne(id)
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activity: StateFlow<Cached<ClientActivityDto>?> = idMarket
        .flatMapLatest { repository.observeActivity(it, id) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        screenModelScope.launch {
            val zone = session.first().store?.timeZone ?: DEFAULT_TIME_ZONE
            mutableState.update { it.copy(timeZone = zone) }
        }
        refresh()
    }

    fun refresh() {
        if (state.value.sync.syncing) return
        mutableState.update { it.copy(sync = it.sync.started()) }
        screenModelScope.launch {
            try {
                repository.refreshActivity(idMarket.first(), id)
                mutableState.update { it.copy(sync = it.sync.succeeded()) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(sync = it.sync.failed(e, "No se pudo cargar lo que debe este cliente.")) }
            }
        }
    }

    /** Pone a este cliente en el borrador de factura y avisa para abrir el editor. */
    fun newInvoice(open: () -> Unit) {
        val c = client.value ?: return open()
        screenModelScope.launch {
            invoices.update(DocumentKind.Invoice) { draft ->
                draft.copy(
                    client = DraftClient(id = c.id, name = c.fullName.ifBlank { "Cliente ${c.id}" }, phone = c.phone, identify = c.identify),
                    rnc = if (draft.withNcf && draft.rnc.isBlank()) c.identify.orEmpty() else draft.rnc,
                )
            }
            open()
        }
    }
}

// ---------------------------------------------------------------- Editor

/** Tipos de documento de `CreateClient.vue`, con el valor que guarda el POS. */
val IDENTIFY_TYPES = listOf("Cedula" to "Cédula", "Pasaporte" to "Pasaporte", "Licencia de conducir" to "Licencia")

data class ClientFormState(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val whatsapp: Boolean = false,
    val email: String = "",
    val address: String = "",
    val identifyType: String = IDENTIFY_TYPES.first().first,
    val identify: String = "",
    val discount: String = "",
    val payItbis: Boolean = true,
)

data class ClientEditorUiState(
    val loaded: Boolean = false,
    val form: ClientFormState = ClientFormState(),
    val original: ClientFormState = ClientFormState(),
    val saving: Boolean = false,
    val error: String? = null,
    val nameError: String? = null,
    /** Guardado: la pantalla se cierra. */
    val done: Boolean = false,
) {
    val dirty: Boolean get() = form != original
}

/**
 * Alta (`id == null`) y edición de un cliente. `pickFor`: se abrió desde el buscador del editor
 * de factura; al guardar, el cliente nuevo queda puesto en ese borrador.
 */
class ClientEditorScreenModel(
    private val id: Int?,
    private val pickFor: DocumentKind?,
    private val sessions: SessionRepository,
    private val repository: ClientsRepository,
    private val invoices: InvoiceRepository,
) : StateScreenModel<ClientEditorUiState>(ClientEditorUiState(loaded = id == null)) {

    init {
        if (id != null) {
            screenModelScope.launch {
                val entity = repository.observeOne(id).first()
                val form = entity?.toForm() ?: ClientFormState()
                mutableState.update { it.copy(loaded = true, form = form, original = form) }
            }
        }
    }

    fun update(change: (ClientFormState) -> ClientFormState) =
        mutableState.update { it.copy(form = change(it.form), error = null, nameError = null) }

    fun save() {
        val current = state.value
        if (current.saving) return
        val form = current.form
        if (form.firstName.isBlank()) {
            mutableState.update { it.copy(nameError = "Escribe el nombre del cliente.") }
            return
        }
        val discount = form.discount.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        if (discount != null && (discount < 0 || discount > 100)) {
            mutableState.update { it.copy(error = "El descuento va de 0 a 100 %.") }
            return
        }
        mutableState.update { it.copy(saving = true, error = null) }
        screenModelScope.launch {
            try {
                val market = sessions.signedIn().first().idMarket
                val identify = if (form.identifyType == IDENTIFY_TYPES.first().first) digitsOnly(form.identify) else form.identify.trim()
                val saved = repository.save(
                    market,
                    id,
                    ClientForm(
                        firstName = form.firstName,
                        lastName = form.lastName,
                        phone = form.phone,
                        whatsapp = form.whatsapp,
                        email = form.email,
                        address = form.address,
                        identifyType = form.identifyType.takeIf { identify.isNotEmpty() },
                        identify = identify,
                        discount = discount?.toInt(),
                        payItbis = form.payItbis,
                    ),
                )
                if (pickFor != null) {
                    invoices.update(pickFor) { draft ->
                        draft.copy(
                            client = DraftClient(id = saved.id, name = saved.fullName, phone = saved.phone, identify = saved.identify),
                            rnc = if (draft.withNcf && draft.rnc.isBlank()) saved.identify.orEmpty() else draft.rnc,
                        )
                    }
                }
                mutableState.update { it.copy(saving = false, done = true) }
            } catch (e: ApiException) {
                val message = if (e.isConnectivity) "Sin conexión. Guardar un cliente necesita internet." else e.message ?: "No se pudo guardar el cliente."
                mutableState.update { it.copy(saving = false, error = message) }
            }
        }
    }
}

private fun ClientEntity.toForm() = ClientFormState(
    firstName = firstName,
    lastName = lastName.orEmpty(),
    phone = phone.orEmpty(),
    whatsapp = whatsapp,
    email = email.orEmpty(),
    address = address.orEmpty(),
    identifyType = identifyType?.takeIf { type -> IDENTIFY_TYPES.any { it.first == type } } ?: IDENTIFY_TYPES.first().first,
    identify = identify?.let { if (identifyType == null || identifyType == IDENTIFY_TYPES.first().first) formatCedula(it) else it }.orEmpty(),
    discount = discount?.takeIf { it > 0 }?.toString().orEmpty(),
    payItbis = payItbis,
)
