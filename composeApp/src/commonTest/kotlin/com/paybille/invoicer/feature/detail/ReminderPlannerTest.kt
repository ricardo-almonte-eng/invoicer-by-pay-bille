package com.paybille.invoicer.feature.detail

import com.paybille.invoicer.feature.detail.data.ReminderPlanner
import com.paybille.invoicer.feature.detail.data.local.ReceivableEntity
import com.paybille.invoicer.feature.detail.domain.DueState
import com.paybille.invoicer.feature.detail.domain.dueState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderPlannerTest {

    // 2026-05-10 12:00 en Santo Domingo (UTC-4) = 16:00 UTC.
    private val now = 1778428800000L

    private fun receivable(docId: Int, due: String?, balance: Double = 500.0, status: String = "Pendiente") =
        ReceivableEntity(
            docId = docId, idMarket = 12, idSale = 100 + docId, number = "CO00$docId",
            partyName = "Tony Stark", balance = balance, dueDate = due, status = status, syncedAt = 0,
        )

    @Test
    fun venceEnVariosDiasProgramaTresAvisosALasNueve() {
        val reminders = ReminderPlanner.plan(listOf(receivable(1, "2026-05-15")), now)

        assertEquals(listOf("due-before-1", "due-today-1", "collect-1"), reminders.map { it.id })
        // 14 may. 9:00 en Santo Domingo = 13:00 UTC.
        assertEquals(1778763600000L, reminders[0].atEpochMillis)
        assertEquals("Mañana vence la factura #CO001", reminders[0].title)
        assertEquals("Tony Stark debe $ 500.00.", reminders[0].body)
        assertEquals(101, reminders[0].saleId)
    }

    @Test
    fun venceHoyDespuesDeLasNueveSoloQuedaElDeCobro() {
        val reminders = ReminderPlanner.plan(listOf(receivable(2, "2026-05-10")), now)
        assertEquals(listOf("collect-2"), reminders.map { it.id })
    }

    @Test
    fun lasYaVencidasVanEnUnSoloResumen() {
        val reminders = ReminderPlanner.plan(
            listOf(receivable(3, "2026-04-01"), receivable(4, "2026-03-01", balance = 250.0)),
            now,
        )
        val digest = reminders.single()
        assertEquals("overdue-digest", digest.id)
        assertEquals("Tienes 2 facturas vencidas", digest.title)
        assertEquals("Por cobrar: $ 750.00.", digest.body)
    }

    @Test
    fun sinFechaSaldadasOAnuladasNoAvisan() {
        val reminders = ReminderPlanner.plan(
            listOf(
                receivable(5, null),
                receivable(6, "2026-05-20", balance = 0.0),
                receivable(7, "2026-05-20", status = "Anulado"),
            ),
            now,
        )
        assertTrue(reminders.isEmpty())
    }

    @Test
    fun estadoDelVencimiento() {
        assertEquals(DueState.InDays(5), dueState("2026-05-15", "2026-05-10"))
        assertEquals(DueState.Today, dueState("2026-05-10", "2026-05-10"))
        assertEquals(DueState.Overdue(9), dueState("2026-05-01", "2026-05-10"))
        assertEquals(DueState.NoDate, dueState(null, "2026-05-10"))
    }
}
