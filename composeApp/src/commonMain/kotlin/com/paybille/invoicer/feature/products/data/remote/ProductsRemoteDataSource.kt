package com.paybille.invoicer.feature.products.data.remote

import com.paybille.invoicer.core.network.LenientBooleanSerializer
import com.paybille.invoicer.core.network.LenientDoubleSerializer
import com.paybille.invoicer.core.network.LenientIntSerializer
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.decodeApi
import com.paybille.invoicer.core.network.decodeApiList
import com.paybille.invoicer.core.platform.PickedImage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Fila de `productinventory/allgrouped` (`repositories/productInventoryView.js`). */
@Serializable
data class GroupedProductDto(
    @Serializable(LenientIntSerializer::class) val idProduct: Int? = null,
    val nombreProducto: String? = null,
    @Serializable(LenientDoubleSerializer::class) val cantidadAgrupada: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val minPrice: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val maxPrice: Double? = null,
    @Serializable(LenientBooleanSerializer::class) val unique: Boolean? = null,
    @SerialName("Marca") val brand: String? = null,
)

/** Resumen del inventario (`productinventory/info`): lo calcula el servidor. */
@Serializable
data class InventoryInfoDto(
    @SerialName("CantidadDeProductos") @Serializable(LenientIntSerializer::class) val products: Int? = null,
    @SerialName("ProductosAgotados") @Serializable(LenientIntSerializer::class) val soldOut: Int? = null,
    @SerialName("ProductosCriticos") @Serializable(LenientIntSerializer::class) val critical: Int? = null,
    @SerialName("UnidadesDisponibles") @Serializable(LenientDoubleSerializer::class) val units: Double? = null,
    @SerialName("TotalInventario") @Serializable(LenientDoubleSerializer::class) val saleValue: Double? = null,
    @SerialName("CostoTotalInventario") @Serializable(LenientDoubleSerializer::class) val costValue: Double? = null,
)

/** La ficha (`products`): nombre, descripción y cómo se calcula su impuesto. */
@Serializable
data class ProductRecordDto(
    val id: Int,
    @SerialName("Name") val name: String? = null,
    @SerialName("Description") val description: String? = null,
    val taxType: String? = null,
    /** URL de la imagen (`image/upload`, o una que el POS pegó de Google). `STRING` en la tabla. */
    @SerialName("Image") val image: String? = null,
    /** `categories.id`; el POS guarda 0 cuando no hay. */
    @SerialName("IdCategory") @Serializable(LenientIntSerializer::class) val idCategory: Int? = null,
    /** Sale en el catálogo público (API `F3`). Por defecto no. */
    @SerialName("IndShowOnCatalog") @Serializable(LenientBooleanSerializer::class) val showOnCatalog: Boolean? = null,
)

/** Solo el id: para saber qué productos salen en el catálogo. */
@Serializable
data class ProductIdDto(val id: Int)

/** Una fila de `warehouse`: existencia, precio y código de barras (regla crítica 6). */
@Serializable
data class WarehouseRecordDto(
    val id: Int,
    @Serializable(LenientIntSerializer::class) val idProduct: Int? = null,
    @SerialName("Barcode") val barcode: String? = null,
    @SerialName("Price1") @Serializable(LenientDoubleSerializer::class) val price: Double? = null,
    @SerialName("Cost") @Serializable(LenientDoubleSerializer::class) val cost: Double? = null,
    @SerialName("Amount") @Serializable(LenientDoubleSerializer::class) val amount: Double? = null,
    @SerialName("MinAmountQty") @Serializable(LenientDoubleSerializer::class) val minAmount: Double? = null,
    @Serializable(LenientBooleanSerializer::class) val unique: Boolean? = null,
    @Serializable(LenientBooleanSerializer::class) val infinityAmount: Boolean? = null,
    @Serializable(LenientBooleanSerializer::class) val sold: Boolean? = null,
    // Marca y color van en `warehouse`, no en `products`: en un producto único cada unidad
    // tiene los suyos (`ProductQuickCreate.vue`). `Color` es el texto heredado; `IdColor`, el
    // catálogo. Se escriben los dos.
    @SerialName("IdBrand") @Serializable(LenientIntSerializer::class) val idBrand: Int? = null,
    @SerialName("IdColor") @Serializable(LenientIntSerializer::class) val idColor: Int? = null,
    @SerialName("Color") val color: String? = null,
)

/** Una fila de `categories`, `brands` o `colors`: los tres tienen la misma forma. */
@Serializable
data class CatalogItemDto(
    val id: Int,
    @SerialName("Name") val name: String = "",
)

/** Catálogos del alta de producto del POS (`commonData.js`). El nombre es el del modelo. */
enum class ProductCatalog(val model: String, val singular: String) {
    Categories("categories", "Categoría"),
    Brands("brands", "Marca"),
    Colors("colors", "Color"),
}

/** Rastro de `reportInventory`: quién movió la existencia, y de cuánto a cuánto. */
@Serializable
data class InventoryMoveDto(
    val id: Int,
    val createdAt: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Comentary") val comment: String? = null,
    @SerialName("User") val user: String? = null,
    @SerialName("Before") @Serializable(LenientDoubleSerializer::class) val before: Double? = null,
    @SerialName("After") @Serializable(LenientDoubleSerializer::class) val after: Double? = null,
)

/** Todo lo que pinta la ficha de un producto, guardado junto para verse sin red. */
@Serializable
data class ProductDetailDto(
    val product: ProductRecordDto,
    /** Filas de `warehouse` del producto (las vendidas de un producto único no cuentan). */
    val warehouses: List<WarehouseRecordDto> = emptyList(),
    val history: List<InventoryMoveDto> = emptyList(),
)

