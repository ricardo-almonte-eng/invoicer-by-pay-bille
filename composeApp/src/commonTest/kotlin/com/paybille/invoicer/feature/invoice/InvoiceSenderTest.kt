package com.paybille.invoicer.feature.invoice

import com.paybille.invoicer.core.billing.Currency
import com.paybille.invoicer.core.network.ApiConfig
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.createHttpClient
import com.paybille.invoicer.feature.invoice.data.InvoiceRepository
import com.paybille.invoicer.feature.invoice.data.InvoiceSender
import com.paybille.invoicer.feature.invoice.data.local.OutboxState
import com.paybille.invoicer.feature.invoice.data.remote.InvoiceRemoteDataSource
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.domain.DraftAccount
import com.paybille.invoicer.feature.invoice.domain.DraftClient
import com.paybille.invoicer.feature.invoice.domain.DraftLine
import com.paybille.invoicer.feature.invoice.domain.DraftPayment
import com.paybille.invoicer.feature.invoice.domain.InvoiceDraft
import com.paybille.invoicer.feature.invoice.domain.NcfType
import com.paybille.invoicer.feature.invoice.domain.PayTo
import com.paybille.invoicer.feature.sales.data.SalesRepository
import com.paybille.invoicer.feature.sales.data.remote.SalesRemoteDataSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El envío de la cola contra una API simulada que se comporta como `PayBille_API`.
 * Lo importante: que el POS reciba lo mismo que manda él, y que un reintento nunca
 * duplique ventas, líneas, NCF ni descuentos de inventario.
 */
class InvoiceSenderTest {

    private class FakeServer {
        val requests = mutableListOf<Pair<HttpRequestData, JsonObject?>>()
        val sales = mutableListOf<JsonObject>()
        val lines = mutableListOf<JsonObject>()
        val warehouseAmount = mutableMapOf(501 to 10.0, 502 to 0.0)
        var sequenceCounter = 0
        var ncfAvailable = true

        /** Ruta que falla por red la próxima vez (y solo esa). */
        var dropOnce: String? = null

        /** Si es true, el alta de la venta llega al servidor pero su respuesta se pierde. */
        var loseSaleResponseOnce = false

        fun bodies(path: String, method: HttpMethod = HttpMethod.Post) =
            requests.filter { it.first.url.encodedPath == path && it.first.method == method }.mapNotNull { it.second }
    }

    private val server = FakeServer()
    private val invoiceDao = FakeInvoiceDao()
    private val salesDao = FakeSalesDao()
    private val createdAt = 1778034600000L // 2026-05-06T02:30:00Z = 5 may. 10:30 pm en Santo Domingo

    private val engine = MockEngine { request ->
        val body = (request.body as? TextContent)?.text?.let { Json.parseToJsonElement(it).jsonObject }
        server.requests += request to body
        val path = request.url.encodedPath.removePrefix("/ventex/api")
        if (server.dropOnce == path) {
            server.dropOnce = null
            throw kotlinx.io.IOException("sin red")
        }
        fun ok(json: String) = respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        when {
            path == "/invoiceSecuency/next/12" -> ok("""{"data":{"Sequence":"CO2026050500000${++server.sequenceCounter}"}}""")
            path == "/nfc/verify" -> if (server.ncfAvailable) ok("""{"data":{"newNFC":true}}""") else
                ok("""{"data":{"newNFC":"No hay rangos disponibles para este tipo de NCF."}}""")
            path == "/nfc/getNextNFC" -> ok("""{"data":{"newNFC":"B0200000000045"}}""")
            path == "/generic/get/sales" -> {
                val seq = body!!["params"]!!.jsonObject["Secuency"]!!.jsonPrimitive.content
                val found = server.sales.withIndex().firstOrNull { it.value["Secuency"]?.jsonPrimitive?.content == seq }
                ok(if (found == null) """{"data":[],"meta":{"hasNextPage":false}}""" else """{"data":[{"id":${100 + found.index}}]}""")
            }
            path == "/generic/sales" -> {
                server.sales += body!!
                if (server.loseSaleResponseOnce) {
                    server.loseSaleResponseOnce = false
                    throw kotlinx.io.IOException("respuesta perdida")
                }
                ok("""{"data":{"id":${99 + server.sales.size}}}""")
            }
            path == "/generic/get/salesProducts" -> {
                val idSale = body!!["params"]!!.jsonObject["IdSale"]!!.jsonPrimitive.int
                val ids = server.lines.withIndex().filter { it.value["IdSale"]!!.jsonPrimitive.int == idSale }
                    .joinToString(",") { """{"id":${900 + it.index}}""" }
                ok("""{"data":[$ids]}""")
            }
            path == "/generic/salesProducts" -> {
                server.lines += body!!
                ok("""{"data":{"id":${899 + server.lines.size}}}""")
            }
            path.startsWith("/generic/warehouse/") && request.method == HttpMethod.Get -> {
                val id = path.substringAfterLast('/').toInt()
                ok("""{"data":{"id":$id,"idProduct":${id - 400},"Barcode":"B$id","Amount":"${server.warehouseAmount[id]}","unique":false,"infinityAmount":false,"IdGuarantee":null}}""")
            }
            path.startsWith("/generic/warehouse/") && request.method == HttpMethod.Put -> {
                val id = path.substringAfterLast('/').toInt()
                server.warehouseAmount[id] = body!!["Amount"]!!.jsonPrimitive.double
                ok("""{"data":{"id":$id}}""")
            }
            else -> ok("""{"data":{"ok":true}}""")
        }
    }

