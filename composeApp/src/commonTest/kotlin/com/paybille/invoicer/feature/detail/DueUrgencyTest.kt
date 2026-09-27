package com.paybille.invoicer.feature.detail

import com.paybille.invoicer.feature.detail.domain.DUE_SOON_DAYS
import com.paybille.invoicer.feature.detail.domain.DueUrgency
import com.paybille.invoicer.feature.detail.domain.dueState
import com.paybille.invoicer.feature.detail.domain.urgency
import kotlin.test.Test
import kotlin.test.assertEquals

class DueUrgencyTest {
    private val today = "2026-09-27"

    @Test
    fun yaPasadaEsVencida() {
        assertEquals(DueUrgency.Overdue, dueState("2026-09-26", today).urgency())
    }

    @Test
    fun hoyYHastaTresDiasEsPorVencer() {
        assertEquals(DueUrgency.Soon, dueState("2026-09-27", today).urgency())
        assertEquals(DueUrgency.Soon, dueState("2026-09-30", today).urgency())
        assertEquals(3, DUE_SOON_DAYS)
    }

    @Test
    fun masLejosOSinFechaNoPideAtencion() {
        assertEquals(DueUrgency.None, dueState("2026-10-01", today).urgency())
        assertEquals(DueUrgency.None, dueState(null, today).urgency())
    }
}
