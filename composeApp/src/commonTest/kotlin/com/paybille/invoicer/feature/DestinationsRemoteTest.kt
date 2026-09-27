package com.paybille.invoicer.feature

import com.paybille.invoicer.feature.products.data.remote.ProductCatalog
import com.paybille.invoicer.feature.store.data.StoreConfigForm
import com.paybille.invoicer.feature.store.data.remote.StoreRemoteDataSource
import com.paybille.invoicer.feature.store.data.toMarketBody
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.boolean
import com.paybille.invoicer.core.database.CachedPayloadEntity
import com.paybille.invoicer.core.database.PayloadCache
import com.paybille.invoicer.core.database.PayloadDao
import com.paybille.invoicer.core.format.DateRange
import com.paybille.invoicer.core.network.ApiConfig
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.createHttpClient
import com.paybille.invoicer.feature.banks.data.BanksRemoteDataSource
import com.paybille.invoicer.feature.clients.data.remote.ClientsRemoteDataSource
import com.paybille.invoicer.feature.invoice.testSession
import com.paybille.invoicer.feature.products.data.ProductCreateProgress
import com.paybille.invoicer.feature.products.data.ProductForm
import com.paybille.invoicer.feature.products.data.ProductsRepository
import com.paybille.invoicer.feature.products.data.local.ProductDao
import com.paybille.invoicer.feature.products.data.local.ProductEntity
import com.paybille.invoicer.feature.products.data.remote.ProductsRemoteDataSource
import com.paybille.invoicer.feature.products.data.unitTax
import com.paybille.invoicer.feature.reports.data.ReportsRemoteDataSource
import com.paybille.invoicer.feature.summary.data.SummaryRemoteDataSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Contratos de los endpoints de Resumen, Productos, Clientes, Bancos y Reportes. */
class DestinationsRemoteTest {

    private val requests = mutableListOf<HttpRequestData>()

