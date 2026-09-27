package com.paybille.invoicer.feature.sales.data

import com.paybille.invoicer.core.format.parseApiTimestamp
import com.paybille.invoicer.feature.invoice.domain.InvoiceDraft
import com.paybille.invoicer.feature.sales.data.local.SaleEntity
import com.paybille.invoicer.feature.sales.data.local.SalesDao
import com.paybille.invoicer.feature.sales.data.remote.SaleDto
import com.paybille.invoicer.feature.sales.data.remote.SalesRemoteDataSource
import com.paybille.invoicer.feature.sales.domain.SaleStatus
import com.paybille.invoicer.feature.sales.domain.SaleSummary
import com.paybille.invoicer.feature.sales.domain.SalesFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

const val SALES_PAGE_SIZE = 30

/**
 * Lista de ventas, offline first: la pantalla observa Room ([observe]) y [loadPage] la
 * rellena desde la API cuando hay red.
 */
@OptIn(ExperimentalTime::class)
class SalesRepository(
    private val dao: SalesDao,
    private val remote: SalesRemoteDataSource,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    fun observe(idMarket: Int, filter: SalesFilter): Flow<List<SaleSummary>> =
        dao.observe(idMarket, filter.statuses).map { rows -> rows.map { it.toSummary() } }

    /**
     * Descarga una página y la guarda. Devuelve si hay más.
     *
     * @throws com.paybille.invoicer.core.network.ApiException sin red o si el servidor falla.
     */
    suspend fun loadPage(idMarket: Int, filter: SalesFilter, page: Int): Boolean {
        val result = remote.page(idMarket, filter.statuses, page, SALES_PAGE_SIZE)
        val syncedAt = now()
        val rows = result.items.map { it.toEntity(idMarket, syncedAt) }

        dao.upsert(rows)
        if (page == 1) {
            if (rows.isEmpty()) {
                dao.deleteAll(idMarket, filter.statuses)
            } else {
                dao.deleteMissing(idMarket, filter.statuses, rows.minOf { it.id }, rows.map { it.id })
            }
        }
        return result.hasNextPage
    }

    /** Pone al día una sola venta en la lista (tras un abono, su estatus puede cambiar). */
    suspend fun refreshOne(saleId: Int, idMarket: Int) {
        val dto = remote.one(idMarket, saleId) ?: return
        dao.upsert(listOf(dto.toEntity(idMarket, now())))
    }

    /**
     * Guarda en la lista local una venta que la app acaba de enviar: aparece en el Inicio al
     * momento, sin esperar al siguiente refresco (que la reemplazará con la del servidor).
     */
    suspend fun saveSent(
        saleId: Int,
        idMarket: Int,
        draft: InvoiceDraft,
        sequence: String?,
        ncf: String?,
        createdAt: Long,
        clientName: String,
    ) {
        dao.upsert(
            listOf(
                SaleEntity(
                    id = saleId,
                    idMarket = idMarket,
                    secuency = sequence,
                    client = clientName,
                    status = draft.status,
                    total = draft.totals.total,
                    createdAt = createdAt,
                    displayDate = null,
                    ncf = ncf,
                    syncedAt = now(),
                ),
            ),
        )
    }

    suspend fun clear() = dao.clear()
}

private fun SaleDto.toEntity(fallbackMarket: Int, syncedAt: Long) = SaleEntity(
    id = id,
    idMarket = idMarket ?: fallbackMarket,
    secuency = secuency?.trim()?.takeIf { it.isNotEmpty() },
    client = client?.trim()?.takeIf { it.isNotEmpty() },
    status = status,
    total = total ?: 0.0,
    createdAt = parseApiTimestamp(createdAt),
    displayDate = date,
    ncf = ncf?.trim()?.takeIf { it.isNotEmpty() },
    syncedAt = syncedAt,
)

/** Fila de lista de una venta que no se guarda en `sales` (la ficha de un cliente, un reporte). */
fun SaleDto.toSummary(): SaleSummary = toEntity(fallbackMarket = 0, syncedAt = 0).toSummary()

// En el POS una venta sin cliente es "Consumidor final".
private const val NO_CLIENT = "Consumidor final"

private fun SaleEntity.toSummary() = SaleSummary(
    id = id,
    number = secuency ?: id.toString(),
    clientName = client ?: NO_CLIENT,
    status = SaleStatus.from(status),
    total = total,
    createdAt = createdAt,
    displayDate = displayDate,
    ncf = ncf,
)
