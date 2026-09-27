package com.paybille.invoicer.feature.products.data

import com.paybille.invoicer.core.billing.TaxType
import com.paybille.invoicer.core.billing.lineTotals
import com.paybille.invoicer.core.database.Cached
import com.paybille.invoicer.core.database.PayloadCache
import com.paybille.invoicer.core.format.formatQuantity
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.platform.PickedImage
import com.paybille.invoicer.feature.auth.domain.Session
import com.paybille.invoicer.feature.products.data.local.ProductDao
import com.paybille.invoicer.feature.products.data.local.ProductEntity
import com.paybille.invoicer.feature.products.data.remote.CatalogItemDto
import com.paybille.invoicer.feature.products.data.remote.GroupedProductDto
import com.paybille.invoicer.feature.products.data.remote.InventoryInfoDto
import com.paybille.invoicer.feature.products.data.remote.ProductDetailDto
import com.paybille.invoicer.feature.products.data.remote.ProductCatalog
import com.paybille.invoicer.feature.products.data.remote.ProductsRemoteDataSource
import com.paybille.invoicer.feature.products.data.remote.WarehouseRecordDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/** Lo que se escribe en el alta rápida o al editar un producto general. */
data class ProductForm(
    val name: String,
    val description: String?,
    /** Precio de venta, **con** impuesto incluido (regla crítica 1). */
    val price: Double,
    val cost: Double,
    val amount: Double,
    val minAmount: Double,
    val barcode: String,
    /** URL ya subida (`image/upload`), o `null` sin imagen. */
    val image: String? = null,
    val idCategory: Int? = null,
    val idBrand: Int? = null,
    val idColor: Int? = null,
    /** Nombre del color: `warehouse.Color` es el texto heredado que el POS sigue leyendo. */
    val colorName: String = "",
    /**
     * `products.IndShowOnCatalog`: sale en el catálogo público que se comparte. `null` = no se
     * sabe (ficha guardada antes de existir el campo): al editar no se toca.
     */
    val showOnCatalog: Boolean? = false,
)

/** Lo que ya llegó al servidor de un alta que se cortó: al reintentar no se repite. */
data class ProductCreateProgress(val productId: Int? = null, val warehouseId: Int? = null)

/**
 * Inventario. La lista vive en Room (copia completa, hasta [MAX_PAGES] páginas) y se busca en
 * el teléfono; la ficha de cada producto se guarda como JSON. Crear y editar necesitan red y
 * replican `ProductQuickCreate.vue` / `producto.vue` del POS.
 */
