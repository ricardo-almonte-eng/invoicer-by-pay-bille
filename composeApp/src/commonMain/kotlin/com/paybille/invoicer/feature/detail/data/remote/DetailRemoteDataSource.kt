package com.paybille.invoicer.feature.detail.data.remote

import com.paybille.invoicer.core.network.ApiException
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Cabecera de `sales` completa (lo que pinta el detalle). */
@Serializable
data class SaleHeaderDto(
    val id: Int,
    @SerialName("IdMarket") @Serializable(LenientIntSerializer::class) val idMarket: Int? = null,
    @SerialName("Secuency") val secuency: String? = null,
    @SerialName("IdClient") @Serializable(LenientIntSerializer::class) val idClient: Int? = null,
    @SerialName("Client") val client: String? = null,
    @SerialName("Status") val status: String = "",
    @SerialName("Date") val date: String? = null,
    val createdAt: String? = null,
    @SerialName("SubTotal") @Serializable(LenientDoubleSerializer::class) val subtotal: Double? = null,
    @SerialName("Tax") @Serializable(LenientDoubleSerializer::class) val tax: Double? = null,
    @SerialName("ClientDiscount") @Serializable(LenientDoubleSerializer::class) val discount: Double? = null,
    @SerialName("Total") @Serializable(LenientDoubleSerializer::class) val total: Double? = null,
    @SerialName("Money") @Serializable(LenientDoubleSerializer::class) val money: Double? = null,
    @SerialName("MoneyDeposit") @Serializable(LenientDoubleSerializer::class) val moneyDeposit: Double? = null,
    @SerialName("MoneyCredit") @Serializable(LenientDoubleSerializer::class) val moneyCredit: Double? = null,
    @SerialName("Change") @Serializable(LenientDoubleSerializer::class) val change: Double? = null,
    @SerialName("NCF") val ncf: String? = null,
    @SerialName("RNC") val rnc: String? = null,
    val taxType: String? = null,
)

@Serializable
data class SaleLineDto(
    val id: Int,
    @SerialName("Name") val name: String? = null,
    @SerialName("Amount") @Serializable(LenientDoubleSerializer::class) val amount: Double? = null,
    @SerialName("Price") @Serializable(LenientDoubleSerializer::class) val price: Double? = null,
    @SerialName("Discount") @Serializable(LenientDoubleSerializer::class) val discount: Double? = null,
    @SerialName("Tax") @Serializable(LenientDoubleSerializer::class) val tax: Double? = null,
    @SerialName("Total") @Serializable(LenientDoubleSerializer::class) val total: Double? = null,
    @Serializable(LenientBooleanSerializer::class) val isTrade: Boolean? = null,
)

/** Cabecera de `AccountDocuments` (también es la fila de `accountdocs/get`). */
@Serializable
data class AccountDocDto(
    val id: Int,
    @SerialName("IdSale") @Serializable(LenientIntSerializer::class) val idSale: Int? = null,
    @SerialName("Secuency") val secuency: String? = null,
    @SerialName("PartyName") val partyName: String? = null,
    @SerialName("Total") @Serializable(LenientDoubleSerializer::class) val total: Double? = null,
    @SerialName("Paid") @Serializable(LenientDoubleSerializer::class) val paid: Double? = null,
    @SerialName("Balance") @Serializable(LenientDoubleSerializer::class) val balance: Double? = null,
    @SerialName("DueDate") val dueDate: String? = null,
    @SerialName("NextDueDate") val nextDueDate: String? = null,
    @SerialName("Status") val status: String = "Pendiente",
    @SerialName("LateFeeAccrued") @Serializable(LenientDoubleSerializer::class) val lateFeeAccrued: Double? = null,
    @SerialName("LateFeePaid") @Serializable(LenientDoubleSerializer::class) val lateFeePaid: Double? = null,
)

@Serializable
data class AccountPaymentDto(
    val id: Int,
    @SerialName("PaymentDate") val paymentDate: String? = null,
    @SerialName("Method") val method: String = "Efectivo",
    @SerialName("Amount") @Serializable(LenientDoubleSerializer::class) val amount: Double? = null,
    @SerialName("LateFeeAmount") @Serializable(LenientDoubleSerializer::class) val lateFee: Double? = null,
    @SerialName("Reference") val reference: String? = null,
    @SerialName("Status") val status: String = "Aplicado",
)

