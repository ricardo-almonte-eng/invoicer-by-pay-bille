package com.paybille.invoicer.feature.document

import com.paybille.invoicer.feature.document.domain.InvoiceClient
import com.paybille.invoicer.feature.document.domain.InvoiceConfig
import com.paybille.invoicer.feature.document.domain.InvoiceData
import com.paybille.invoicer.feature.document.domain.InvoiceHtml
import com.paybille.invoicer.feature.document.domain.InvoiceItem
import com.paybille.invoicer.feature.document.domain.InvoiceMarket
import com.paybille.invoicer.feature.document.domain.InvoicePayTo
import com.paybille.invoicer.feature.document.domain.InvoicePayment
import com.paybille.invoicer.feature.document.domain.InvoiceReceivable
import com.paybille.invoicer.feature.document.domain.InvoiceSale
import com.paybille.invoicer.feature.document.domain.MiniTemplate
import com.paybille.invoicer.feature.document.domain.SignaturePoint
import com.paybille.invoicer.feature.document.domain.SignatureSvg
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MiniTemplateTest {

    @Test
    fun escapaLoQueVieneDeLosDatos() {
        val out = MiniTemplate.render("<b>{{name}}</b>{{{raw}}}", mapOf("name" to "<script>&", "raw" to "<i>x</i>"))
        assertEquals("<b>&lt;script&gt;&amp;</b><i>x</i>", out)
    }

    @Test
    fun repiteListasYBuscaEnElContextoDeFuera() {
        val out = MiniTemplate.render(
            "{{#items}}[{{name}}·{{currency}}]{{/items}}",
            mapOf("currency" to "$", "items" to listOf(mapOf("name" to "A"), mapOf("name" to "B"))),
        )
        assertEquals("[A·$][B·$]", out)
    }

    @Test
    fun seccionesCondicionalesEInvertidas() {
        val template = "{{#a}}si{{/a}}{{^a}}no{{/a}}|{{#b.c}}{{b.c}}{{/b.c}}"
        assertEquals("si|x", MiniTemplate.render(template, mapOf("a" to true, "b" to mapOf("c" to "x"))))
        assertEquals("no|", MiniTemplate.render(template, mapOf("a" to "", "b" to emptyMap<String, Any>())))
    }

    @Test
    fun unaPlantillaRotaNoRompeLaFactura() {
        assertEquals("hola {{", MiniTemplate.render("hola {{", emptyMap()))
        assertEquals("ab", MiniTemplate.render("{{#x}}a{{/y}}b", mapOf("x" to true)))
    }
}

class InvoiceHtmlTest {

    private val template = SystemFileSystem.source(Path("src/commonMain/composeResources/files/invoice_template.html"))
        .buffered().use { it.readString() }

    private fun data(
        status: String = "Pagos Pendientes",
        balance: Double = 1500.0,
        paid: Double = 1000.0,
    ) = InvoiceData(
        sale = InvoiceSale(
            id = 7,
            number = "RI0007",
            status = status,
            createdAt = "2026-10-06T16:00:00.000Z",
            taxType = "included",
            subtotal = 2118.64,
            tax = 381.36,
            total = 2500.0,
            ncf = "B0100000001",
        ),
        items = listOf(InvoiceItem(name = "Bocina <JBL>", quantity = 2.0, price = 1250.0, total = 2500.0)),
        client = InvoiceClient(name = "Ana Pérez", identify = "001-1234567-8", phone = "809-555-0101"),
        market = InvoiceMarket(id = 2, name = "Rialaq", address = "Santiago", taxLabel = "ITBIS"),
        receivable = InvoiceReceivable(
            total = 2500.0,
            paid = paid,
            balance = balance,
            dueDate = "2026-10-21",
            payments = listOf(InvoicePayment(date = "2026-10-06T17:00:00.000Z", method = "Deposito", amount = paid, reference = "TRX-9")),
        ),
        paymentAccounts = listOf(InvoicePayTo(bank = "Banco Popular", type = "Cuenta de ahorros", number = "123-456", holderName = "Rialaq", holderId = "131-0000000-1")),
        paymentNote = "Envía el comprobante.",
    )

    @Test
    fun laPlantillaDeLaAppSeRellenaEntera() {
        val html = InvoiceHtml.render(template, data(), InvoiceConfig(), todayIso = "2026-10-06")
        assertFalse(html.contains("{{"), "Quedó una etiqueta sin rellenar")
        assertTrue(html.contains("RI0007"))
        assertTrue(html.contains("Bocina &lt;JBL&gt;"))
        assertTrue(html.contains("$ 2,500.00"))
        assertTrue(html.contains("Saldo pendiente"))
        assertTrue(html.contains("$ 1,500.00"))
        assertTrue(html.contains("21 oct. 2026"))
        assertTrue(html.contains("Banco Popular"))
        assertTrue(html.contains("131-0000000-1"))
        assertTrue(html.contains("Envía el comprobante."))
        assertTrue(html.contains("Transferencia"), "Deposito se nombra como el cliente lo entiende")
        assertTrue(html.contains("ITBIS (incluido)"))
        assertTrue(html.contains("B0100000001"))
    }