data class GroupedPage(val items: List<GroupedProductDto>, val hasNextPage: Boolean)

class ProductsRemoteDataSource(private val api: PayBilleApi) {

    /**
     * Inventario agrupado por nombre, como `inventario_general.vue`. Los `params` van como
     * arreglo `[{key, value}]`: el servidor los reparte por prefijo (`Product.` / `Warehouse.`) y
     * saca `IdMarket` del que no lo lleva.
     */
    suspend fun groupedPage(idMarket: Int, page: Int, pageSize: Int): GroupedPage {
        val result = api.getPage(
            "productinventory",
            buildJsonObject {
                put("is", true)
                put("IdMarket", idMarket)
                put("params", buildJsonArray { add(param("IdMarket", idMarket)) })
            },
            route = "allgrouped",
            page = page,
            pageSize = pageSize,
        )
        return GroupedPage(decodeApiList(GroupedProductDto.serializer(), result.items), result.hasNextPage)
    }

    /** El controlador lee `IdMarket` del cuerpo, no de `params`. */
    suspend fun info(idMarket: Int): InventoryInfoDto =
        decodeApi(InventoryInfoDto.serializer(), api.get("productinventory", buildJsonObject { put("IdMarket", idMarket) }, route = "info"))

    suspend fun product(id: Int): ProductRecordDto = decodeApi(ProductRecordDto.serializer(), api.getById("products", id))

    /** Filas de `warehouse` del producto que siguen en inventario (sin las unidades únicas vendidas). */
    suspend fun warehouses(idMarket: Int, idProduct: Int): List<WarehouseRecordDto> {
        val page = api.getGenericPage(
            "warehouse",
            buildJsonObject {
                put("idProduct", idProduct)
                put("IdMarket", idMarket)
            },
            page = 1,
            pageSize = 100,
        )
        return decodeApiList(WarehouseRecordDto.serializer(), page.items).filterNot { it.unique == true && it.sold == true }
    }

    /** Las últimas entradas y salidas (`id DESC`). */
    suspend fun history(idMarket: Int, idProduct: Int): List<InventoryMoveDto> {
        val page = api.getGenericPage(
            "reportInventory",
            buildJsonObject {
                put("IdProduct", idProduct)
                put("IdMarket", idMarket)
            },
            page = 1,
            pageSize = 20,
        )
        return decodeApiList(InventoryMoveDto.serializer(), page.items)
    }

    suspend fun createProduct(body: JsonObject): ProductRecordDto = decodeApi(ProductRecordDto.serializer(), api.postGeneric("products", body))

    suspend fun updateProduct(id: Int, body: JsonObject) {
        api.put("products", id, body)
    }

    suspend fun createWarehouse(body: JsonObject): WarehouseRecordDto =
        decodeApi(WarehouseRecordDto.serializer(), api.postGeneric("warehouse", body))

    suspend fun updateWarehouse(id: Int, body: JsonObject) {
        api.put("warehouse", id, body)
    }

    suspend fun createInventoryReport(body: JsonObject) {
        api.postGeneric("reportInventory", body)
    }

    /**
     * Las activas de la tienda, como `commonData.js → getCategories/getBrands/getColors`: el
     * servidor añade `IdMarket` a `params` por su cuenta. 500 por página, igual que el POS.
     */
    suspend fun catalog(kind: ProductCatalog): List<CatalogItemDto> {
        val data = api.getGeneric(kind.model, buildJsonObject { put("Active", true) }, page = 1, pageSize = CATALOG_PAGE)
        return decodeApiList(CatalogItemDto.serializer(), data as? JsonArray ?: JsonArray(emptyList()))
            .filter { it.name.isNotBlank() }
            .sortedBy { it.name.lowercase() }
    }

    /** Alta rápida, como `CreateCatalogItem.vue`: `{ Name, Active: true, IdMarket }`. */
    suspend fun createCatalogItem(kind: ProductCatalog, name: String, idMarket: Int): CatalogItemDto =
        decodeApi(
            CatalogItemDto.serializer(),
            api.postGeneric(
                kind.model,
                buildJsonObject {
                    put("Name", name)
                    put("Active", true)
                    put("IdMarket", idMarket)
                },
            ),
        )

    /**
     * Ids de los productos marcados para el catálogo público. La tabla `products` no es enorme,
     * pero se pide solo lo marcado y por páginas.
     */
    suspend fun catalogProductIds(idMarket: Int): List<Int> {
        val ids = mutableListOf<Int>()
        var page = 1
        do {
            val result = api.getGenericPage(
                "products",
                buildJsonObject {
                    put("IdMarket", idMarket)
                    put("IndShowOnCatalog", true)
                },
                page = page,
                pageSize = CATALOG_IDS_PAGE,
            )
            ids += decodeApiList(ProductIdDto.serializer(), result.items).map { it.id }
            page++
        } while (result.hasNextPage && page <= CATALOG_IDS_MAX_PAGES)
        return ids
    }

    suspend fun setShowOnCatalog(idProduct: Int, shown: Boolean) {
        api.put("products", idProduct, buildJsonObject { put("IndShowOnCatalog", shown) })
    }

    suspend fun uploadImage(image: PickedImage): String = api.uploadImage(image.bytes, image.mimeType, image.extension)

    private fun param(key: String, value: Int) = buildJsonObject {
        put("key", key)
        put("value", value)
    }
}

private const val CATALOG_PAGE = 500
private const val CATALOG_IDS_PAGE = 200
private const val CATALOG_IDS_MAX_PAGES = 25
