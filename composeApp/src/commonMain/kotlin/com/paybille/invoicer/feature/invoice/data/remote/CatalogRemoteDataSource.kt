package com.paybille.invoicer.feature.invoice.data.remote

import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.network.ApiPage
import com.paybille.invoicer.core.network.LenientBooleanSerializer
import com.paybille.invoicer.core.network.LenientDoubleSerializer
import com.paybille.invoicer.core.network.LenientIntSerializer
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.PayBilleJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Producto para vender: una fila de `warehouse` con su `product` (`productinventory/sales`). */
@Serializable
data class SaleableItemDto(
    val id: Int,
    @Serializable(LenientIntSerializer::class) val idProduct: Int? = null,
    @SerialName("Barcode") val barcode: String? = null,
    @SerialName("Price1") @Serializable(LenientDoubleSerializer::class) val price: Double? = null,
    @SerialName("Amount") @Serializable(LenientDoubleSerializer::class) val amount: Double? = null,
    @Serializable(LenientBooleanSerializer::class) val unique: Boolean? = null,
    @Serializable(LenientBooleanSerializer::class) val infinityAmount: Boolean? = null,
    @SerialName("TypeSize") val typeSize: String? = null,
    val product: ProductRefDto? = null,
) {
    // La API pide el atributo `name` en minúscula; el POS lee las dos grafías.
    val displayName: String get() = product?.name ?: product?.nameUpper ?: "Producto"
}

@Serializable
data class ProductRefDto(
    val id: Int? = null,
    val name: String? = null,
    @SerialName("Name") val nameUpper: String? = null,
)

@Serializable
data class ClientDto(
    val id: Int,
    @SerialName("FirstName") val firstName: String? = null,
    @SerialName("LastName") val lastName: String? = null,
    @SerialName("Phone") val phone: String? = null,
    @SerialName("Identify") val identify: String? = null,
) {
    val fullName: String get() = listOfNotNull(firstName, lastName).joinToString(" ").trim()
}

@Serializable
data class AccountDto(
    val id: Int,
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String? = null,
    @SerialName("BankName") val bankName: String? = null,
)

data class CatalogPage<T>(val items: List<T>, val hasNextPage: Boolean)

/** Lo que el editor consulta mientras se escribe la factura. */
class CatalogRemoteDataSource(private val api: PayBilleApi) {

    /**
     * Mismo criterio que la pantalla de ventas del POS (`pages/ventas.vue → getProducts`):
     * primero por código de barras (contiene) y, si no hay nada, por nombre.
     *
     * ⚠️ El backend busca el nombre por PREFIJO (`name LIKE 'texto%'`): "cola" no encuentra
     * "Coca Cola". Ya excluye lo vendido (`sold`), lo no vendible y las piezas de taller.
     */
    suspend fun searchProducts(idMarket: Int, query: String, page: Int, pageSize: Int): CatalogPage<SaleableItemDto> {
        val text = query.trim()
        fun body(key: String?) = buildJsonObject {
            put("IdMarket", idMarket)
            if (key != null) put(key, text)
        }
        if (text.isEmpty()) return productsPage(body(null), page, pageSize)

        val byBarcode = productsPage(body("barcode"), page, pageSize)
        if (byBarcode.items.isNotEmpty() || page > 1) return byBarcode
        return productsPage(body("name"), page, pageSize)
    }

    private suspend fun productsPage(body: JsonObject, page: Int, pageSize: Int) =
        api.getPage("productinventory", body, route = "sales", page = page, pageSize = pageSize)
            .decode(SaleableItemDto.serializer())

    /** Busca por nombre, apellido, teléfono o cédula en una sola caja de texto. */
    suspend fun searchClients(idMarket: Int, query: String, page: Int, pageSize: Int): CatalogPage<ClientDto> {
        val text = query.trim()
        val extra = if (text.isEmpty()) {
            JsonObject(emptyMap())
        } else {
            buildJsonObject {
                // Búsqueda OR sobre varios campos (`repositories/generic.js → get`).
                put("likeOrParams", JsonArray(CLIENT_SEARCH_FIELDS.map(::JsonPrimitive)))
                put("likeOrValue", text)
            }
        }
        return api.getGenericPage(
            "clients",
            buildJsonObject { put("IdMarket", idMarket) },
            page = page,
            pageSize = pageSize,
            extra = extra,
        ).decode(ClientDto.serializer())
    }

    /** Cuentas de dinero activas de la tienda. */
    suspend fun accounts(idMarket: Int): List<AccountDto> =
        api.getGenericPage(
            "cuentas",
            buildJsonObject {
                put("IdMarket", idMarket)
                put("Active", true)
            },
            page = 1,
            pageSize = 100,
        ).decode(AccountDto.serializer()).items

    private fun <T> ApiPage.decode(serializer: KSerializer<T>): CatalogPage<T> = try {
        CatalogPage(PayBilleJson.decodeFromJsonElement(ListSerializer(serializer), items), hasNextPage)
    } catch (e: SerializationException) {
        throw ApiException("El servidor respondió algo inesperado.", ApiException.Kind.Unexpected, cause = e)
    }

    private companion object {
        val CLIENT_SEARCH_FIELDS = listOf("FirstName", "LastName", "Phone", "Identify")
    }
}
