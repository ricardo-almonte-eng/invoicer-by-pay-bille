package com.paybille.invoicer.feature.sales.data.remote

import com.paybille.invoicer.core.network.ApiConfig
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.createHttpClient
import com.paybille.invoicer.feature.sales.domain.SalesFilter
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SalesRemoteDataSourceTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun source(body: String): SalesRemoteDataSource {
        val engine = MockEngine { request ->
            requests += request
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = PayBilleApi(createHttpClient({ "tk" }, engine), ApiConfig("https://api.test/ventex/api", "k"))
        return SalesRemoteDataSource(api)
    }

    @Test
    fun filtraPorTiendaEstatusYExcluyeGastos() = runTest {
        val remote = source("""{"data":[],"meta":{"hasNextPage":false,"totalCount":0}}""")

        remote.page(idMarket = 12, statuses = SalesFilter.Sales.statuses, page = 3, pageSize = 30)

        val request = requests.single()
        assertEquals("/ventex/api/generic/get/sales", request.url.encodedPath)
        assertEquals("3", request.url.parameters["page"])
        assertEquals("30", request.url.parameters["pageSize"])
        val params = Json.parseToJsonElement((request.body as TextContent).text).jsonObject["params"]!!.jsonObject
        assertEquals(12, params["IdMarket"]!!.jsonPrimitive.int)
        // null explícito = `Gasto IS NULL`. Si faltara la clave, los gastos saldrían como ventas.
        assertTrue("Gasto" in params)
        assertEquals(JsonNull, params["Gasto"])
        assertEquals(
            listOf("Complete", "Pagos Pendientes"),
            params["Status"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun leeTotalesComoStringYLaPaginacion() = runTest {
        val remote = source(
            """{"data":[{"id":9,"IdMarket":12,"Secuency":"0009","Client":"Tony Stark","Status":"Complete",""" +
                """"Total":"1250.50","Date":"05/05/2026 10:30 am","NCF":null,"createdAt":"2026-05-05T14:30:00.000Z",""" +
                """"Money":"1250.50","Gasto":null}],"meta":{"currentPage":1,"hasNextPage":true,"totalCount":31}}""",
        )

        val page = remote.page(12, SalesFilter.All.statuses, page = 1, pageSize = 30)

        assertTrue(page.hasNextPage)
        val sale = page.items.single()
        assertEquals(1250.50, sale.total)
        assertEquals("0009", sale.secuency)
        assertEquals("Complete", sale.status)
    }

    @Test
    fun sinMetaNoHayMasPaginas() = runTest {
        val remote = source("""{"data":[],"meta":null}""")

        assertFalse(remote.page(12, SalesFilter.Quotes.statuses, 1, 30).hasNextPage)
    }
}
