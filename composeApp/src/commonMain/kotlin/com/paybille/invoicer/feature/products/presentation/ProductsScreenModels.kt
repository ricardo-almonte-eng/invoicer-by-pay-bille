package com.paybille.invoicer.feature.products.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.database.Cached
import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.core.format.formatQuantity
import com.paybille.invoicer.core.format.randomBarcode
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.platform.PickedImage
import com.paybille.invoicer.core.ui.SyncState
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.data.idMarketFlow
import com.paybille.invoicer.feature.auth.data.signedIn
import com.paybille.invoicer.feature.invoice.data.InvoiceRepository
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.domain.DraftLine
import com.paybille.invoicer.feature.products.data.ProductCreateProgress
import com.paybille.invoicer.feature.products.data.ProductForm
import com.paybille.invoicer.feature.products.data.ProductsRepository
import com.paybille.invoicer.feature.products.data.local.ProductEntity
import com.paybille.invoicer.feature.products.data.remote.CatalogItemDto
import com.paybille.invoicer.feature.products.data.remote.InventoryInfoDto
import com.paybille.invoicer.feature.products.data.remote.ProductCatalog
import com.paybille.invoicer.feature.products.data.remote.ProductDetailDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// ---------------------------------------------------------------- Lista

/** Filtro de existencia; `code` es el que entiende `ProductDao.observe`. */
enum class StockFilter(val code: Int, val label: String) {
    All(0, "Todos"),
    Available(1, "Con existencia"),
    SoldOut(2, "Agotados"),
}

data class ProductsUiState(
    val query: String = "",
    val stock: StockFilter = StockFilter.All,
    val sync: SyncState = SyncState(),
)

/** Destino Productos: el inventario del teléfono, buscado al escribir. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductsScreenModel(
    sessions: SessionRepository,
    private val repository: ProductsRepository,
) : StateScreenModel<ProductsUiState>(ProductsUiState()) {

    private val idMarket = sessions.idMarketFlow()

    val products: StateFlow<List<ProductEntity>> =
        combine(idMarket, state.map { it.query to it.stock }.distinctUntilChanged()) { market, filter -> market to filter }
            .flatMapLatest { (market, filter) -> repository.observe(market, filter.first, filter.second.code) }
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val info: StateFlow<Cached<InventoryInfoDto>?> = idMarket
        .flatMapLatest { repository.observeInfo(it) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(value: String) = mutableState.update { it.copy(query = value) }

    fun onStockChange(value: StockFilter) = mutableState.update { it.copy(stock = value) }

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
                runCatching { repository.refreshInfo(market) }
                mutableState.update { it.copy(sync = it.sync.succeeded()) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(sync = it.sync.failed(e, "No se pudo cargar el inventario.")) }
            }
        }
    }

    private companion object {
        const val STALE_AFTER_MS = 5 * 60_000L
    }
}

// ---------------------------------------------------------------- Ficha

data class ProductDetailUiState(
    val timeZone: String = DEFAULT_TIME_ZONE,
    val taxRate: Double = 0.0,
    val taxLabel: String = "ITBIS",
    val sync: SyncState = SyncState(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProductDetailScreenModel(
    private val idProduct: Int,
    private val sessions: SessionRepository,
    private val repository: ProductsRepository,
) : StateScreenModel<ProductDetailUiState>(ProductDetailUiState()) {

    private val idMarket = sessions.idMarketFlow()

    val product: StateFlow<ProductEntity?> = idMarket
        .flatMapLatest { repository.observeOne(it, idProduct) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val detail: StateFlow<Cached<ProductDetailDto>?> = idMarket
        .flatMapLatest { repository.observeDetail(it, idProduct) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Para poner nombre a la categoría, la marca y el color de la ficha. */
    val catalogs: StateFlow<Map<ProductCatalog, List<CatalogItemDto>>> = idMarket
        .flatMapLatest { repository.observeCatalogs(it) }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        screenModelScope.launch {
            val s = sessions.signedIn().first()
            mutableState.update {
                it.copy(timeZone = s.store?.timeZone ?: DEFAULT_TIME_ZONE, taxRate = s.defaultTaxRate, taxLabel = s.store?.taxLabel ?: "ITBIS")
            }
        }
        refresh()
        // Sin red no pasa nada: se nombran con lo guardado, o no se nombran.
        screenModelScope.launch {
            try {
                repository.refreshCatalogs(idMarket.first())
            } catch (_: ApiException) {
            }
        }
    }

    fun refresh() {
        if (state.value.sync.syncing) return
        mutableState.update { it.copy(sync = it.sync.started()) }
        screenModelScope.launch {
            try {
                repository.refreshDetail(idMarket.first(), idProduct)
                mutableState.update { it.copy(sync = it.sync.succeeded()) }
            } catch (e: ApiException) {
                mutableState.update { it.copy(sync = it.sync.failed(e, "No se pudo cargar el producto.")) }
            }
        }
    }
}