class ProductsRepository(
    private val dao: ProductDao,
    private val remote: ProductsRemoteDataSource,
    private val cache: PayloadCache,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    fun observe(idMarket: Int, query: String, stock: Int): Flow<List<ProductEntity>> = dao.observe(idMarket, query.trim(), stock)

    fun observeOne(idMarket: Int, idProduct: Int): Flow<ProductEntity?> = dao.observeOne(idMarket, idProduct)

    /** @throws com.paybille.invoicer.core.network.ApiException sin red o si el servidor falla. */
    suspend fun sync(idMarket: Int) {
        val rows = mutableListOf<ProductEntity>()
        var page = 1
        do {
            val result = remote.groupedPage(idMarket, page, PAGE_SIZE)
            rows += result.items.mapNotNull { it.toEntity(idMarket, now()) }
            page++
        } while (result.hasNextPage && page <= MAX_PAGES)
        dao.replaceAll(idMarket, rows.distinctBy { it.idProduct })
    }

    fun observeInfo(idMarket: Int): Flow<Cached<InventoryInfoDto>?> = cache.observe(idMarket, INFO_KEY, InventoryInfoDto.serializer())

    suspend fun refreshInfo(idMarket: Int) = cache.put(idMarket, INFO_KEY, InventoryInfoDto.serializer(), remote.info(idMarket))

    fun observeDetail(idMarket: Int, idProduct: Int): Flow<Cached<ProductDetailDto>?> =
        cache.observe(idMarket, detailKey(idProduct), ProductDetailDto.serializer())

    suspend fun refreshDetail(idMarket: Int, idProduct: Int) {
        val detail = ProductDetailDto(
            product = remote.product(idProduct),
            warehouses = remote.warehouses(idMarket, idProduct),
            history = remote.history(idMarket, idProduct),
        )
        cache.put(idMarket, detailKey(idProduct), ProductDetailDto.serializer(), detail)
    }

    /**
     * Alta rápida de un producto general: `products` → `warehouse` → `reportInventory`, igual
     * que el POS. `progress`: lo que ya se creó en un intento anterior que falló después; no se
     * repite (sin fichas ni existencias duplicadas con mala señal).
     *
     * @param onProgress se llama tras cada paso, para reintentar desde ahí.
     * @return el `idProduct`.
     */
    suspend fun create(
        session: Session,
        form: ProductForm,
        progress: ProductCreateProgress,
        onProgress: (ProductCreateProgress) -> Unit,
    ): Int {
        var done = progress
        val idProduct = done.productId ?: remote.createProduct(
            buildJsonObject {
                put("Name", form.name.trim())
                put("Description", form.description?.trim().orEmpty())
                put("Image", form.image)
                put("IdCategory", form.idCategory ?: 0)
                put("IdFamily", 0)
                put("IdSubFamily", 0)
                put("IdGroup", 0)
                put("IdGuarantee", JsonNull)
                put("isProduced", true)
                put("isSolding", true)
                put("IsPart", false)
                put("IndShowOnCatalog", form.showOnCatalog == true)
                put("IsWorkshop", false)
                put("isCollection", false)
                put("IdMarket", session.idMarket)
            },
        ).id.also {
            done = done.copy(productId = it)
            onProgress(done)
        }

        val tax = unitTax(form.price, session.store?.taxType, session.defaultTaxRate)
        val idWarehouse = done.warehouseId ?: remote.createWarehouse(
            buildJsonObject {
                put("idProduct", idProduct)
                // camelCase: es el atributo del modelo `Warehouse` (el POS manda lo mismo).
                put("idMarket", session.idMarket)
                put("IdProvider", JsonNull)
                put("shopping", JsonNull)
                put("Barcode", form.barcode)
                put("Price1", form.price)
                put("Price2", 0)
                put("Price3", 0)
                put("Price4", 0)
                put("Tax1", tax)
                put("Tax2", 0)
                put("Tax3", 0)
                put("Tax4", 0)
                put("utility1", form.price - tax - form.cost)
                put("utility2", 0)
                put("utility3", 0)
                put("utility4", 0)
                put("RangeMin", 1)
                put("RangeMax", 1)
                put("RangeTax", 0)
                put("MinRetailQty", 1)
                put("MaxRetailQty", 1)
                put("retail", false)
                put("Size", 1)
                put("TypeSize", "Unidades")
                put("Color", form.colorName)
                put("IdColor", form.idColor)
                put("IdBrand", form.idBrand)
                put("unique", false)
                put("Amount", form.amount)
                put("MinAmountQty", form.minAmount)
                put("infinityAmount", false)
                put("Cost", form.cost)
                put("ignoreInReport", false)
            },
        ).id.also {
            done = done.copy(warehouseId = it)
            onProgress(done)
        }
        val user = session.displayName
        remote.createInventoryReport(
            buildJsonObject {
                put("IdProduct", idProduct)
                put("ProductName", form.name.trim())
                put("Name", form.name.trim())
                put("User", user)
                put("IdMarket", session.idMarket)
                put("Comentary", "${formatQuantity(form.amount)} Productos Ingresado por $user")
                put("Barcode", form.barcode)
                put("Total", form.price)
                put("IdWarehouse", idWarehouse)
                put("After", form.amount)
                put("IdUser", session.userId)
                put("IdPerson", session.idPerson)
            },
        )
        dao.upsert(listOf(form.toEntity(session.idMarket, idProduct, now())))
        rememberShown(session.idMarket, idProduct, form.showOnCatalog == true)
        return idProduct
    }

    /**
     * Edita un producto general de **un solo** `warehouse` (con varios lotes, cada uno tiene su
     * precio y existencia: eso se hace en el POS). Si cambió la existencia, deja el rastro en
     * `reportInventory` con el antes y el después.
     */
    suspend fun update(session: Session, idProduct: Int, taxType: String?, warehouse: WarehouseRecordDto, form: ProductForm) {
        val name = form.name.trim()
        remote.updateProduct(
            idProduct,
            buildJsonObject {
                put("Name", name)
                put("Description", form.description?.trim().orEmpty())
                put("Image", form.image)
                put("IdCategory", form.idCategory ?: 0)
                form.showOnCatalog?.let { put("IndShowOnCatalog", it) }
            },
        )
        form.showOnCatalog?.let { rememberShown(session.idMarket, idProduct, it) }
        val tax = unitTax(form.price, taxType, session.defaultTaxRate)
        remote.updateWarehouse(
            warehouse.id,
            buildJsonObject {
                put("Barcode", form.barcode)
                put("Price1", form.price)
                put("Tax1", tax)
                put("utility1", form.price - tax - form.cost)
                put("Cost", form.cost)
                put("Amount", form.amount)
                put("MinAmountQty", form.minAmount)
                put("Color", form.colorName)
                put("IdColor", form.idColor)
                put("IdBrand", form.idBrand)
            },
        )
        val before = warehouse.amount ?: 0.0
        if (before != form.amount) {
            val user = session.displayName
            remote.createInventoryReport(
                buildJsonObject {
                    put("IdProduct", idProduct)
                    put("ProductName", name)
                    put("Name", name)
                    put("User", user)
                    put("IdMarket", session.idMarket)
                    put("Comentary", "Existencia ajustada por $user")
                    put("Barcode", form.barcode)
                    put("IdWarehouse", warehouse.id)
                    put("Before", before)
                    put("After", form.amount)
                    put("IdUser", session.userId)
                    put("IdPerson", session.idPerson)
                },
            )
        }
        dao.upsert(listOf(form.toEntity(session.idMarket, idProduct, now())))
        runCatching { refreshDetail(session.idMarket, idProduct) }
    }

    suspend fun clear() = dao.clear()

    // ------------------------------------------------------------ Catálogo público

    /** Ids de los productos que salen en el catálogo (lo último que se trajo), o null si nunca. */
    fun observeCatalogIds(idMarket: Int): Flow<Set<Int>?> =
        cache.observe(idMarket, CATALOG_IDS_KEY, IDS_SERIALIZER).map { it?.value?.toSet() }

    /** @throws ApiException sin red o si el servidor falla. */
    suspend fun refreshCatalogIds(idMarket: Int) {
        cache.put(idMarket, CATALOG_IDS_KEY, IDS_SERIALIZER, remote.catalogProductIds(idMarket).distinct())
    }

    /** Mete o saca un producto del catálogo. Necesita red. @throws ApiException */
    suspend fun setShowOnCatalog(idMarket: Int, idProduct: Int, shown: Boolean) {
        remote.setShowOnCatalog(idProduct, shown)
        rememberShown(idMarket, idProduct, shown)
    }

    private suspend fun rememberShown(idMarket: Int, idProduct: Int, shown: Boolean) {
        val current = observeCatalogIds(idMarket).first() ?: return // nunca se trajo: la próxima vez viene entera
        val next = if (shown) current + idProduct else current - idProduct
        if (next != current) cache.put(idMarket, CATALOG_IDS_KEY, IDS_SERIALIZER, next.toList())
    }

    // ------------------------------------------------------------ Catálogos e imagen

    /** Categorías, marcas o colores guardados (sin red también). */
    fun observeCatalog(idMarket: Int, kind: ProductCatalog): Flow<Cached<List<CatalogItemDto>>?> =
        cache.observe(idMarket, catalogKey(kind), CATALOG_SERIALIZER)

    /** Los tres catálogos juntos (vacíos si todavía no se han traído). */
    fun observeCatalogs(idMarket: Int): Flow<Map<ProductCatalog, List<CatalogItemDto>>> = combine(
        observeCatalog(idMarket, ProductCatalog.Categories),
        observeCatalog(idMarket, ProductCatalog.Brands),
        observeCatalog(idMarket, ProductCatalog.Colors),
    ) { categories, brands, colors ->
        mapOf(
            ProductCatalog.Categories to categories?.value.orEmpty(),
            ProductCatalog.Brands to brands?.value.orEmpty(),
            ProductCatalog.Colors to colors?.value.orEmpty(),
        )
    }

    /** Trae los tres catálogos. Si uno falla, los demás se guardan igual y se lanza el primer error. */
    suspend fun refreshCatalogs(idMarket: Int) {
        var failure: ApiException? = null
        ProductCatalog.entries.forEach { kind ->
            try {
                cache.put(idMarket, catalogKey(kind), CATALOG_SERIALIZER, remote.catalog(kind))
            } catch (e: ApiException) {
                if (failure == null) failure = e
            }
        }
        failure?.let { throw it }
    }

    /** Alta rápida desde el formulario; queda en el catálogo guardado sin esperar a otra sincronización. */
    suspend fun createCatalogItem(idMarket: Int, kind: ProductCatalog, name: String): CatalogItemDto {
        val created = remote.createCatalogItem(kind, name.trim(), idMarket)
        val current = observeCatalog(idMarket, kind).first()?.value.orEmpty()
        cache.put(idMarket, catalogKey(kind), CATALOG_SERIALIZER, (current + created).sortedBy { it.name.lowercase() })
        return created
    }

    /** Sube la imagen y devuelve su URL. */
    suspend fun uploadImage(image: PickedImage): String = remote.uploadImage(image)

    private fun catalogKey(kind: ProductCatalog) = "catalog:${kind.model}"

    private fun detailKey(idProduct: Int) = "product:$idProduct"

    private companion object {
        val CATALOG_SERIALIZER = ListSerializer(CatalogItemDto.serializer())
        val IDS_SERIALIZER = ListSerializer(Int.serializer())
        const val CATALOG_IDS_KEY = "catalog:shownIds"
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 50
        const val INFO_KEY = "inventory:info"
    }
}

