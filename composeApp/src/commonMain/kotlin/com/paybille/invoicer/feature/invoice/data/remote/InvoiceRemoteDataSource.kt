package com.paybille.invoicer.feature.invoice.data.remote

import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.network.LenientBooleanSerializer
import com.paybille.invoicer.core.network.LenientDoubleSerializer
import com.paybille.invoicer.core.network.LenientIntSerializer
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.PayBilleJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

@Serializable
data class CreatedDto(val id: Int)

@Serializable
data class SaleLineRefDto(
    val id: Int,
    @Serializable(LenientIntSerializer::class) val idWarehouse: Int? = null,
)

/** Lo que hace falta de `warehouse` para descontar la venta (lo mismo que lee el POS). */
@Serializable
data class WarehouseDto(
    val id: Int,
    @Serializable(LenientIntSerializer::class) val idProduct: Int? = null,
    @SerialName("Barcode") val barcode: String? = null,
    @SerialName("Amount") @Serializable(LenientDoubleSerializer::class) val amount: Double? = null,
    @Serializable(LenientBooleanSerializer::class) val unique: Boolean? = null,
    @Serializable(LenientBooleanSerializer::class) val infinityAmount: Boolean? = null,
    @SerialName("IdGuarantee") @Serializable(LenientIntSerializer::class) val idGuarantee: Int? = null,
    @SerialName("GuaranteeDuration") @Serializable(LenientIntSerializer::class) val guaranteeDuration: Int? = null,
)

/**
 * Las llamadas que cierran una venta, una por paso. Réplica de
 * `completeOrder.vue → saveSold()` del POS, sin la "venta base" (guía 02).
 */
class InvoiceRemoteDataSource(private val api: PayBilleApi) {

    /** `POST invoiceSecuency/next/{IdMarket}` → `"CO202609160000123"`. */
    suspend fun nextSequence(idMarket: Int): String {
        val data = api.post("invoiceSecuency", JsonObject(emptyMap()), route = "next/$idMarket")
        return (data as? JsonObject)?.get("Sequence")?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: throw unexpected()
    }

    /**
     * `POST nfc/verify`. Responde `{ newNFC: true }` si hay rango activo, o el texto del
     * problema, a veces dentro de `newNFC` y a veces como texto plano (`controllers/reports.js`).
     */
    suspend fun verifyNcf(idMarket: Int, type: String) {
        val data = api.post("nfc", ncfBody(idMarket, type), route = "verify")
        val value = (data as? JsonObject)?.get("newNFC") as? JsonPrimitive
        if (value?.booleanOrNull == true) return
        throw ApiException(ncfMessage(value?.contentOrNull ?: (data as? JsonPrimitive)?.contentOrNull), ApiException.Kind.Server)
    }

    /**
     * `POST nfc/getNextNFC`. **Consume** un número: no se devuelve aunque la factura falle.
     *
     * El backend mete sus errores en `newNFC` como si fueran un NCF, así que se valida la
     * forma: el tipo pedido seguido solo de dígitos. (Su aviso de "rango agotado" nunca
     * salta: compara contra `rango.Final`, que no existe.)
     */
    suspend fun nextNcf(idMarket: Int, type: String): String {
        val data = api.post("nfc", ncfBody(idMarket, type), route = "getNextNFC")
        val value = ((data as? JsonObject)?.get("newNFC") as? JsonPrimitive)?.contentOrNull
            ?: (data as? JsonPrimitive)?.contentOrNull
        if (value != null && isNcf(value, type)) return value
        throw ApiException(ncfMessage(value), ApiException.Kind.Server)
    }

    /** Para no duplicar una venta cuyo alta llegó al servidor pero cuya respuesta se perdió. */
    suspend fun findSaleIdBySequence(idMarket: Int, sequence: String): Int? {
        val page = api.getGenericPage(
            "sales",
            buildJsonObject {
                put("IdMarket", idMarket)
                put("Secuency", sequence)
            },
            page = 1,
            pageSize = 1,
        )
        return (page.items.firstOrNull() as? JsonObject)?.get("id")?.let { (it as? JsonPrimitive)?.intOrNull }
    }

    suspend fun createSale(body: JsonObject): Int = created(api.postGeneric("sales", body))

    /** Líneas ya creadas de una venta, en orden de alta. */
    suspend fun saleLines(idSale: Int): List<SaleLineRefDto> {
        val page = api.getGenericPage(
            "salesProducts",
            buildJsonObject { put("IdSale", idSale) },
            page = 1,
            pageSize = 500,
        )
        return decode(ListSerializer(SaleLineRefDto.serializer()), page.items).sortedBy { it.id }
    }

    suspend fun createSaleLine(body: JsonObject): Int = created(api.postGeneric("salesProducts", body))

    suspend fun warehouse(id: Int): WarehouseDto = decode(WarehouseDto.serializer(), api.getById("warehouse", id))

    suspend fun updateWarehouse(id: Int, body: JsonObject) {
        api.put("warehouse", id, body)
    }

    suspend fun createInventoryReport(body: JsonObject) {
        api.postGeneric("reportInventory", body)
    }

    suspend fun createGuarantee(body: JsonObject) {
        api.postGeneric("guaranteeProductSale", body)
    }

    /** Documento espejo en el libro de cuentas. **Idempotente** (índice único sobre `IdSale`). */
    suspend fun accountDocFromSale(idSale: Int, dueDate: String?) {
        api.post(
            "accountdocs",
            buildJsonObject {
                put("IdSale", idSale)
                put("DueDate", dueDate)
            },
            route = "from-sale",
        )
    }

    suspend fun createAccountMovement(body: JsonObject) {
        api.post("cuentas", body, route = "movimientos")
    }

    private fun ncfBody(idMarket: Int, type: String) = buildJsonObject {
        put("IdMarket", idMarket)
        put("tipoNCF", type)
    }

    private fun ncfMessage(raw: String?): String = when {
        raw.isNullOrBlank() -> "No se pudo obtener el comprobante fiscal (NCF)."
        raw.contains("No hay rangos") -> "No hay comprobantes fiscales disponibles de este tipo. Agrégalos en el POS."
        else -> raw
    }

    private fun created(data: JsonElement): Int = decode(CreatedDto.serializer(), data).id

    private fun <T> decode(serializer: KSerializer<T>, data: JsonElement): T = try {
        PayBilleJson.decodeFromJsonElement(serializer, data)
    } catch (e: SerializationException) {
        throw unexpected(e)
    } catch (e: IllegalArgumentException) {
        throw unexpected(e)
    }

    private fun unexpected(cause: Throwable? = null) =
        ApiException("El servidor respondió algo inesperado.", ApiException.Kind.Unexpected, cause = cause)

    companion object {
        fun isNcf(value: String, type: String): Boolean {
            val digits = value.removePrefix(type)
            return value.startsWith(type) && digits.isNotEmpty() && digits.all { it.isDigit() }
        }
    }
}
