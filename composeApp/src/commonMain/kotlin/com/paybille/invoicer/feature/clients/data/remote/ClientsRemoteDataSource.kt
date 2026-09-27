package com.paybille.invoicer.feature.clients.data.remote

import com.paybille.invoicer.core.network.LenientBooleanSerializer
import com.paybille.invoicer.core.network.LenientDoubleSerializer
import com.paybille.invoicer.core.network.LenientIntSerializer
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.decodeApi
import com.paybille.invoicer.core.network.decodeApiList
import com.paybille.invoicer.feature.detail.data.remote.AccountDocDto
import com.paybille.invoicer.feature.sales.data.remote.SaleDto
import com.paybille.invoicer.feature.sales.domain.SalesFilter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Fila de `clients` con lo que la app enseña y edita (`domain/models/clients.js`). */
@Serializable
data class ClientRecordDto(
    val id: Int,
    @SerialName("IdMarket") @Serializable(LenientIntSerializer::class) val idMarket: Int? = null,
    @SerialName("FirstName") val firstName: String? = null,
    @SerialName("LastName") val lastName: String? = null,
    @SerialName("IdentifyType") val identifyType: String? = null,
    @SerialName("Identify") val identify: String? = null,
    @SerialName("Phone") val phone: String? = null,
    @SerialName("Whatsapp") @Serializable(LenientBooleanSerializer::class) val whatsapp: Boolean? = null,
    @SerialName("Email") val email: String? = null,
    @SerialName("Address") val address: String? = null,
    @SerialName("Discount") @Serializable(LenientIntSerializer::class) val discount: Int? = null,
    @SerialName("PayItbis") @Serializable(LenientBooleanSerializer::class) val payItbis: Boolean? = null,
)

/**
 * Saldo por cliente (`accountdocs/byparty`, la pantalla "Saldos pendientes" del POS).
 * `PartyKey` es el `IdClient` (0 si el documento se hizo a un nombre libre).
 */
@Serializable
data class PartyBalanceDto(
    @SerialName("PartyKey") @Serializable(LenientIntSerializer::class) val partyKey: Int? = null,
    @SerialName("PartyName") val partyName: String? = null,
    @SerialName("Docs") @Serializable(LenientIntSerializer::class) val docs: Int? = null,
    @SerialName("Total") @Serializable(LenientDoubleSerializer::class) val total: Double? = null,
    @SerialName("Paid") @Serializable(LenientDoubleSerializer::class) val paid: Double? = null,
    @SerialName("Balance") @Serializable(LenientDoubleSerializer::class) val balance: Double? = null,
    @SerialName("OldestDueDate") val oldestDueDate: String? = null,
    @SerialName("DaysOverdue") @Serializable(LenientIntSerializer::class) val daysOverdue: Int? = null,
)

/** Lo que guarda la ficha de un cliente para verse sin red: lo que debe y sus facturas. */
@Serializable
data class ClientActivityDto(
    val docs: List<AccountDocDto> = emptyList(),
    val sales: List<SaleDto> = emptyList(),
)

/** Datos del formulario, ya limpios (la cédula sin guiones). */
data class ClientForm(
    val firstName: String,
    val lastName: String?,
    val phone: String?,
    val whatsapp: Boolean,
    val email: String?,
    val address: String?,
    val identifyType: String?,
    val identify: String?,
    val discount: Int?,
    val payItbis: Boolean,
)

data class ClientsPage(val items: List<ClientRecordDto>, val hasNextPage: Boolean)

class ClientsRemoteDataSource(private val api: PayBilleApi) {

    /** `IdMarket` va siempre: la API genérica no filtra por la tienda del token. */
    suspend fun page(idMarket: Int, page: Int, pageSize: Int): ClientsPage {
        val result = api.getGenericPage("clients", buildJsonObject { put("IdMarket", idMarket) }, page = page, pageSize = pageSize)
        return ClientsPage(decodeApiList(ClientRecordDto.serializer(), result.items), result.hasNextPage)
    }

    suspend fun create(idMarket: Int, form: ClientForm): ClientRecordDto =
        decodeApi(ClientRecordDto.serializer(), api.postGeneric("clients", form.toBody(idMarket)))

    /** El `PUT` genérico no devuelve la fila: se arma con lo enviado. */
    suspend fun update(idMarket: Int, id: Int, form: ClientForm): ClientRecordDto {
        api.put("clients", id, form.toBody(idMarket))
        return form.toRecord(id, idMarket)
    }

    /** Clientes con saldo por cobrar, del que más debe al que menos. */
    suspend fun balances(page: Int, pageSize: Int): Pair<List<PartyBalanceDto>, Boolean> {
        val result = api.getPage(
            "accountdocs",
            buildJsonObject {
                put("Kind", "Cobrar")
                put("OnlyWithBalance", true)
            },
            route = "byparty",
            page = page,
            pageSize = pageSize,
        )
        return decodeApiList(PartyBalanceDto.serializer(), result.items) to result.hasNextPage
    }

    /** Documentos con saldo de un cliente (`accountdocs/get` acepta `IdClient`). */
    suspend fun openDocs(idClient: Int): List<AccountDocDto> {
        val result = api.getPage(
            "accountdocs",
            buildJsonObject {
                put("Kind", "Cobrar")
                put("IdClient", idClient)
                put("OnlyWithBalance", true)
            },
            route = "get",
            page = 1,
            pageSize = 50,
        )
        return decodeApiList(AccountDocDto.serializer(), result.items)
    }

    /** Sus últimas facturas y cotizaciones. `Gasto: null` obligatorio (guía 08 §7). */
    suspend fun sales(idMarket: Int, idClient: Int): List<SaleDto> {
        val params = buildJsonObject {
            put("IdMarket", idMarket)
            put("IdClient", idClient)
            put("Gasto", JsonNull)
            put("Status", JsonArray(SalesFilter.All.statuses.map(::JsonPrimitive)))
        }
        return decodeApiList(SaleDto.serializer(), api.getGenericPage("sales", params, page = 1, pageSize = 30).items)
    }
}

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun ClientForm.toBody(idMarket: Int): JsonObject = buildJsonObject {
    put("FirstName", firstName.trim())
    put("LastName", lastName.clean())
    put("Phone", phone.clean())
    put("Whatsapp", whatsapp)
    put("Email", email.clean())
    put("Address", address.clean())
    put("IdentifyType", identifyType.clean())
    put("Identify", identify.clean())
    put("Discount", discount ?: 0)
    // NOT NULL en la tabla.
    put("PayItbis", payItbis)
    put("IdMarket", idMarket)
}

private fun ClientForm.toRecord(id: Int, idMarket: Int) = ClientRecordDto(
    id = id,
    idMarket = idMarket,
    firstName = firstName.trim(),
    lastName = lastName.clean(),
    identifyType = identifyType.clean(),
    identify = identify.clean(),
    phone = phone.clean(),
    whatsapp = whatsapp,
    email = email.clean(),
    address = address.clean(),
    discount = discount,
    payItbis = payItbis,
)