    private val api = PayBilleApi(createHttpClient({ "tk" }, engine), ApiConfig("https://api.test/ventex/api", "k"))
    private val repository = InvoiceRepository(invoiceDao, now = { createdAt })
    private val salesRepository = SalesRepository(salesDao, SalesRemoteDataSource(api), now = { createdAt })
    private val sender = InvoiceSender(
        dao = invoiceDao,
        remote = InvoiceRemoteDataSource(api),
        sales = salesRepository,
        currentSession = { testSession() },
    )

    private fun line(idWarehouse: Int, quantity: Double, price: Double) = DraftLine(
        key = "k$idWarehouse",
        idWarehouse = idWarehouse,
        idProduct = idWarehouse - 400,
        barcode = "B$idWarehouse",
        name = "Producto $idWarehouse",
        quantity = quantity,
        unitPrice = price,
        stock = server.warehouseAmount[idWarehouse],
    )

    private suspend fun enqueue(draft: InvoiceDraft) {
        repository.ensureDraft(draft.kind, testSession())
        repository.update(draft.kind) { draft }
        requireNotNull(repository.enqueue(draft.kind, idMarket = 12))
    }

    private val paidInvoice = InvoiceDraft(
        kind = DocumentKind.Invoice,
        client = DraftClient(id = 44, name = "Tony Stark", identify = "00100000001"),
        lines = listOf(line(501, 2.0, 100.0), line(502, 1.0, 50.0)),
        taxRate = 0.18,
        payment = DraftPayment(cash = 300.0),
        account = DraftAccount(id = 8, name = "Caja"),
    )

