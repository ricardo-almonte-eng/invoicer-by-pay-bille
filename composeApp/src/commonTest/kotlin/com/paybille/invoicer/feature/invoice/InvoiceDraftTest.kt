package com.paybille.invoicer.feature.invoice

import com.paybille.invoicer.feature.invoice.data.remote.InvoiceRemoteDataSource
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.domain.DraftLine
import com.paybille.invoicer.feature.invoice.domain.DraftPayment
import com.paybille.invoicer.feature.invoice.domain.InvoiceDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InvoiceDraftTest {

    private val line = DraftLine(
        key = "a", idWarehouse = 1, idProduct = 1, barcode = null, name = "A",
        quantity = 1.0, unitPrice = 100.0, stock = 0.0,
    )
    private val draft = InvoiceDraft(kind = DocumentKind.Invoice, lines = listOf(line), taxRate = 0.18)

    @Test
    fun sinCobroQuedaPendiente() {
        assertEquals("Pagos Pendientes", draft.status)
        assertEquals(118.0, draft.missing)
    }

    @Test
    fun devueltaSoloSaleDelEfectivo() {
        val paid = draft.copy(payment = DraftPayment(cash = 200.0))
        assertEquals("Complete", paid.status)
        assertEquals(82.0, paid.change)
        assertFalse(paid.overpaidWithoutCash)
    }

    @Test
    fun transferenciaDeMasNoSePuedeGuardar() {
        val over = draft.copy(payment = DraftPayment(transfer = 150.0))
        assertTrue(over.overpaidWithoutCash)
        assertEquals(0.0, over.change)
        assertFalse(over.canSave)
    }

    @Test
    fun efectivoYTarjetaCombinados() {
        val mixed = draft.copy(payment = DraftPayment(cash = 50.0, card = 100.0))
        // 150 recibidos, 118 de total: 32 de devuelta, que salen del efectivo.
        assertEquals(32.0, mixed.change)
        assertFalse(mixed.overpaidWithoutCash)
    }

    @Test
    fun cotizacionIgnoraElCobro() {
        val quote = draft.copy(kind = DocumentKind.Quote, payment = DraftPayment(cash = 500.0))
        assertEquals("Cotizacion", quote.status)
        assertEquals(0.0, quote.paid)
        assertEquals(0.0, quote.change)
    }

    @Test
    fun sinExistenciaAvisaPeroDejaGuardar() {
        assertTrue(draft.lines.single().exceedsStock)
        assertTrue(draft.canSave)
    }

    @Test
    fun formaDeNcf() {
        assertTrue(InvoiceRemoteDataSource.isNcf("B0200000000045", "B02"))
        assertFalse(InvoiceRemoteDataSource.isNcf("No hay rangos disponibles para este tipo de NCF.", "B02"))
        assertFalse(InvoiceRemoteDataSource.isNcf("B01", "B01"))
    }
}
