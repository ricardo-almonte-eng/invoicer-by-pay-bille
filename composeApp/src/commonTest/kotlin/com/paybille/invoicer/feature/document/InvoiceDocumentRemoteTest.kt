package com.paybille.invoicer.feature.document

import com.paybille.invoicer.core.network.ApiConfig
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.createHttpClient
import com.paybille.invoicer.feature.document.data.InvoiceDocumentRemote
import com.paybille.invoicer.feature.document.data.InvoiceDocumentRepository
import com.paybille.invoicer.feature.document.domain.InvoiceConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class InvoiceDocumentRemoteTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun remote(body: String) = InvoiceDocumentRemote(
        PayBilleApi(
            createHttpClient(
                { "tk" },
                MockEngine { request ->
                    requests += request
                    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
            ApiConfig("https://api.test/ventex/api", "k"),
        ),
    )

    @Test
    fun losDatosDeLaFacturaLleganEnElSobreYConDecimalesComoTexto() = runTest {
        val source = remote(
            """{"data":{"sale":{"id":69,"number":"RI19","status":"Pagos Pendientes","total":"1295.00","tax":233.1},""" +
                """"items":[{"id":1,"name":"Circular","quantity":"1.00","price":1295,"total":1295}],""" +
                """"client":{"id":null,"name":"Ana"},"market":{"id":2,"name":"Rialaq","taxValue":0.18},""" +
                """"receivable":{"balance":1295,"paid":0,"dueDate":null,"payments":[]},""" +
                """"paymentAccounts":[],"paymentNote":null,"config":{"Title":"Factura","ShowLogo":1,"ShowTerms":false}},"meta":null}""",
        )

        val data = source.data(69)

        assertEquals("/ventex/api/ventas/factura/69/data", requests.single().url.encodedPath)
        assertEquals(HttpMethod.Get, requests.single().method)
        assertEquals(1295.0, data.sale.total)
        assertEquals(1.0, data.items.single().quantity)
        assertEquals(true, data.config?.showLogo)
        assertEquals(false, data.config?.showTerms)
    }

    @Test
    fun alGuardarViajanTambienLosValoresPorDefectoYLosNulos() = runTest {
        val source = remote("""{"Title":"Recibo","ShowLogo":true}""")

        val saved = source.saveConfig(2, InvoiceConfig(title = "Recibo", showLogo = true, signature = null))

        val request = requests.single()
        assertEquals("/ventex/api/invoiceConfig/2", request.url.encodedPath)
        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        // `true` es el valor por defecto: si no viajara, un interruptor que vuelve a encenderse no se guardaría.
        assertEquals(true, body["ShowLogo"]?.jsonPrimitive?.boolean)
        assertEquals(JsonNull, body["Signature"])
        assertEquals("Recibo", saved.title)
    }

    @Test
    fun elLogoSeReconocePorSusBytes() {
        assertEquals("image/png", InvoiceDocumentRepository.imageMime(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0)))
        assertEquals("image/jpeg", InvoiceDocumentRepository.imageMime(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0)))
        assertEquals(null, InvoiceDocumentRepository.imageMime("<html>".encodeToByteArray()))
    }
}