@Serializable
data class InstallmentDto(
    @SerialName("Number") @Serializable(LenientIntSerializer::class) val number: Int? = null,
    @SerialName("DueDate") val dueDate: String? = null,
    @SerialName("TotalAmount") @Serializable(LenientDoubleSerializer::class) val total: Double? = null,
    @SerialName("BalanceRemaining") @Serializable(LenientDoubleSerializer::class) val balance: Double? = null,
    @SerialName("Status") val status: String = "Pendiente",
)

/** `{ Document, Items, Installments, Payments }` de `accountdocs/{id}` y `from-sale`. */
@Serializable
data class FullDocDto(
    @SerialName("Document") val document: AccountDocDto? = null,
    @SerialName("Installments") val installments: List<InstallmentDto> = emptyList(),
    @SerialName("Payments") val payments: List<AccountPaymentDto> = emptyList(),
)

data class ReceivablesPage(val rows: List<AccountDocDto>, val hasNextPage: Boolean)

class DetailRemoteDataSource(private val api: PayBilleApi) {

    /** Cabecera. `IdMarket` va siempre: la API genérica no filtra por la tienda del token. */
    suspend fun sale(idMarket: Int, saleId: Int): SaleHeaderDto? {
        val page = api.getGenericPage(
            "sales",
            buildJsonObject {
                put("id", saleId)
                put("IdMarket", idMarket)
            },
            page = 1,
            pageSize = 1,
        )
        return decode(ListSerializer(SaleHeaderDto.serializer()), page.items).firstOrNull()
    }

    suspend fun lines(saleId: Int): List<SaleLineDto> {
        val page = api.getGenericPage("salesProducts", buildJsonObject { put("IdSale", saleId) }, page = 1, pageSize = 500)
        return decode(ListSerializer(SaleLineDto.serializer()), page.items).sortedBy { it.id }
    }

    /**
     * Documento de cuentas por cobrar de una venta **con saldo**. `from-sale` es idempotente
     * (índice único sobre `IdSale`): si ya existe lo devuelve, si no lo crea — igual que el
     * backfill del POS para ventas viejas. NO se llama para ventas pagadas: crearía un
     * documento que no hace falta.
     */
    suspend fun receivableForSale(saleId: Int): FullDocDto =
        decode(FullDocDto.serializer(), api.post("accountdocs", buildJsonObject { put("IdSale", saleId) }, route = "from-sale"))

    suspend fun receivable(docId: Int): FullDocDto = decode(FullDocDto.serializer(), api.getBusiness("accountdocs/$docId"))

    /**
     * Registra un abono. El servidor recalcula saldo, pasa la venta a `Complete` al saldarla
     * y, si hay cuenta, crea él el movimiento: la app NO crea movimiento (anti doble conteo).
     */
    suspend fun addPayment(
        docId: Int,
        amount: Double,
        method: String,
        paymentDateIso: String,
        idCuenta: Int?,
        reference: String?,
    ) {
        api.post(
            "accountdocs",
            buildJsonObject {
                put("Amount", amount)
                put("LateFeeAmount", 0.0)
                put("Method", method)
                put("PaymentDate", paymentDateIso)
                put("IdCuenta", idCuenta)
                put("Reference", reference?.trim()?.takeIf { it.isNotEmpty() })
            },
            route = "$docId/payments",
        )
    }

    /** Cuentas por cobrar con saldo de la tienda, ordenadas por vencimiento. */
    suspend fun openReceivables(page: Int, pageSize: Int): ReceivablesPage {
        val result = api.getPage(
            "accountdocs",
            buildJsonObject {
                put("Kind", "Cobrar")
                put("OnlyWithBalance", true)
            },
            route = "get",
            page = page,
            pageSize = pageSize,
        )
        return ReceivablesPage(decode(ListSerializer(AccountDocDto.serializer()), result.items), result.hasNextPage)
    }

    private fun <T> decode(serializer: KSerializer<T>, data: JsonElement): T = try {
        PayBilleJson.decodeFromJsonElement(serializer, data)
    } catch (e: SerializationException) {
        throw ApiException("El servidor respondió algo inesperado.", ApiException.Kind.Unexpected, cause = e)
    } catch (e: IllegalArgumentException) {
        throw ApiException("El servidor respondió algo inesperado.", ApiException.Kind.Unexpected, cause = e)
    }
}
