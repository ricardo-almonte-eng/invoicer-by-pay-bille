package com.paybille.invoicer.feature.detail.data

import com.paybille.invoicer.core.format.parseApiTimestamp
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.network.PayBilleJson
import com.paybille.invoicer.core.platform.ReminderScheduler
import com.paybille.invoicer.feature.detail.data.local.DetailDao
import com.paybille.invoicer.feature.detail.data.local.ReceivableEntity
import com.paybille.invoicer.feature.detail.data.local.SaleDetailEntity
import com.paybille.invoicer.feature.detail.data.remote.AccountDocDto
import com.paybille.invoicer.feature.detail.data.remote.DetailRemoteDataSource
import com.paybille.invoicer.feature.detail.data.remote.FullDocDto
import com.paybille.invoicer.feature.detail.data.remote.SaleHeaderDto
import com.paybille.invoicer.feature.detail.domain.DetailLine
import com.paybille.invoicer.feature.detail.domain.Installment
import com.paybille.invoicer.feature.detail.domain.PaymentMethod
import com.paybille.invoicer.feature.detail.domain.Receivable
import com.paybille.invoicer.feature.detail.domain.ReceivablePayment
import com.paybille.invoicer.feature.detail.domain.SaleDetail
import com.paybille.invoicer.feature.sales.data.SalesRepository
import com.paybille.invoicer.feature.sales.domain.SaleStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Detalle de una venta. Offline first: la pantalla observa la copia de Room y [refresh] la
 * pone al día. Los abonos sí necesitan red: el saldo lo calcula solo el servidor.
 */
@OptIn(ExperimentalTime::class)
class SaleDetailRepository(
    private val dao: DetailDao,
    private val remote: DetailRemoteDataSource,
    private val sales: SalesRepository,
    private val receivables: ReceivablesRepository,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    fun observe(saleId: Int): Flow<SaleDetail?> =
        dao.observeDetail(saleId).map { entity ->
            entity?.let { runCatching { PayBilleJson.decodeFromString(SaleDetail.serializer(), it.json) }.getOrNull() }
        }

    /** @throws ApiException sin red o si el servidor falla. */
    suspend fun refresh(saleId: Int, idMarket: Int) {
        val header = remote.sale(idMarket, saleId)
            ?: throw ApiException("Esta factura ya no existe en el servidor.", ApiException.Kind.Server, 404)
        val lines = remote.lines(saleId)
            .filter { it.isTrade != true }
            .map {
                DetailLine(
                    name = it.name ?: "Producto",
                    quantity = it.amount ?: 0.0,
                    price = it.price ?: 0.0,
                    discount = it.discount ?: 0.0,
                    tax = it.tax ?: 0.0,
                    total = it.total ?: 0.0,
                )
            }
        // Solo las ventas con saldo tienen documento. `from-sale` lo encuentra (o lo crea, para
        // las ventas viejas del POS que aún no lo tenían).
        val receivable = if (header.status == SaleStatus.PENDING) remote.receivableForSale(saleId).toDomain() else null
        save(header.toDetail(idMarket, lines, receivable))
    }

    /**
     * Registra un abono y vuelve a leer la factura: el servidor recalcula saldo, cambia el
     * estatus y crea el movimiento de cuenta. La app no calcula ni crea nada de eso.
     */
    suspend fun addPayment(
        detail: SaleDetail,
        amount: Double,
        method: PaymentMethod,
        paymentDateIso: String,
        idCuenta: Int?,
        reference: String?,
    ) {
        val doc = detail.receivable ?: throw ApiException("Esta factura no tiene saldo pendiente.", ApiException.Kind.Server)
        remote.addPayment(doc.docId, amount, method.apiValue, paymentDateIso, idCuenta, reference)
        refresh(detail.id, detail.idMarket)
        // La lista del Inicio y los avisos también cambian (puede haber quedado saldada).
        runCatching { sales.refreshOne(detail.id, detail.idMarket) }
        runCatching { receivables.refresh(detail.idMarket) }
    }

    private suspend fun save(detail: SaleDetail) {
        val stamped = detail.copy(syncedAt = now())
        dao.upsertDetail(
            SaleDetailEntity(
                id = detail.id,
                idMarket = detail.idMarket,
                json = PayBilleJson.encodeToString(SaleDetail.serializer(), stamped),
                syncedAt = stamped.syncedAt,
            ),
        )
    }
}

