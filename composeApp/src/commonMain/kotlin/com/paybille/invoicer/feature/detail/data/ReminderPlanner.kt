package com.paybille.invoicer.feature.detail.data

import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.platform.Reminder
import com.paybille.invoicer.feature.detail.data.local.ReceivableEntity
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Qué avisos tocan, a partir de las cuentas por cobrar. Función pura: se prueba sin teléfono.
 *
 * Por cada factura con saldo y fecha:
 * - **el día antes** a las 9:00 → "Mañana vence";
 * - **el mismo día** a las 9:00 → "Hoy vence";
 * - **3 días después** a las 9:00 → "Cobra…", si sigue debiendo.
 *
 * Las que ya estaban vencidas no generan un aviso cada una (serían decenas): van juntas en
 * un solo resumen mañana a las 9:00.
 */
@OptIn(ExperimentalTime::class)
object ReminderPlanner {

    private val REMIND_AT = LocalTime(9, 0)
    const val FOLLOW_UP_DAYS = 3

    fun plan(receivables: List<ReceivableEntity>, nowMillis: Long, timeZone: String = DEFAULT_TIME_ZONE): List<Reminder> {
        val zone = runCatching { TimeZone.of(timeZone) }.getOrElse { TimeZone.of(DEFAULT_TIME_ZONE) }
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
        fun at(date: LocalDate) = LocalDateTime(date, REMIND_AT).toInstant(zone).toEpochMilliseconds()

        val reminders = mutableListOf<Reminder>()
        val overdue = mutableListOf<ReceivableEntity>()

        receivables.filter { it.balance > 0.004 && it.status != "Anulado" }.forEach { r ->
            val due = r.dueDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return@forEach
            val label = r.number?.let { "#$it" } ?: "de ${r.partyName ?: "un cliente"}"
            val who = r.partyName ?: "El cliente"
            val amount = formatMoney(r.balance)

            val candidates = listOf(
                Reminder(
                    id = "due-before-${r.docId}",
                    title = "Mañana vence la factura $label",
                    body = "$who debe $amount.",
                    atEpochMillis = at(due.plus(DatePeriod(days = -1))),
                    saleId = r.idSale,
                ),
                Reminder(
                    id = "due-today-${r.docId}",
                    title = "Hoy vence la factura $label",
                    body = "$who debe $amount. Toca para registrar el pago.",
                    atEpochMillis = at(due),
                    saleId = r.idSale,
                ),
                Reminder(
                    id = "collect-${r.docId}",
                    title = "Cobra la factura $label",
                    body = "Lleva $FOLLOW_UP_DAYS días vencida: $who debe $amount.",
                    atEpochMillis = at(due.plus(DatePeriod(days = FOLLOW_UP_DAYS))),
                    saleId = r.idSale,
                ),
            )
            val future = candidates.filter { it.atEpochMillis > nowMillis }
            if (future.isEmpty() && due < today) overdue += r else reminders += future
        }

        if (overdue.isNotEmpty()) {
            val total = overdue.sumOf { it.balance }
            val single = overdue.singleOrNull()
            reminders += Reminder(
                id = "overdue-digest",
                title = if (single != null) "Tienes una factura vencida" else "Tienes ${overdue.size} facturas vencidas",
                body = "Por cobrar: ${formatMoney(total)}.",
                atEpochMillis = at(today.plus(DatePeriod(days = 1))),
                saleId = single?.idSale,
            )
        }
        return reminders.sortedBy { it.atEpochMillis }
    }
}
