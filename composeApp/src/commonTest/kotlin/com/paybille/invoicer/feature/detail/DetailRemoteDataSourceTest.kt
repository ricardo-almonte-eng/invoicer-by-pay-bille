package com.paybille.invoicer.feature.detail

import com.paybille.invoicer.core.network.ApiConfig
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.createHttpClient
import com.paybille.invoicer.feature.detail.data.remote.DetailRemoteDataSource
import com.paybille.invoicer.feature.detail.data.toDomain
import com.paybille.invoicer.feature.detail.domain.PaymentMethod
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetailRemoteDataSourceTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun source(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): DetailRemoteDataSource {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return DetailRemoteDataSource(PayBilleApi(createHttpClient({ "tk" }, engine), ApiConfig("https://api.test/ventex/api", "k")))
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun elDocumentoDeUnaVentaSeBuscaConFromSaleYTraeAbonos() = runTest {
        val remote = source {
            json(
                """{"data":{"Document":{"id":55,"IdSale":100,"Total":"295.00","Paid":"100.00","Balance":"195.00",""" +
                    """"DueDate":"2026-05-20","NextDueDate":null,"Status":"Parcial","LateFeeAccrued":"0.00","LateFeePaid":"0.00"},""" +
                    """"Items":[],"Installments":[],"Payments":[{"id":1,"PaymentDate":"2026-05-06T02:30:00.000Z",""" +
                    """"Method":"Deposito","Amount":"100.00","LateFeeAmount":"0.00","Status":"Aplicado"}],"Created":false}}""",
            )
        }

        val receivable = remote.receivableForSale(100).toDomain()!!

        assertEquals("/ventex/api/accountdocs/from-sale", requests.single().url.encodedPath)
        assertEquals(55, receivable.docId)
        assertEquals(195.0, receivable.balance)
        assertEquals("2026-05-20", receivable.effectiveDueDate)
        assertTrue(receivable.isOpen)
        assertEquals("Deposito", receivable.payments.single().method)
    }

    @Test
    fun elAbonoPorTransferenciaSeMandaComoDepositoYSinMovimientoPropio() = runTest {
        val remote = source { json("""{"data":{"ok":true}}""") }

        remote.addPayment(
            docId = 55,
            amount = 195.0,
            method = PaymentMethod.Transfer.apiValue,
            paymentDateIso = "2026-05-10T16:00:00Z",
            idCuenta = 8,
            reference = "  ",
        )

        val request = requests.single()
        assertEquals("/ventex/api/accountdocs/55/payments", request.url.encodedPath)
        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        // `Transferencia` iría a la columna de tarjeta de la venta (CAMPO_VENTA del backend).
        assertEquals("Deposito", body["Method"]!!.jsonPrimitive.content)
        assertEquals(195.0, body["Amount"]!!.jsonPrimitive.double)
        assertEquals(JsonNull, body["Reference"])
        // Nunca Paid ni Balance: el saldo lo escribe solo el servidor.
        assertTrue("Paid" !in body && "Balance" !in body)
        // Una sola llamada: el movimiento de cuenta lo crea el servidor.
        assertTrue(requests.none { it.url.encodedPath.contains("cuentas") })
    }

    @Test
    fun cuentasPorCobrarConSaldoFiltranPorTipo() = runTest {
        val remote = source {
            json(
                """{"data":[{"id":55,"IdSale":100,"Secuency":"CO1","PartyName":"Tony","Balance":"195.00",""" +
                    """"DueDate":"2026-05-20","NextDueDate":"2026-05-15","Status":"Parcial"}],"meta":{"hasNextPage":false}}""",
            )
        }

        val page = remote.openReceivables(page = 1, pageSize = 100)

        val body = Json.parseToJsonElement((requests.single().body as TextContent).text).jsonObject
        assertEquals("Cobrar", body["Kind"]!!.jsonPrimitive.content)
        assertEquals("true", body["OnlyWithBalance"]!!.jsonPrimitive.content)
        assertEquals("2026-05-15", page.rows.single().nextDueDate)
    }
}