/** Cuentas por cobrar con saldo: vencimientos del Inicio y avisos del teléfono. */
@OptIn(ExperimentalTime::class)
class ReceivablesRepository(
    private val dao: DetailDao,
    private val remote: DetailRemoteDataSource,
    private val scheduler: ReminderScheduler,
    private val timeZone: suspend () -> String,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    fun observeBySale(idMarket: Int): Flow<Map<Int, ReceivableEntity>> =
        dao.observeReceivables(idMarket).map { rows -> rows.filter { it.idSale != null }.associateBy { it.idSale!! } }

    /** "Te deben": la suma de los saldos guardados y cuántos documentos son. */
    fun observeOwed(idMarket: Int): Flow<Pair<Double, Int>> =
        dao.observeReceivables(idMarket).map { rows -> rows.sumOf { it.balance } to rows.count { it.balance > 0.004 } }

    /**
     * Descarga todas las cuentas con saldo (hasta [MAX_PAGES] páginas) y reprograma los avisos.
     * @throws ApiException sin red o si el servidor falla.
     */
    suspend fun refresh(idMarket: Int) {
        val rows = mutableListOf<AccountDocDto>()
        var page = 1
        do {
            val result = remote.openReceivables(page, PAGE_SIZE)
            rows += result.rows
            page++
        } while (result.hasNextPage && page <= MAX_PAGES)

        val syncedAt = now()
        val entities = rows.map { doc ->
            ReceivableEntity(
                docId = doc.id,
                idMarket = idMarket,
                idSale = doc.idSale,
                number = doc.secuency,
                partyName = doc.partyName,
                balance = doc.balance ?: 0.0,
                dueDate = (doc.nextDueDate ?: doc.dueDate)?.take(10),
                status = doc.status,
                syncedAt = syncedAt,
            )
        }
        dao.replaceReceivables(idMarket, entities)
        reschedule(idMarket)
    }

    /** Reprograma los avisos con lo que hay guardado (también sirve sin red). */
    suspend fun reschedule(idMarket: Int) {
        scheduler.replaceAll(ReminderPlanner.plan(dao.receivables(idMarket), now(), timeZone()))
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 5
    }
}

private fun SaleHeaderDto.toDetail(idMarket: Int, lines: List<DetailLine>, receivable: Receivable?) = SaleDetail(
    id = id,
    idMarket = this.idMarket ?: idMarket,
    number = secuency?.trim()?.takeIf { it.isNotEmpty() } ?: id.toString(),
    clientName = client?.trim()?.takeIf { it.isNotEmpty() } ?: "Consumidor final",
    idClient = idClient,
    status = status,
    dateText = date,
    createdAt = parseApiTimestamp(createdAt),
    subtotal = subtotal ?: 0.0,
    tax = tax ?: 0.0,
    discount = discount ?: 0.0,
    total = total ?: 0.0,
    cash = money ?: 0.0,
    transfer = moneyDeposit ?: 0.0,
    card = moneyCredit ?: 0.0,
    change = change ?: 0.0,
    ncf = ncf?.trim()?.takeIf { it.isNotEmpty() },
    rnc = rnc?.trim()?.takeIf { it.isNotEmpty() },
    taxType = taxType,
    lines = lines,
    receivable = receivable,
)

internal fun FullDocDto.toDomain(): Receivable? {
    val doc = document ?: return null
    return Receivable(
        docId = doc.id,
        total = doc.total ?: 0.0,
        paid = doc.paid ?: 0.0,
        balance = doc.balance ?: 0.0,
        dueDate = doc.dueDate?.take(10),
        nextDueDate = doc.nextDueDate?.take(10),
        status = doc.status,
        lateFeeOutstanding = ((doc.lateFeeAccrued ?: 0.0) - (doc.lateFeePaid ?: 0.0)).coerceAtLeast(0.0),
        payments = payments.map {
            ReceivablePayment(
                id = it.id,
                date = it.paymentDate,
                method = it.method,
                amount = it.amount ?: 0.0,
                lateFee = it.lateFee ?: 0.0,
                reference = it.reference,
                voided = it.status == "Anulado",
            )
        },
        installments = installments.mapNotNull { inst ->
            Installment(
                number = inst.number ?: return@mapNotNull null,
                dueDate = inst.dueDate?.take(10) ?: return@mapNotNull null,
                total = inst.total ?: 0.0,
                balance = inst.balance ?: 0.0,
                status = inst.status,
            )
        },
    )
}