/**
 * Se puede editar desde el teléfono: producto general con **un solo** lote. Los únicos (IMEI,
 * serie) y los de varios lotes tienen un precio y una existencia por fila: eso es del POS.
 */
fun ProductDetailDto.isEditable(): Boolean = warehouses.size == 1 && warehouses.single().unique != true

// ---------------------------------------------------------------- Editor

data class ProductFormState(
    val name: String = "",
    val price: String = "",
    val cost: String = "",
    val amount: String = "1",
    val minAmount: String = "5",
    val barcode: String = "",
    val description: String = "",
    /** URL que ya está en el servidor; `null` = sin imagen (o quitada). */
    val image: String? = null,
    val idCategory: Int? = null,
    val idBrand: Int? = null,
    val idColor: Int? = null,
    /** `null`: la ficha guardada es de antes del campo y no se sabe; al guardar no se toca. */
    val showOnCatalog: Boolean? = false,
)

data class ProductEditorUiState(
    val loaded: Boolean = false,
    val form: ProductFormState = ProductFormState(),
    val original: ProductFormState = ProductFormState(),
    val taxRate: Double = 0.0,
    val taxLabel: String = "ITBIS",
    val taxType: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val nameError: String? = null,
    val priceError: String? = null,
    val done: Boolean = false,
    /** Foto nueva, todavía en el teléfono: se sube al guardar. */
    val newImage: PickedImage? = null,
    val catalogs: Map<ProductCatalog, List<CatalogItemDto>> = emptyMap(),
    /** Catálogo cuya hoja está abierta. */
    val picking: ProductCatalog? = null,
    val creatingItem: Boolean = false,
    val catalogMessage: String? = null,
    val catalogsLoading: Boolean = false,
) {
    val dirty: Boolean get() = form != original || newImage != null

    fun selectedId(kind: ProductCatalog): Int? = when (kind) {
        ProductCatalog.Categories -> form.idCategory
        ProductCatalog.Brands -> form.idBrand
        ProductCatalog.Colors -> form.idColor
    }

    fun nameOf(kind: ProductCatalog): String? {
        val id = selectedId(kind) ?: return null
        return catalogs[kind]?.firstOrNull { it.id == id }?.name
    }
}

/**
 * Alta rápida (`idProduct == null`: nombre, precio y cantidad, como `ProductQuickCreate.vue`) y
 * edición de un producto general. `pickFor`: se abrió desde el buscador del editor de factura;
 * al crearlo, queda como línea del borrador.
 */
