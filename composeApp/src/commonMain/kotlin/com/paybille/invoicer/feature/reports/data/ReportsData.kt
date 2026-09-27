package com.paybille.invoicer.feature.reports.data

import com.paybille.invoicer.core.database.Cached
import com.paybille.invoicer.core.database.PayloadCache
import com.paybille.invoicer.core.format.DateRange
import com.paybille.invoicer.core.network.LenientDoubleSerializer
import com.paybille.invoicer.core.network.LenientIntSerializer
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.decodeApi
import com.paybille.invoicer.core.network.decodeApiList
import com.paybille.invoicer.feature.products.data.remote.InventoryMoveDto
import com.paybille.invoicer.feature.sales.data.remote.SaleDto
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Totales de "Ventas por fecha" (`report/totals/no`, `repositories/sales.js → getSaleByRangeDate`). Solo ventas `Complete`. */
@Serializable
data class SalesTotalsDto(
    @Serializable(LenientDoubleSerializer::class) val totalVendido: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalImpuestos: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val subtotal: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalDescuento: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalGastos: Double? = null,
    @Serializable(LenientIntSerializer::class) val cantidadTransacciones: Int? = null,
    @Serializable(LenientDoubleSerializer::class) val totalEfectivo: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalEfectivoRetornado: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalDeposito: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalCredito: Double? = null,
)

@Serializable
data class SalesReportDto(
    val totals: SalesTotalsDto = SalesTotalsDto(),
    val sales: List<SaleDto> = emptyList(),
    /** Hay más ventas que las descargadas ([MAX_SALES]). */
    val truncated: Boolean = false,
)

/** Totales de "Productos vendidos" (`productinventory/report/totalProductCost`). */
@Serializable
data class ProductCostDto(
    @Serializable(LenientDoubleSerializer::class) val totalPrice: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalCost: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalDiscount: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalProfit: Double? = null,
)

/** Fila de `productinventory/report/salesProductsGrouped`. */
@Serializable
data class SoldProductDto(
    @SerialName("IdProduct") @Serializable(LenientIntSerializer::class) val idProduct: Int? = null,
    @SerialName("Name") val name: String? = null,
    @Serializable(LenientDoubleSerializer::class) val totalAmount: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalPrice: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalCost: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalDiscount: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val profit: Double? = null,
)

@Serializable
data class SoldProductsReportDto(
    val totals: ProductCostDto = ProductCostDto(),
    val products: List<SoldProductDto> = emptyList(),
)

@Serializable
data class InventoryHistoryDto(val moves: List<InventoryMoveDto> = emptyList(), val truncated: Boolean = false)

/**
 * Los reportes del POS (carpeta `pages/reportes`). ⚠️ Los de ventas recorren `params` con `for…of`
 * (`repositories/sales.js`): tienen que ir como **arreglo** `[{key, value}]`; un objeto hace
 * fallar al servidor.
 */
class ReportsRemoteDataSource(private val api: PayBilleApi) {

    suspend fun sales(idMarket: Int, range: DateRange): SalesReportDto {
        val totals = decodeApi(
            SalesTotalsDto.serializer(),
            api.get("report", rangeBody(idMarket, range), route = "totals/no").orEmptyObject(),
        )
        // `Gasto: null` → `Gasto IS NULL`: la lista es de ventas, sin gastos (guía 08 §7).
        val page = api.getPage("report", rangeBody(idMarket, range, withoutExpenses = true), route = "no", page = 1, pageSize = MAX_SALES)
        return SalesReportDto(totals, decodeApiList(SaleDto.serializer(), page.items), truncated = page.hasNextPage)
    }

    suspend fun soldProducts(idMarket: Int, range: DateRange): SoldProductsReportDto {
        val totals = decodeApi(
            ProductCostDto.serializer(),
            api.get("productinventory", rangeBody(idMarket, range), route = "report/totalProductCost").orEmptyObject(),
        )
        val page = api.getPage("productinventory", rangeBody(idMarket, range), route = "report/salesProductsGrouped", page = 1, pageSize = 500)
        return SoldProductsReportDto(totals, mergeByProduct(decodeApiList(SoldProductDto.serializer(), page.items)))
    }