    /** `routes`: sufijo de la ruta → cuerpo de la respuesta. Lo que no está responde `{data:[]}`. */
    private fun api(routes: Map<String, String> = emptyMap(), failOn: String? = null): PayBilleApi {
        val engine = MockEngine { request ->
            requests += request
            val path = request.url.encodedPath
            if (failOn != null && path.endsWith(failOn)) {
                respond("""{"error":"falló"}""", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                val body = routes.entries.firstOrNull { path.endsWith(it.key) }?.value ?: """{"data":[],"meta":null}"""
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        return PayBilleApi(createHttpClient({ "tk" }, engine), ApiConfig("https://api.test/ventex/api", "k"))
    }

    private fun HttpRequestData.json(): JsonObject = Json.parseToJsonElement((body as TextContent).text).jsonObject

    private val range = DateRange("2026-09-01", "2026-09-18")

    @Test
    fun resumenMandaElRangoYLeeLosDecimalesComoTexto() = runTest {
        val remote = SummaryRemoteDataSource(
            api(
                mapOf(
                    "/dashboard/summary" to """{"data":{"today":{"totalVendido":"1500.50","cantidadTransacciones":"3"},""" +
                        """"range":{"totalVendido":9000},"series":[{"time":"2026-09-02","value":"10.5"}],""" +
                        """"topProducts":[{"name":"Cola","qty":"4","total":"200"}],"activeTornings":{"count":1}},"meta":null}""",
                ),
            ),
        )

        val summary = remote.summary(12, range)

        val request = requests.single()
        assertEquals("/ventex/api/dashboard/summary", request.url.encodedPath)
        val body = request.json()
        assertEquals("2026-09-01", body["startDate"]!!.jsonPrimitive.content)
        assertEquals("2026-09-18", body["endDate"]!!.jsonPrimitive.content)
        assertEquals(true, body["isGet"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(12, body["params"]!!.jsonObject["IdMarket"]!!.jsonPrimitive.int)
        assertEquals(1500.50, summary.today.totalVendido)
        assertEquals(3, summary.today.cantidadTransacciones)
        assertEquals(10.5, summary.series.single().value)
        assertEquals(4.0, summary.topProducts.single().qty)
    }

    @Test
    fun inventarioAgrupadoConParamsComoArregloYPaginaEnLaQuery() = runTest {
        val remote = ProductsRemoteDataSource(
            api(
                mapOf(
                    "/productinventory/allgrouped" to """{"data":[{"nombreProducto":"Cola","cantidadAgrupada":"-2","minPrice":"50.00",""" +
                        """"maxPrice":"60.00","unique":0,"idProduct":8,"estado":"Disponible"}],"meta":{"hasNextPage":true}}""",
                ),
            ),
        )

        val page = remote.groupedPage(12, page = 2, pageSize = 100)

        val request = requests.single()
        assertEquals("2", request.url.parameters["page"])
        assertEquals("100", request.url.parameters["pageSize"])
        val params = request.json()["params"]!!.jsonArray
        assertEquals("IdMarket", params.single().jsonObject["key"]!!.jsonPrimitive.content)
        assertEquals(12, params.single().jsonObject["value"]!!.jsonPrimitive.int)
        assertTrue(page.hasNextPage)
        // La existencia puede ser negativa (decisión 2026-09-06): se lee tal cual.
        assertEquals(-2.0, page.items.single().cantidadAgrupada)
        assertEquals(false, page.items.single().unique)
    }

    @Test
    fun movimientosConPaginaEnLaQueryYFechasEnParams() = runTest {
        val remote = BanksRemoteDataSource(api())

        remote.movements(idCuenta = 5, range = range, page = 3, pageSize = 100)

        val request = requests.single()
        assertEquals("/ventex/api/cuentas/5/movimientos", request.url.encodedPath)
        // El controlador ignora `page` en el cuerpo: por eso el POS solo enseña 10.
        assertEquals("3", request.url.parameters["page"])
        assertEquals("100", request.url.parameters["pageSize"])
        val params = request.json()["params"]!!.jsonObject
        assertEquals("2026-09-01 00:00:00", params["createdAt__gte"]!!.jsonPrimitive.content)
        assertEquals("2026-09-18 23:59:59", params["createdAt__lte"]!!.jsonPrimitive.content)
    }

    @Test
    fun ventasPorFechaSinGastosYTotalesVaciosEnCero() = runTest {
        val remote = ReportsRemoteDataSource(
            api(
                mapOf(
                    "/report/totals/no" to """{"data":{"message":"No se encontraron datos"},"meta":null}""",
                    "/report/no" to """{"data":[{"id":4,"Client":"Tony","Status":"Complete","Total":"99.90"}],"meta":{"hasNextPage":false}}""",
                ),
            ),
        )

        val report = remote.sales(12, range)

        assertEquals(null, report.totals.totalVendido)
        assertEquals(99.90, report.sales.single().total)
        val list = requests.single { it.url.encodedPath.endsWith("/report/no") }
        // Arreglo `[{key, value}]`: `repositories/sales.js` lo recorre con `for…of`.
        val params = list.json()["params"]!!.jsonArray.map { it.jsonObject }
        assertEquals(12, params.first { it["key"]!!.jsonPrimitive.content == "IdMarket" }["value"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, params.first { it["key"]!!.jsonPrimitive.content == "Gasto" }["value"])
        // Los totales sí cuentan los gastos (tarjeta "Gastos"): sin filtro de `Gasto`.
        val totals = requests.single { it.url.encodedPath.endsWith("/report/totals/no") }
        assertTrue(totals.json()["params"]!!.jsonArray.none { it.jsonObject["key"]!!.jsonPrimitive.content == "Gasto" })
    }

    @Test
    fun saldosPorClienteLeeLaClaveDelCliente() = runTest {
        val remote = ClientsRemoteDataSource(
            api(
                mapOf(
                    "/accountdocs/byparty" to """{"data":[{"PartyKey":"31","PartyName":"Ana","Docs":2,"Balance":"750.00",""" +
                        """"DaysOverdue":"4"}],"meta":{"hasNextPage":false}}""",
                ),
            ),
        )

        val (rows, hasMore) = remote.balances(1, 100)

        assertEquals(false, hasMore)
        assertEquals(31, rows.single().partyKey)
        assertEquals(750.0, rows.single().balance)
        assertEquals(4, rows.single().daysOverdue)
        val body = requests.single().json()
        assertEquals("Cobrar", body["Kind"]!!.jsonPrimitive.content)
    }

    @Test
    fun altaDeProductoEnTresPasosYSinRepetirAlReintentar() = runTest {
        val routes = mapOf(
            "/generic/products" to """{"id":70,"Name":"Cola"}""",
            "/generic/warehouse" to """{"id":90,"idProduct":70}""",
            "/generic/reportInventory" to """{"id":1}""",
        )
        val dao = FakeProductDao()
        val session = testSession()
        val form = ProductForm("Cola", null, price = 118.0, cost = 60.0, amount = 10.0, minAmount = 2.0, barcode = "123456789012")

        // Primer intento: el rastro de inventario falla después de crear ficha y existencia.
        var progress = ProductCreateProgress()
        val failing = ProductsRepository(dao, ProductsRemoteDataSource(api(routes, failOn = "/generic/reportInventory")), PayloadCache(FakePayloadDao()))
        assertFailsWith<Exception> { failing.create(session, form, progress) { progress = it } }
        assertEquals(ProductCreateProgress(productId = 70, warehouseId = 90), progress)

        // Reintento: solo falta el rastro.
        requests.clear()
        val repository = ProductsRepository(dao, ProductsRemoteDataSource(api(routes)), PayloadCache(FakePayloadDao()))
        val id = repository.create(session, form, progress) { progress = it }

        assertEquals(70, id)
        assertEquals(listOf("/ventex/api/generic/reportInventory"), requests.map { it.url.encodedPath })
        val report = requests.single().json()
        assertEquals(90, report["IdWarehouse"]!!.jsonPrimitive.int)
        assertEquals(10.0, report["After"]!!.jsonPrimitive.double)
        assertEquals(10.0, dao.rows.single().quantity)
    }

    @Test
    fun altaDeProductoMandaElImpuestoInformativo() = runTest {
        val routes = mapOf(
            "/generic/products" to """{"id":70}""",
            "/generic/warehouse" to """{"id":90}""",
            "/generic/reportInventory" to """{"id":1}""",
        )
        val repository = ProductsRepository(FakeProductDao(), ProductsRemoteDataSource(api(routes)), PayloadCache(FakePayloadDao()))
        val form = ProductForm("Cola", null, price = 118.0, cost = 60.0, amount = 1.0, minAmount = 0.0, barcode = "1")

        repository.create(testSession(), form, ProductCreateProgress()) { }

        assertEquals(
            listOf("/ventex/api/generic/products", "/ventex/api/generic/warehouse", "/ventex/api/generic/reportInventory"),
            requests.map { it.url.encodedPath },
        )
        val warehouse = requests[1].json()
        val tax = unitTax(118.0, "with_tax", 0.18)
        assertEquals(tax, warehouse["Tax1"]!!.jsonPrimitive.double)
        assertEquals(118.0 - tax - 60.0, warehouse["utility1"]!!.jsonPrimitive.double)
        assertEquals(70, warehouse["idProduct"]!!.jsonPrimitive.int)
        assertEquals(12, warehouse["idMarket"]!!.jsonPrimitive.int)
    }

    @Test
    fun altaDeProductoLlevaImagenCategoriaMarcaYColor() = runTest {
        val routes = mapOf(
            "/generic/products" to """{"id":70}""",
            "/generic/warehouse" to """{"id":90}""",
            "/generic/reportInventory" to """{"id":1}""",
        )
        val repository = ProductsRepository(FakeProductDao(), ProductsRemoteDataSource(api(routes)), PayloadCache(FakePayloadDao()))
        val form = ProductForm(
            "Cola", null, price = 118.0, cost = 60.0, amount = 1.0, minAmount = 0.0, barcode = "1",
            image = "https://api.test/uploads/1.jpg", idCategory = 3, idBrand = 5, idColor = 7, colorName = "Rojo",
        )

        repository.create(testSession(), form, ProductCreateProgress()) { }

        val product = requests[0].json()
        assertEquals("https://api.test/uploads/1.jpg", product["Image"]!!.jsonPrimitive.content)
        assertEquals(3, product["IdCategory"]!!.jsonPrimitive.int)
        // Marca y color van en `warehouse`, como en `ProductQuickCreate.vue`.
        val warehouse = requests[1].json()
        assertEquals(5, warehouse["IdBrand"]!!.jsonPrimitive.int)
        assertEquals(7, warehouse["IdColor"]!!.jsonPrimitive.int)
        assertEquals("Rojo", warehouse["Color"]!!.jsonPrimitive.content)
    }

    @Test
    fun altaSinCategoriaMandaCeroComoElPos() = runTest {
        val routes = mapOf(
            "/generic/products" to """{"id":70}""",
            "/generic/warehouse" to """{"id":90}""",
            "/generic/reportInventory" to """{"id":1}""",
        )
        val repository = ProductsRepository(FakeProductDao(), ProductsRemoteDataSource(api(routes)), PayloadCache(FakePayloadDao()))

        repository.create(testSession(), ProductForm("Cola", null, 100.0, 0.0, 1.0, 0.0, "1"), ProductCreateProgress()) { }

        assertEquals(0, requests[0].json()["IdCategory"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, requests[0].json()["Image"])
        assertEquals(JsonNull, requests[1].json()["IdBrand"])
    }

    @Test
    fun catalogosPidenSoloLosActivosDe500EnQuinientosYOrdenanPorNombre() = runTest {
        val remote = ProductsRemoteDataSource(
            api(mapOf("/generic/get/brands" to """{"data":[{"id":2,"Name":"Zeta"},{"id":1,"Name":"acme"},{"id":3,"Name":" "}],"meta":null}""")),
        )

        val brands = remote.catalog(ProductCatalog.Brands)

        assertEquals(listOf("acme", "Zeta"), brands.map { it.name })
        val request = requests.single()
        assertEquals("/ventex/api/generic/get/brands", request.url.encodedPath)
        assertEquals("500", request.url.parameters["pageSize"])
        assertEquals(true, request.json()["params"]!!.jsonObject["Active"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun crearUnColorLoGuardaEnElCatalogoDelTelefono() = runTest {
        val repository = ProductsRepository(
            FakeProductDao(),
            ProductsRemoteDataSource(api(mapOf("/generic/colors" to """{"id":9,"Name":"Rojo","Active":true,"IdMarket":12}"""))),
            PayloadCache(FakePayloadDao()),
        )

        val created = repository.createCatalogItem(12, ProductCatalog.Colors, "  Rojo ")

        assertEquals(9, created.id)
        val body = requests.single().json()
        assertEquals("Rojo", body["Name"]!!.jsonPrimitive.content)
        assertEquals(true, body["Active"]!!.jsonPrimitive.boolean)
        assertEquals(12, body["IdMarket"]!!.jsonPrimitive.int)
        assertEquals(listOf("Rojo"), repository.observeCatalog(12, ProductCatalog.Colors).first()?.value?.map { it.name })
    }

    @Test
    fun subirImagenEsMultipartConElCampoImageYLeeLaUrlSinSobre() = runTest {
        val api = api(mapOf("/image/upload" to """{"message":"Imagen subida exitosamente","url":"https://api.test/uploads/1-2.jpg"}"""))

        val url = api.uploadImage(byteArrayOf(1, 2, 3), "image/jpeg", "jpg")

        assertEquals("https://api.test/uploads/1-2.jpg", url)
        val request = requests.single()
        assertEquals("/ventex/api/image/upload", request.url.encodedPath)
        assertTrue(request.body.contentType?.match(ContentType.MultiPart.FormData) == true)
        val sent = request.body.toByteArray().decodeToString()
        // multer: `uploadImage.single('image')`, la extensión sale del nombre y el tipo filtra.
        assertTrue("name=\"image\"" in sent, sent)
        assertTrue("filename=\"imagen.jpg\"" in sent, sent)
        assertTrue("image/jpeg" in sent, sent)
    }

    @Test
    fun tiendaSoloMandaLoQueSeEditaYLaTasaEnDecimal() {
        val body = StoreConfigForm(
            name = " Colmado Ana ", address = "Calle 1", phone = "809", mail = "a@b.do", rnc = "101",
            image = null, taxRate = 0.18, taxLabel = "ITBIS", taxType = "included",
        ).toMarketBody()

        assertEquals(
            setOf("Name", "Address", "Phone", "Mail", "RNC", "Image", "taxValue", "taxLabel", "taxType"),
            body.keys,
        )
        assertEquals("Colmado Ana", body["Name"]!!.jsonPrimitive.content)
        assertEquals(0.18, body["taxValue"]!!.jsonPrimitive.double)
        assertEquals(JsonNull, body["Image"])
    }

    @Test
    fun fichaDeLaTiendaLeeLaTasaQueLlegaComoTexto() = runTest {
        val remote = StoreRemoteDataSource(
            api(mapOf("/generic/markets/12" to """{"data":{"id":12,"Name":"Colmado Ana","Address":"Calle 1","Mail":"a@b.do","taxValue":"0.18","taxType":"with_tax","Active":1},"meta":null}""")),
        )

        val market = remote.market(12)

        assertEquals(0.18, market.taxValue)
        assertEquals("a@b.do", market.mail)
    }
}

private class FakeProductDao : ProductDao {
    val state = MutableStateFlow<List<ProductEntity>>(emptyList())
    val rows get() = state.value

    override fun observe(idMarket: Int, query: String, stock: Int): Flow<List<ProductEntity>> = state
    override fun observeOne(idMarket: Int, idProduct: Int): Flow<ProductEntity?> = state.map { r -> r.firstOrNull { it.idProduct == idProduct } }
    override suspend fun upsert(rows: List<ProductEntity>) {
        state.value = state.value.filterNot { old -> rows.any { it.idProduct == old.idProduct } } + rows
    }
    override suspend fun deleteAll(idMarket: Int) {
        state.value = emptyList()
    }
    override suspend fun clear() {
        state.value = emptyList()
    }
}

private class FakePayloadDao : PayloadDao {
    private val state = MutableStateFlow<Map<String, CachedPayloadEntity>>(emptyMap())
    override fun observe(idMarket: Int, key: String): Flow<CachedPayloadEntity?> = state.map { it["$idMarket:$key"] }
    override suspend fun upsert(row: CachedPayloadEntity) {
        state.value = state.value + ("${row.idMarket}:${row.key}" to row)
    }
    override suspend fun clear() {
        state.value = emptyMap()
    }
}
