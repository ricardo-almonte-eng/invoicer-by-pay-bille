package com.paybille.invoicer.feature.sales.data.remote

import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.network.LenientDoubleSerializer
import com.paybille.invoicer.core.network.LenientIntSerializer
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.PayBilleJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Cabecera de `sales` (`PayBille_API/src/domain/models/sales.js`), solo lo que usa la lista. */
@Serializable
data class SaleDto(
    val id: Int,
    @SerialName("IdMarket") @Serializable(LenientIntSerializer::class) val idMarket: Int? = null,
    @SerialName("Secuency") val secuency: String? = null,
    @SerialName("Client") val client: String? = null,
    @SerialName("Status") val status: String = "",
    // DECIMAL(9,2): llega como string.
    @SerialName("Total") @Serializable(LenientDoubleSerializer::class) val total: Double? = null,
    // 'DD/MM/YYYY hh:mm am' — presentación, NO es una fecha.
    @SerialName("Date") val date: String? = null,
    @SerialName("NCF") val ncf: String? = null,
    val createdAt: String? = null,
)

data class SalesPage(val items: List<SaleDto>, val hasNextPage: Boolean)

class SalesRemoteDataSource(private val api: PayBilleApi) {

    /**
     * Ventas de la tienda con esos estatus, más nuevas primero (la API ordena por `id DESC`).
     *
     * `Gasto: null` es OBLIGATORIO: un gasto es una fila de `sales` con `Gasto = true`, la
     * columna admite nulos y `Gasto = false` no filtra nada (guía 08 §7).
     */
    suspend fun page(idMarket: Int, statuses: List<String>, page: Int, pageSize: Int): SalesPage {
        val params = buildJsonObject {
            put("IdMarket", idMarket)
            put("Gasto", JsonNull)
            // Un arreglo es `IN (…)` en la API genérica.
            put("Status", JsonArray(statuses.map(::JsonPrimitive)))
        }
        val result = api.getGenericPage("sales", params, page = page, pageSize = pageSize)
        val items = try {
            PayBilleJson.decodeFromJsonElement(ListSerializer(SaleDto.serializer()), result.items)
        } catch (e: SerializationException) {
            throw ApiException("El servidor respondió algo inesperado.", ApiException.Kind.Unexpected, cause = e)
        }
        return SalesPage(items, result.hasNextPage)
    }

    /** Una venta concreta (cualquier estatus), o null si no existe. */
    suspend fun one(idMarket: Int, saleId: Int): SaleDto? {
        val params = buildJsonObject {
            put("id", saleId)
            put("IdMarket", idMarket)
            put("Gasto", JsonNull)
        }
        val result = api.getGenericPage("sales", params, page = 1, pageSize = 1)
        return try {
            PayBilleJson.decodeFromJsonElement(ListSerializer(SaleDto.serializer()), result.items).firstOrNull()
        } catch (e: SerializationException) {
            throw ApiException("El servidor respondió algo inesperado.", ApiException.Kind.Unexpected, cause = e)
        }
    }
}