    @Test
    fun facturaPagadaMandaLoMismoQueElPos() = runTest {
        enqueue(paidInvoice)

        val report = sender.sendPending()

        assertEquals(1, report.sent)
        val sale = server.bodies("/ventex/api/generic/sales").single()
        assertEquals("Complete", sale["Status"]!!.jsonPrimitive.content)
        assertEquals("CO20260505000001", sale["Secuency"]!!.jsonPrimitive.content)
        assertEquals("05/05/2026 10:30 pm", sale["Date"]!!.jsonPrimitive.content)
        assertEquals(295.0, sale["Total"]!!.jsonPrimitive.double) // 250 + 18 %
        assertEquals(45.0, sale["Tax"]!!.jsonPrimitive.double)
        assertEquals(300.0, sale["Money"]!!.jsonPrimitive.double) // efectivo recibido
        assertEquals(5.0, sale["Change"]!!.jsonPrimitive.double)
        assertEquals("Tony Stark", sale["Client"]!!.jsonPrimitive.content)
        assertEquals(44, sale["IdClient"]!!.jsonPrimitive.int)
        assertEquals(5, sale["Torning"]!!.jsonPrimitive.int)
        assertEquals("Ana Pérez", sale["Username"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, sale["NCF"])

        val lines = server.bodies("/ventex/api/generic/salesProducts")
        assertEquals(2, lines.size)
        assertEquals(501, lines[0]["idWarehouse"]!!.jsonPrimitive.int) // camelCase
        assertEquals(200.0, lines[0]["Total"]!!.jsonPrimitive.double) // bruto, sin impuesto
        assertEquals(36.0, lines[0]["Tax"]!!.jsonPrimitive.double)

        // Inventario: 10 − 2 = 8, y 0 − 1 = −1 (se permite negativo).
        assertEquals(8.0, server.warehouseAmount[501])
        assertEquals(-1.0, server.warehouseAmount[502])
        val reports = server.bodies("/ventex/api/generic/reportInventory")
        assertEquals(2, reports.size)
        assertEquals("2 Vendidos por Ana Pérez a Tony Stark", reports[0]["Comentary"]!!.jsonPrimitive.content)

        // Pagada: sin documento espejo. Movimiento por lo que de verdad entró (300 − 5).
        assertTrue(server.bodies("/ventex/api/accountdocs/from-sale").isEmpty())
        val movement = server.bodies("/ventex/api/cuentas/movimientos").single()
        assertEquals(295.0, movement["Amount"]!!.jsonPrimitive.double)
        assertEquals("Venta", movement["ReferenceType"]!!.jsonPrimitive.content)
        assertEquals(100, movement["ReferenceId"]!!.jsonPrimitive.int)

        // Sale de la cola y aparece en la lista local.
        assertTrue(invoiceDao.outboxRows.value.isEmpty())
        assertEquals("Complete", salesDao.rows.value.getValue(100).status)
    }

    @Test
    fun sinRedSeQuedaEnLaColaYAlVolverNoPideOtraSecuencia() = runTest {
        enqueue(paidInvoice)
        server.dropOnce = "/generic/sales"

        val first = sender.sendPending()

        assertTrue(first.offline)
        val row = invoiceDao.outboxRows.value.values.single()
        assertEquals(OutboxState.PENDING, row.state)

        val second = sender.sendPending()

        assertEquals(1, second.sent)
        assertEquals(1, server.sequenceCounter, "La secuencia se pide una sola vez")
        assertEquals(1, server.sales.size)
    }

    @Test
    fun siLaVentaLlegoPeroSeperdioLaRespuestaNoSeDuplica() = runTest {
        enqueue(paidInvoice)
        server.loseSaleResponseOnce = true

        sender.sendPending()
        sender.sendPending()

        assertEquals(1, server.sales.size, "Se encontró por su secuencia en vez de crearla otra vez")
        assertEquals(2, server.lines.size)
        assertTrue(invoiceDao.outboxRows.value.isEmpty())
    }

    @Test
    fun siUnaLineaLlegoSinRespuestaNoSeRepite() = runTest {
        enqueue(paidInvoice)
        server.dropOnce = "/generic/warehouse/501" // falla después de crear las dos líneas

        sender.sendPending()
        sender.sendPending()

        assertEquals(2, server.lines.size)
        assertEquals(8.0, server.warehouseAmount[501], "El inventario se descuenta una sola vez")
    }

    @Test
    fun sinRangoDeNcfNoSeCreaLaVentaYQuedaConError() = runTest {
        enqueue(paidInvoice.copy(withNcf = true, ncfType = NcfType.B02))
        server.ncfAvailable = false

        val report = sender.sendPending()

        assertEquals(1, report.failed)
        assertTrue(server.sales.isEmpty())
        assertTrue(server.bodies("/ventex/api/nfc/getNextNFC").isEmpty(), "No se consume un NCF sin rango")
        val row = invoiceDao.outboxRows.value.values.single()
        assertEquals(OutboxState.FAILED, row.state)
        assertTrue(row.lastError!!.contains("comprobantes fiscales"))
    }

    @Test
    fun conNcfLoPideDespuesDeVerificarYLoGuarda() = runTest {
        enqueue(paidInvoice.copy(withNcf = true, rnc = "130000001"))

        sender.sendPending()

        val sale = server.bodies("/ventex/api/generic/sales").single()
        assertEquals("B0200000000045", sale["NCF"]!!.jsonPrimitive.content)
        assertEquals("130000001", sale["RNC"]!!.jsonPrimitive.content)
    }

    @Test
    fun aCreditoCreaElDocumentoEspejoConVencimiento() = runTest {
        enqueue(paidInvoice.copy(payment = DraftPayment(transfer = 100.0), dueInDays = 15))

        sender.sendPending()

        val sale = server.bodies("/ventex/api/generic/sales").single()
        assertEquals("Pagos Pendientes", sale["Status"]!!.jsonPrimitive.content)
        assertEquals(0.0, sale["Change"]!!.jsonPrimitive.double)
        assertEquals(100.0, sale["MoneyDeposit"]!!.jsonPrimitive.double)
        val mirror = server.bodies("/ventex/api/accountdocs/from-sale").single()
        assertEquals(100, mirror["IdSale"]!!.jsonPrimitive.int)
        assertEquals("2026-05-20", mirror["DueDate"]!!.jsonPrimitive.content)
    }

    @Test
    fun cotizacionNoLlevaSecuenciaNiTocaInventarioNiDinero() = runTest {
        enqueue(
            InvoiceDraft(
                kind = DocumentKind.Quote,
                lines = listOf(line(501, 3.0, 100.0)),
                taxRate = 0.18,
                currency = Currency.USD,
                exchangeRate = 60.0,
            ),
        )

        sender.sendPending()

        assertEquals(0, server.sequenceCounter)
        val sale = server.bodies("/ventex/api/generic/sales").single()
        assertEquals("Cotizacion", sale["Status"]!!.jsonPrimitive.content)
        assertEquals(0.0, sale["Money"]!!.jsonPrimitive.double)
        // En moneda base aunque se emitiera en dólares.
        assertEquals(354.0, sale["Total"]!!.jsonPrimitive.double)
        assertNull(sale["Secuency"])
        assertEquals(10.0, server.warehouseAmount[501])
        assertTrue(server.bodies("/ventex/api/generic/reportInventory").isEmpty())
        assertTrue(server.bodies("/ventex/api/cuentas/movimientos").isEmpty())
        assertNull(server.bodies("/ventex/api/generic/salesProducts").single()["sold"])
    }

    @Test
    fun aCreditoMandaDondePagarYLaNota() = runTest {
        val popular = DraftAccount(id = 3, name = "Popular", bankName = "Popular", accountNumber = "812345678")
        val bhd = DraftAccount(id = 7, name = "BHD", bankName = "BHD", accountNumber = "2200011")
        enqueue(paidInvoice.copy(payment = DraftPayment(), payTo = PayTo(listOf(popular, bhd), "  Envía el comprobante al 809  ")))

        sender.sendPending()

        val sale = server.bodies("/ventex/api/generic/sales").single()
        assertEquals("Pagos Pendientes", sale["Status"]!!.jsonPrimitive.content)
        assertEquals("3,7", sale["PaymentAccounts"]!!.jsonPrimitive.content)
        assertEquals("Envía el comprobante al 809", sale["PaymentNote"]!!.jsonPrimitive.content)
    }

    @Test
    fun pagadaNoMandaDondePagar() = runTest {
        enqueue(paidInvoice.copy(payTo = PayTo(listOf(DraftAccount(id = 3, name = "Popular")), "Nota")))

        sender.sendPending()

        val sale = server.bodies("/ventex/api/generic/sales").single()
        assertNull(sale["PaymentAccounts"])
        assertNull(sale["PaymentNote"])
    }

    @Test
    fun laSiguienteFacturaEmpiezaConLoUltimoDeDondePagar() = runTest {
        val memory = object : com.paybille.invoicer.feature.invoice.data.PayToMemory {
            var saved: PayTo? = null
            override suspend fun load(idMarket: Int) = saved
            override suspend fun save(idMarket: Int, payTo: PayTo) { saved = payTo }
        }
        val repo = InvoiceRepository(invoiceDao, payToMemory = memory, now = { createdAt })
        val payTo = PayTo(listOf(DraftAccount(id = 3, name = "Popular")), "Nota")
        repo.ensureDraft(DocumentKind.Invoice, testSession())
        repo.update(DocumentKind.Invoice) { paidInvoice.copy(payment = DraftPayment(), payTo = payTo) }
        repo.enqueue(DocumentKind.Invoice, idMarket = 12)

        repo.ensureDraft(DocumentKind.Invoice, testSession())

        assertEquals(payTo, repo.observeDraft(DocumentKind.Invoice).first()!!.payTo)
    }
}
