package com.paybille.invoicer.feature.invoice.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.domain.SessionState
import com.paybille.invoicer.feature.invoice.data.InvoiceRepository
import com.paybille.invoicer.feature.invoice.data.remote.CatalogPage
import com.paybille.invoicer.feature.invoice.data.remote.CatalogRemoteDataSource
import com.paybille.invoicer.feature.invoice.data.remote.ClientDto
import com.paybille.invoicer.feature.invoice.data.remote.SaleableItemDto
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.domain.DraftClient
import com.paybille.invoicer.feature.invoice.domain.DraftLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class SearchUiState<T>(
    val query: String = "",
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    /** Se eligió algo: la pantalla se cierra. */
    val done: Boolean = false,
)

/**
 * Búsqueda con espera (se consulta cuando el usuario deja de escribir) y paginación.
 * Es solo en línea: el catálogo no se copia al teléfono todavía.
 */
abstract class SearchScreenModel<T>(
    private val sessions: SessionRepository,
) : StateScreenModel<SearchUiState<T>>(SearchUiState()) {

    private var searchJob: Job? = null

    protected abstract suspend fun fetch(idMarket: Int, query: String, page: Int): CatalogPage<T>

    // No hay `init` aquí: correría antes de que la subclase tenga sus dependencias.
    // Cada subclase llama a `retry()` al final de su propio `init`.

    fun onQueryChange(value: String) {
        mutableState.update { it.copy(query = value) }
        search(immediate = false)
    }

    fun retry() = search(immediate = true)

    private fun search(immediate: Boolean) {
        searchJob?.cancel()
        searchJob = screenModelScope.launch {
            if (!immediate) delay(DEBOUNCE_MS)
            val query = state.value.query
            mutableState.update { it.copy(loading = true, error = null, offline = false) }
            try {
                val page = fetch(idMarket(), query, 1)
                mutableState.update { it.copy(items = page.items, page = 1, hasMore = page.hasNextPage, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                mutableState.update { it.withError(e).copy(loading = false, items = emptyList()) }
            }
        }
    }

    fun loadMore() {
        val current = state.value
        if (!current.hasMore || current.loading || current.loadingMore) return
        mutableState.update { it.copy(loadingMore = true) }
        screenModelScope.launch {
            try {
                val next = fetch(idMarket(), current.query, current.page + 1)
                mutableState.update {
                    // Si mientras tanto cambió la búsqueda, esta página ya no sirve.
                    if (it.query != current.query) {
                        it.copy(loadingMore = false)
                    } else {
                        it.copy(items = it.items + next.items, page = current.page + 1, hasMore = next.hasNextPage, loadingMore = false)
                    }
                }
            } catch (e: ApiException) {
                mutableState.update { it.withError(e).copy(loadingMore = false) }
            }
        }
    }

    protected fun finish() = mutableState.update { it.copy(done = true) }

    private suspend fun idMarket(): Int =
        sessions.state.filterIsInstance<SessionState.SignedIn>().first().session.idMarket

    private fun SearchUiState<T>.withError(e: ApiException) =
        if (e.isConnectivity) copy(offline = true) else copy(error = e.message ?: "No se pudo buscar.")

    private companion object {
        const val DEBOUNCE_MS = 350L
    }
}

class ProductPickerScreenModel(
    private val kind: DocumentKind,
    sessions: SessionRepository,
    private val invoices: InvoiceRepository,
    private val catalog: CatalogRemoteDataSource,
) : SearchScreenModel<SaleableItemDto>(sessions) {

    init {
        retry()
    }

    override suspend fun fetch(idMarket: Int, query: String, page: Int) =
        catalog.searchProducts(idMarket, query, page, PAGE_SIZE)

    @OptIn(ExperimentalUuidApi::class)
    fun select(item: SaleableItemDto) {
        screenModelScope.launch {
            invoices.addLine(
                kind,
                DraftLine(
                    key = Uuid.random().toString(),
                    idWarehouse = item.id,
                    idProduct = item.idProduct ?: item.product?.id,
                    barcode = item.barcode,
                    name = item.displayName,
                    quantity = 1.0,
                    unitPrice = item.price ?: 0.0,
                    stock = item.amount,
                    infinite = item.infinityAmount == true,
                    unique = item.unique == true,
                ),
            )
            finish()
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

class ClientPickerScreenModel(
    private val kind: DocumentKind,
    sessions: SessionRepository,
    private val invoices: InvoiceRepository,
    private val catalog: CatalogRemoteDataSource,
) : SearchScreenModel<ClientDto>(sessions) {

    init {
        retry()
    }

    override suspend fun fetch(idMarket: Int, query: String, page: Int) =
        catalog.searchClients(idMarket, query, page, PAGE_SIZE)

    fun select(client: ClientDto) {
        screenModelScope.launch {
            val identify = client.identify?.replace("-", "")?.trim()?.takeIf { it.isNotEmpty() }
            invoices.update(kind) { draft ->
                draft.copy(
                    client = DraftClient(
                        id = client.id,
                        name = client.fullName.ifBlank { "Cliente ${client.id}" },
                        phone = client.phone,
                        identify = identify,
                    ),
                    // Con NCF activo y sin RNC escrito, se toma la cédula del cliente (como el POS).
                    rnc = if (draft.withNcf && draft.rnc.isBlank()) identify.orEmpty() else draft.rnc,
                )
            }
            finish()
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
