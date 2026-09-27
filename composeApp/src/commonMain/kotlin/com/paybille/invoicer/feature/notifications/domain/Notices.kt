package com.paybille.invoicer.feature.notifications.domain

import com.paybille.invoicer.feature.detail.domain.DueState
import com.paybille.invoicer.feature.detail.domain.DueUrgency
import com.paybille.invoicer.feature.detail.domain.dueState
import com.paybille.invoicer.feature.detail.domain.urgency
import com.paybille.invoicer.feature.invoice.domain.PendingDocument

/** Una factura con saldo, tal como la guarda `receivables`. */
data class OpenDebt(
    val docId: Int,
    val saleId: Int?,
    val number: String?,
    val partyName: String?,
    val balance: Double,
    /** `YYYY-MM-DD`, o null. */
    val dueDate: String?,
    val status: String,
)

/**
 * Lo que sale en Notificaciones (la campana de Facturas). No hay servidor de notificaciones:
 * todo se deriva de lo guardado en el teléfono, así que también funciona sin red.
 */
sealed interface Notice {
    val key: String

    /** Pide atención ya: cuenta para el punto rojo de la campana. */
    val urgent: Boolean

    /** Un documento que no llega al servidor: `failed` si el servidor lo rechazó. */
    data class Unsent(val doc: PendingDocument) : Notice {
        override val key: String get() = "unsent-${doc.localId}"
        override val urgent: Boolean get() = doc.failed
    }

    /** Una factura vencida o por vencer ([com.paybille.invoicer.feature.detail.domain.DUE_SOON_DAYS]). */
    data class Due(val debt: OpenDebt, val due: DueState) : Notice {
        override val key: String get() = "due-${debt.docId}"
        override val urgent: Boolean get() = due is DueState.Overdue || due == DueState.Today
    }
}

object Notices {

    /**
     * Orden: lo que el servidor rechazó, las vencidas (la más vieja primero), las que vencen hoy,
     * las por vencer (la más cercana primero) y, al final, lo que espera conexión.
     * Una factura sin fecha o con vencimiento lejano no es un aviso.
     */
    fun build(debts: List<OpenDebt>, pending: List<PendingDocument>, todayIso: String): List<Notice> {
        val due = debts
            .filter { it.balance > 0.004 && it.status != "Anulado" }
            .map { Notice.Due(it, dueState(it.dueDate, todayIso)) }
            .filter { it.due.urgency() != DueUrgency.None }
            .sortedBy { rank(it.due) }
        val failed = pending.filter { it.failed }.sortedBy { it.createdAt }.map { Notice.Unsent(it) }
        val waiting = pending.filterNot { it.failed }.sortedBy { it.createdAt }.map { Notice.Unsent(it) }
        return failed + due + waiting
    }

    /** Vencida hace 10 → −10; hoy → 0; en 2 días → 2. */
    private fun rank(due: DueState): Int = when (due) {
        is DueState.Overdue -> -due.days
        DueState.Today -> 0
        is DueState.InDays -> due.days
        DueState.NoDate -> Int.MAX_VALUE
    }
}