/** Impuesto informativo de una unidad: la fórmula vive solo en `core/billing/Tax.kt`. */
fun unitTax(price: Double, taxType: String?, rate: Double): Double =
    lineTotals(quantity = 1.0, unitPrice = price, discount = 0.0, taxType = TaxType.fromApi(taxType), rate = rate).tax

private fun GroupedProductDto.toEntity(idMarket: Int, syncedAt: Long): ProductEntity? {
    val id = idProduct ?: return null
    return ProductEntity(
        idMarket = idMarket,
        idProduct = id,
        name = nombreProducto?.trim().orEmpty().ifBlank { "Producto $id" },
        quantity = cantidadAgrupada ?: 0.0,
        minPrice = minPrice ?: 0.0,
        maxPrice = maxPrice ?: minPrice ?: 0.0,
        unique = unique == true,
        brand = brand?.trim()?.takeIf { it.isNotEmpty() },
        syncedAt = syncedAt,
    )
}

private fun ProductForm.toEntity(idMarket: Int, idProduct: Int, syncedAt: Long) = ProductEntity(
    idMarket = idMarket,
    idProduct = idProduct,
    name = name.trim(),
    quantity = amount,
    minPrice = price,
    maxPrice = price,
    unique = false,
    brand = null,
    syncedAt = syncedAt,
)