    suspend fun inventoryHistory(idMarket: Int, range: DateRange): InventoryHistoryDto {
        // `repositories/productInventoryView.js → getReport` saca `IdMarket` de `params`.
        val page = api.getPage("productinventory", rangeBody(idMarket, range), route = "report/history", page = 1, pageSize = MAX_MOVES)
        return InventoryHistoryDto(decodeApiList(InventoryMoveDto.serializer(), page.items), truncated = page.hasNextPage)
    }

    private fun rangeBody(idMarket: Int, range: DateRange, withoutExpenses: Boolean = false) = buildJsonObject {
        put("startDate", range.start)
        put("endDate", range.end)
        put(
            "params",
            buildJsonArray {
                add(buildJsonObject { put("key", "IdMarket"); put("value", idMarket) })
                if (withoutExpenses) add(buildJsonObject { put("key", "Gasto"); put("value", JsonNull) })
            },
        )
    }

    /** `{ message: "No se encontraron datos" }` o nada: totales en cero. */
    private fun JsonElement.orEmptyObject(): JsonElement =
        if (this is JsonObject && !containsKey("message")) this else JsonObject(emptyMap())

    companion object {
        const val MAX_SALES = 200
        const val MAX_MOVES = 200
    }
}

/**
 * El servidor agrupa también por estatus y precio: un mismo producto sale en varias filas
 * (pagada y pendiente, o vendida a dos precios). Aquí se suman en una, del más vendido al menos.
 */
fun mergeByProduct(rows: List<SoldProductDto>): List<SoldProductDto> =
    rows.groupBy { it.idProduct?.toString() ?: it.name.orEmpty() }
        .map { (_, group) ->
            SoldProductDto(
                idProduct = group.first().idProduct,
                name = group.first().name,
                totalAmount = group.sumOf { it.totalAmount ?: 0.0 },
                totalPrice = group.sumOf { it.totalPrice ?: 0.0 },
                totalCost = group.sumOf { it.totalCost ?: 0.0 },
                totalDiscount = group.sumOf { it.totalDiscount ?: 0.0 },
                profit = group.sumOf { it.profit ?: 0.0 },
            )
        }
        .sortedByDescending { it.totalAmount ?: 0.0 }

/** Cada reporte guarda su último resultado por rango, para verse sin red. */
class ReportsRepository(
    private val remote: ReportsRemoteDataSource,
    private val cache: PayloadCache,
) {
    fun observeSales(idMarket: Int, range: DateRange): Flow<Cached<SalesReportDto>?> =
        cache.observe(idMarket, key("sales", range), SalesReportDto.serializer())

    fun observeSoldProducts(idMarket: Int, range: DateRange): Flow<Cached<SoldProductsReportDto>?> =
        cache.observe(idMarket, key("products", range), SoldProductsReportDto.serializer())

    fun observeInventoryHistory(idMarket: Int, range: DateRange): Flow<Cached<InventoryHistoryDto>?> =
        cache.observe(idMarket, key("inventory", range), InventoryHistoryDto.serializer())

    suspend fun refreshSales(idMarket: Int, range: DateRange) =
        put(idMarket, key("sales", range), SalesReportDto.serializer(), remote.sales(idMarket, range))

    suspend fun refreshSoldProducts(idMarket: Int, range: DateRange) =
        put(idMarket, key("products", range), SoldProductsReportDto.serializer(), remote.soldProducts(idMarket, range))

    suspend fun refreshInventoryHistory(idMarket: Int, range: DateRange) =
        put(idMarket, key("inventory", range), InventoryHistoryDto.serializer(), remote.inventoryHistory(idMarket, range))

    private suspend fun <T> put(idMarket: Int, key: String, serializer: KSerializer<T>, value: T) = cache.put(idMarket, key, serializer, value)

    private fun key(report: String, range: DateRange) = "report:$report:${range.key}"
}