    @Test
    fun laConfiguracionApagaSecciones() {
        val config = InvoiceConfig(
            showClientContact = false,
            showPayments = false,
            showBalance = false,
            showPaymentAccounts = false,
            showPaymentNote = false,
            showNcf = false,
            showTax = false,
        )
        val html = InvoiceHtml.render(template, data(), config, todayIso = "2026-10-06")
        assertTrue(html.contains("Ana Pérez"))
        assertFalse(html.contains("001-1234567-8"))
        assertFalse(html.contains("Pagos recibidos"))
        assertFalse(html.contains("Saldo pendiente"))
        assertFalse(html.contains("Dónde pagar"))
        assertFalse(html.contains("B0100000001"))
        assertFalse(html.contains("ITBIS"))
    }

    @Test
    fun unaPagadaNoPideDondePagarYLlevaElSello() {
        val html = InvoiceHtml.render(template, data(status = "Complete", balance = 0.0, paid = 2500.0), InvoiceConfig(), todayIso = "2026-10-06")
        assertFalse(html.contains("Dónde pagar"))
        assertFalse(html.contains("Saldo pendiente"))
        assertTrue(html.contains("PAGADA"))
    }

    @Test
    fun laCotizacionSeTitulaCotizacionAunqueLaTiendaEligieraOtroTitulo() {
        val model = InvoiceHtml.model(data(status = "Cotizacion"), InvoiceConfig(title = "Invoice"), com.paybille.invoicer.feature.document.domain.InvoiceAssets(), "America/Santo_Domingo")
        @Suppress("UNCHECKED_CAST")
        val doc = model["doc"] as Map<String, Any?>
        assertEquals("Cotización", doc["title"])
        assertNull(model["balance"])
    }

    @Test
    fun vencidaSiYaPasoLaFecha() {
        val model = InvoiceHtml.model(data(), InvoiceConfig(), com.paybille.invoicer.feature.document.domain.InvoiceAssets(), "America/Santo_Domingo", todayIso = "2026-10-30")
        @Suppress("UNCHECKED_CAST")
        val doc = model["doc"] as Map<String, Any?>
        assertEquals("Vencida", doc["statusLabel"])
    }

    @Test
    fun unaPlantillaGuardadaPorLaTiendaSustituyeALaDeLaApp() {
        val html = InvoiceHtml.render(template, data(), InvoiceConfig(templateHtml = "<p>{{store.name}} #{{doc.number}}</p>"))
        assertEquals("<p>Rialaq #RI0007</p>", html)
    }

    @Test
    fun elColorDeAcentoSeValidaYSeAclara() {
        assertEquals("#E8EDF1", InvoiceHtml.softOf("#16426F"))
        val html = InvoiceHtml.render(template, data(), InvoiceConfig(accentColor = "red;}</style><script>"))
        assertTrue(html.contains("--accent: ${InvoiceHtml.DEFAULT_ACCENT}"))
    }
}

class SignatureSvgTest {

    @Test
    fun losTrazosVanYVuelven() {
        val strokes = listOf(listOf(SignaturePoint(10f, 20f), SignaturePoint(30.5f, 40f)), listOf(SignaturePoint(5f, 5f)))
        val svg = assertNotNull(SignatureSvg.fromStrokes(strokes, 600f, 200f))
        assertTrue(svg.startsWith("<svg"))
        assertEquals(600f to 200f, SignatureSvg.size(svg))
        assertEquals(strokes, SignatureSvg.toStrokes(svg))
    }

    @Test
    fun soloPasanLosTrazos() {
        val dirty = """<svg viewBox="0 0 600 200" onload="alert(1)"><script>alert(2)</script>""" +
            """<path d="M1 2 L3 4" onclick="x()"/><path d="javascript:alert(3)"/></svg>"""
        val clean = assertNotNull(SignatureSvg.sanitize(dirty))
        assertFalse(clean.contains("script"))
        assertFalse(clean.contains("onload"))
        assertFalse(clean.contains("onclick"))
        assertTrue(clean.contains("""d="M1 2 L3 4""""))
    }

    @Test
    fun sinTrazosNoHayFirma() {
        assertNull(SignatureSvg.fromStrokes(emptyList(), 600f, 200f))
        assertNull(SignatureSvg.sanitize("<svg></svg>"))
    }
}
