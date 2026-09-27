package com.paybille.invoicer.feature.notifications

import com.paybille.invoicer.feature.detail.domain.DueState
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.domain.PendingDocument
import com.paybille.invoicer.feature.notifications.domain.Notice
import com.paybille.invoicer.feature.notifications.domain.Notices
import com.paybille.invoicer.feature.notifications.domain.OpenDebt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoticesTest {

    private val today = "2026-09-27"

    private fun debt(id: Int, due: String?, balance: Double = 100.0, status: String = "Pendiente") =
        OpenDebt(docId = id, saleId = id + 1000, number = "$id", partyName = "Cliente $id", balance = balance, dueDate = due, status = status)

    private fun pending(id: String, failed: Boolean, createdAt: Long = 0) = PendingDocument(
        localId = id,
        kind = DocumentKind.Invoice,
        clientName = "Cliente",
        total = 50.0,
        createdAt = createdAt,
        failed = failed,
        sending = false,
        error = if (failed) "Rechazada" else null,
        status = "Complete",
    )

    @Test
    fun ordenRechazadasVencidasHoyPorVencerYEnEspera() {
        val notices = Notices.build(
            debts = listOf(
                debt(1, "2026-09-29"), // en 2 días
                debt(2, "2026-09-20"), // vencida hace 7
                debt(3, "2026-09-27"), // hoy
                debt(4, "2026-09-25"), // vencida hace 2
            ),
            pending = listOf(pending("espera", failed = false), pending("rechazada", failed = true)),
            todayIso = today,
        )

        assertEquals(
            listOf("unsent-rechazada", "due-2", "due-4", "due-3", "due-1", "unsent-espera"),
            notices.map { it.key },
        )
        assertEquals(DueState.Overdue(7), (notices[1] as Notice.Due).due)
    }

    @Test
    fun sinFechaLejanaPagadaOAnuladaNoAvisan() {
        val notices = Notices.build(
            debts = listOf(
                debt(1, null),
                debt(2, "2026-10-15"),
                debt(3, "2026-09-20", balance = 0.0),
                debt(4, "2026-09-20", status = "Anulado"),
            ),
            pending = emptyList(),
            todayIso = today,
        )
        assertTrue(notices.isEmpty())
    }

    @Test
    fun soloLoVencidoLoDeHoyYLoRechazadoEsUrgente() {
        val notices = Notices.build(
            debts = listOf(debt(1, "2026-09-20"), debt(2, "2026-09-27"), debt(3, "2026-09-29")),
            pending = listOf(pending("espera", failed = false), pending("rechazada", failed = true)),
            todayIso = today,
        )
        assertEquals(3, notices.count { it.urgent })
    }
}
