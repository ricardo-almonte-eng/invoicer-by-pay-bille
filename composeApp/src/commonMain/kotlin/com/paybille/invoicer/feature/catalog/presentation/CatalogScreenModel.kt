package com.paybille.invoicer.feature.catalog.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.network.ApiConfig
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.platform.DocumentPlatform
import com.paybille.invoicer.core.ui.SyncState
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.data.idMarketFlow
import com.paybille.invoicer.feature.auth.data.signedIn
import com.paybille.invoicer.feature.catalog.domain.CatalogLink
import com.paybille.invoicer.feature.products.data.ProductsRepository
import com.paybille.invoicer.feature.products.data.local.ProductEntity
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

/** Qué productos enseñar en la lista del catálogo. */
enum class CatalogFilter(val label: String) {
    All("Todos"),
    Shown("En el catálogo"),
    Hidden("Fuera"),
}

/** Un producto del inventario y si sale en el catálogo público. */
data class CatalogItem(val product: ProductEntity, val shown: Boolean)

data class CatalogUiState(
    val storeName: String? = null,
    /** Enlace para compartir, o null si el nombre de la tienda no sirve (ver [invalidCharacters]). */
    val url: String? = null,
    val invalidCharacters: Set<Char> = emptySet(),
    val query: String = "",
    val filter: CatalogFilter = CatalogFilter.All,
    val sync: SyncState = SyncState(),
    /** Productos que se están metiendo o sacando ahora mismo. */
    val busy: Set<Int> = emptySet(),
    val message: String? = null,
)

/**
 * "Catálogo en línea": el enlace del catálogo público de la tienda (web del POS) para
 * compartirlo, y qué productos salen en él (`products.IndShowOnCatalog`).
 *
 * La lista es el inventario de Room; qué está marcado se guarda en `cached_payloads` y se
 * refresca al abrir. Marcar o desmarcar necesita red: es un PUT a `products`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogScreenModel(
    private val sessions: SessionRepository,
    private val products: ProductsRepository,
    private val config: ApiConfig,
    private val platform: DocumentPlatform,
) : StateScreenModel<CatalogUiState>(CatalogUiState()) {

    private val idMarket = sessions.idMarketFlow()

    /** Ids marcados; `null` mientras no se haya traído nunca. */
    val shownIds: StateFlow<Set<Int>?> = idMarket
        .flatMapLatest { products.observeCatalogIds(it) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items: StateFlow<List<CatalogItem>> =
        combine(
            idMarket,
            state.map { it.query }.distinctUntilChanged(),
        ) { market, query -> market to query }
            .flatMapLatest { (market, query) -> products.observe(market, query, 0) }
            .combine(shownIds) { rows, shown -> rows.map { CatalogItem(it, shown?.contains(it.idProduct) == true) } }
            .combine(state.map { it.filter }.distinctUntilChanged()) { rows, filter ->
                when (filter) {
                    CatalogFilter.All -> rows
                    CatalogFilter.Shown -> rows.filter { it.shown }
                    CatalogFilter.Hidden -> rows.filterNot { it.shown }
                }
            }
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        screenModelScope.launch {
            val session = sessions.signedIn().first()
            val name = session.store?.name
            mutableState.update {
                it.copy(
                    storeName = name,
                    url = name?.let { n -> CatalogLink.url(config.webBaseUrl, n) },
                    invalidCharacters = name?.let(CatalogLink::invalidCharacters).orEmpty(),
                )
            }
        }
        refresh()
    }

    fun onQueryChange(value: String) = mutableState.update { it.copy(query = value) }

    fun onFilterChange(value: CatalogFilter) = mutableState.update { it.copy(filter = value) }

    fun refresh() {
        if (state.value.sync.syncing) return
        mutableState.update { it.copy(sync = it.sync.started()) }
        screenModelScope.launch {
            try {
                val market = idMarket.first()
                products.refreshCatalogIds(market)
                mutableState.update { it.copy(sync = it.sync.succeeded()) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(sync = it.sync.failed(e, "No se pudo cargar el catálogo.")) }
            }
        }
    }

    fun toggle(idProduct: Int, shown: Boolean) {
        if (idProduct in state.value.busy) return
        mutableState.update { it.copy(busy = it.busy + idProduct, message = null) }
        screenModelScope.launch {
            try {
                products.setShowOnCatalog(idMarket.first(), idProduct, shown)
            } catch (e: ApiException) {
                val message = if (e.isConnectivity) "Sin conexión. Cambiar el catálogo necesita internet." else e.message
                mutableState.update { it.copy(message = message) }
            } finally {
                mutableState.update { it.copy(busy = it.busy - idProduct) }
            }
        }
    }

    fun dismissMessage() = mutableState.update { it.copy(message = null) }

    fun share() {
        val current = state.value
        val url = current.url ?: return
        val store = current.storeName ?: "la tienda"
        platform.shareText(text = "Mira el catálogo de $store: $url", title = "Catálogo de $store")
    }
}
