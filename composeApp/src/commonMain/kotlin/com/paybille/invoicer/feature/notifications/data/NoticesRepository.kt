package com.paybille.invoicer.feature.notifications.data

import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.feature.detail.data.local.DetailDao
import com.paybille.invoicer.feature.detail.data.local.ReceivableEntity
import com.paybille.invoicer.feature.invoice.data.InvoiceRepository
import com.paybille.invoicer.feature.notifications.domain.Notice
import com.paybille.invoicer.feature.notifications.domain.Notices
import com.paybille.invoicer.feature.notifications.domain.OpenDebt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Notificaciones, a partir de Room: las cuentas por cobrar (`receivables`, que también
 * alimentan los avisos del teléfono) y la cola de envíos. Sin red, igual.
 */
class NoticesRepository(
    private val dao: DetailDao,
    private val invoices: InvoiceRepository,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    fun observe(idMarket: Int, timeZone: String): Flow<List<Notice>> =
        combine(dao.observeReceivables(idMarket), invoices.observePending(idMarket)) { debts, pending ->
            Notices.build(debts.map { it.toDebt() }, pending, todayIso(timeZone))
        }

    /** Hoy en la zona del negocio: una factura vence a medianoche de Santo Domingo, no del teléfono. */
    fun todayIso(timeZone: String): String {
        val zone = runCatching { TimeZone.of(timeZone) }.getOrElse { TimeZone.of(DEFAULT_TIME_ZONE) }
        return kotlin.time.Instant.fromEpochMilliseconds(now()).toLocalDateTime(zone).date.toString()
    }
}

private fun ReceivableEntity.toDebt() = OpenDebt(
    docId = docId,
    saleId = idSale,
    number = number,
    partyName = partyName,
    balance = balance,
    dueDate = dueDate,
    status = status,
)
