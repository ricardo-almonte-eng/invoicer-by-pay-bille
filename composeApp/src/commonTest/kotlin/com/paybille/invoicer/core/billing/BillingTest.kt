package com.paybille.invoicer.core.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BillingTest {

    @Test
    fun redondeoMitadHaciaArribaComoJavaScript() {
        assertEquals(0.13, round2(0.125))
        assertEquals(2.68, round2(2.675)) // 2.67499… en binario
        assertEquals(1.01, round2(1.005))
        assertEquals(0.0, round2(Double.NaN))
    }

    @Test
    fun conImpuestoSeSumaSobreElBrutoConDescuento() {
        val t = lineTotals(quantity = 2.0, unitPrice = 100.0, discount = 20.0, taxType = TaxType.WithTax, rate = 0.18)
        assertEquals(LineTotals(subtotal = 180.0, tax = 32.4, total = 212.4), t)
    }

    @Test
    fun incluidoSeDesglosaHaciaAtrasYNoSubeElTotal() {
        val t = lineTotals(quantity = 1.0, unitPrice = 118.0, discount = 0.0, taxType = TaxType.Included, rate = 0.18)
        assertEquals(LineTotals(subtotal = 100.0, tax = 18.0, total = 118.0), t)
    }

    @Test
    fun sinImpuestoIgnoraLaTasa() {
        val t = lineTotals(quantity = 3.0, unitPrice = 10.0, discount = 0.0, taxType = TaxType.NoTax, rate = 0.18)
        assertEquals(LineTotals(subtotal = 30.0, tax = 0.0, total = 30.0), t)
    }

    @Test
    fun laTasaElegidaPorFacturaCambiaElTotal() {
        val lines = listOf(LineInput(1.0, 1000.0, 0.0), LineInput(2.0, 250.0, 50.0))
        val at18 = documentTotals(lines, TaxType.WithTax, 0.18)
        assertEquals(DocumentTotals(subtotal = 1450.0, tax = 261.0, discount = 50.0, total = 1711.0), at18)
        assertEquals(1522.5, documentTotals(lines, TaxType.WithTax, 0.05).total)
    }

    @Test
    fun monedaExtranjeraEnAmbasDirecciones() {
        // "El dólar está a 60": 1,500 pesos se imprimen como USD 25.00 …
        assertEquals(25.0, toInvoiceCurrency(1500.0, 60.0))
        // … y USD 25.00 cobrados son 1,500 pesos para la API.
        assertEquals(1500.0, toBaseCurrency(25.0, 60.0))
        // La dirección invertida daría 90,000: si alguien cambia la fórmula, esto lo detecta.
        assertFalse(sameCents(toBaseCurrency(1500.0, 60.0), 1500.0 / 60.0))
    }

    @Test
    fun tasaNoValidaNoRompeLaConversion() {
        assertEquals(1500.0, toInvoiceCurrency(1500.0, 0.0))
        assertTrue(isValidTaxRate(0.0))
        assertTrue(isValidTaxRate(1.0))
        assertFalse(isValidTaxRate(-0.01))
        assertFalse(isValidTaxRate(1.5))
    }
}