class ProductEditorScreenModel(
    private val idProduct: Int?,
    private val pickFor: DocumentKind?,
    private val sessions: SessionRepository,
    private val repository: ProductsRepository,
    private val invoices: InvoiceRepository,
) : StateScreenModel<ProductEditorUiState>(ProductEditorUiState(loaded = idProduct == null)) {

    /** Lo que ya se creó si un alta se cortó a medias: el reintento sigue desde ahí. */
    private var progress = ProductCreateProgress()

    /** Foto ya subida en un intento anterior: si el guardado falla después, no se vuelve a subir. */
    private var uploaded: Pair<PickedImage, String>? = null

    init {
        screenModelScope.launch {
            val market = sessions.signedIn().first().idMarket
            repository.observeCatalogs(market).collect { catalogs -> mutableState.update { it.copy(catalogs = catalogs) } }
        }
        loadCatalogs()
        screenModelScope.launch {
            val s = sessions.signedIn().first()
            mutableState.update { it.copy(taxRate = s.defaultTaxRate, taxType = s.store?.taxType, taxLabel = s.store?.taxLabel ?: "ITBIS") }
            if (idProduct == null) {
                val form = ProductFormState(barcode = randomBarcode())
                mutableState.update { it.copy(form = form, original = form) }
                return@launch
            }
            // La ficha ya se abrió antes (el lápiz está en ella): lo guardado basta.
            val detail = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                repository.observeDetail(s.idMarket, idProduct).filterNotNull().first()
            }?.value
            val warehouse = detail?.warehouses?.singleOrNull()
            if (detail == null || warehouse == null) {
                mutableState.update { it.copy(loaded = true, error = "No se pudo cargar el producto. Vuelve a abrir su ficha con internet.") }
                return@launch
            }
            val form = ProductFormState(
                name = detail.product.name.orEmpty(),
                price = warehouse.price?.let(::formatQuantity).orEmpty(),
                cost = warehouse.cost?.let(::formatQuantity).orEmpty(),
                amount = formatQuantity(warehouse.amount ?: 0.0),
                minAmount = formatQuantity(warehouse.minAmount ?: 0.0),
                barcode = warehouse.barcode.orEmpty(),
                description = detail.product.description.orEmpty(),
                image = detail.product.image?.takeIf { it.isNotBlank() },
                // El POS guarda 0 para "sin categoría".
                idCategory = detail.product.idCategory?.takeIf { it > 0 },
                idBrand = warehouse.idBrand?.takeIf { it > 0 },
                idColor = warehouse.idColor?.takeIf { it > 0 },
                showOnCatalog = detail.product.showOnCatalog
                    ?: repository.observeCatalogIds(s.idMarket).first()?.contains(idProduct),
            )
            mutableState.update { it.copy(loaded = true, form = form, original = form, taxType = detail.product.taxType) }
        }
    }

    fun update(change: (ProductFormState) -> ProductFormState) =
        mutableState.update { it.copy(form = change(it.form), error = null, nameError = null, priceError = null) }

    fun pickImage(image: PickedImage) = mutableState.update { it.copy(newImage = image, error = null) }

    fun removeImage() = mutableState.update { it.copy(newImage = null, form = it.form.copy(image = null)) }

    fun imageFailed(message: String) = mutableState.update { it.copy(error = message) }

    fun openCatalog(kind: ProductCatalog) {
        mutableState.update { it.copy(picking = kind, catalogMessage = null) }
        if (state.value.catalogs[kind].isNullOrEmpty()) loadCatalogs()
    }

    fun closeCatalog() = mutableState.update { it.copy(picking = null, catalogMessage = null) }

    fun selectCatalogItem(id: Int?) {
        val kind = state.value.picking ?: return
        update { form ->
            when (kind) {
                ProductCatalog.Categories -> form.copy(idCategory = id)
                ProductCatalog.Brands -> form.copy(idBrand = id)
                ProductCatalog.Colors -> form.copy(idColor = id)
            }
        }
        closeCatalog()
    }

    /** Alta rápida desde la hoja; queda elegida. Necesita red, como el resto del alta. */
    fun createCatalogItem(name: String) {
        val kind = state.value.picking ?: return
        if (state.value.creatingItem || name.isBlank()) return
        mutableState.update { it.copy(creatingItem = true, catalogMessage = null) }
        screenModelScope.launch {
            try {
                val market = sessions.signedIn().first().idMarket
                val created = repository.createCatalogItem(market, kind, name)
                mutableState.update { it.copy(creatingItem = false) }
                selectCatalogItem(created.id)
            } catch (e: ApiException) {
                val message = if (e.isConnectivity) {
                    "Sin conexión. Crear una ${kind.singular.lowercase()} necesita internet."
                } else {
                    e.message ?: "No se pudo crear."
                }
                mutableState.update { it.copy(creatingItem = false, catalogMessage = message) }
            }
        }
    }

    private fun loadCatalogs() {
        if (state.value.catalogsLoading) return
        mutableState.update { it.copy(catalogsLoading = true) }
        screenModelScope.launch {
            try {
                repository.refreshCatalogs(sessions.signedIn().first().idMarket)
                mutableState.update { it.copy(catalogsLoading = false) }
            } catch (e: ApiException) {
                mutableState.update {
                    it.copy(
                        catalogsLoading = false,
                        catalogMessage = if (e.isConnectivity) "Sin conexión: se muestran las opciones guardadas." else e.message,
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun save() {
        val current = state.value
        if (current.saving || !current.loaded) return
        val f = current.form
        val price = f.price.trim().toDoubleOrNull()
        if (f.name.isBlank()) {
            mutableState.update { it.copy(nameError = "Escribe el nombre del producto.") }
            return
        }
        if (price == null || price < 0) {
            mutableState.update { it.copy(priceError = "Escribe el precio de venta.") }
            return
        }
        val form = ProductForm(
            name = f.name,
            description = f.description,
            price = price,
            cost = f.cost.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
            // Puede quedar negativa (decisión 2026-09-06): no se corrige aquí.
            amount = f.amount.trim().toDoubleOrNull() ?: 0.0,
            minAmount = f.minAmount.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
            barcode = f.barcode.trim().ifEmpty { randomBarcode() },
            idCategory = f.idCategory,
            idBrand = f.idBrand,
            idColor = f.idColor,
            colorName = current.nameOf(ProductCatalog.Colors).orEmpty(),
            showOnCatalog = f.showOnCatalog,
        )
        val newImage = current.newImage
        mutableState.update { it.copy(saving = true, error = null) }
        screenModelScope.launch {
            try {
                val session = sessions.signedIn().first()
                // La foto primero: si no sube, no se crea un producto a medias sin ella.
                val imageUrl = when {
                    newImage == null -> f.image
                    uploaded?.first === newImage -> uploaded?.second
                    else -> repository.uploadImage(newImage).also { uploaded = newImage to it }
                }
                val toSave = form.copy(image = imageUrl)
                if (idProduct == null) {
                    val id = repository.create(session, toSave, progress) { progress = it }
                    if (pickFor != null) {
                        invoices.addLine(
                            pickFor,
                            DraftLine(
                                key = Uuid.random().toString(),
                                idWarehouse = progress.warehouseId,
                                idProduct = id,
                                barcode = toSave.barcode,
                                name = toSave.name.trim(),
                                quantity = 1.0,
                                unitPrice = toSave.price,
                                stock = toSave.amount,
                                infinite = false,
                                unique = false,
                            ),
                        )
                    }
                } else {
                    val detail = repository.observeDetail(session.idMarket, idProduct).first()?.value
                    val warehouse = detail?.warehouses?.singleOrNull() ?: throw ApiException(
                        "Este producto cambió en el POS. Vuelve a abrir su ficha.",
                        ApiException.Kind.Unexpected,
                    )
                    repository.update(session, idProduct, detail.product.taxType, warehouse, toSave)
                }
                mutableState.update { it.copy(saving = false, done = true) }
            } catch (e: ApiException) {
                val message = if (e.isConnectivity) "Sin conexión. Guardar un producto necesita internet." else e.message ?: "No se pudo guardar el producto."
                mutableState.update { it.copy(saving = false, error = message) }
            }
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 3_000L
    }
}
