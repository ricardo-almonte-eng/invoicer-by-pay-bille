package com.paybille.invoicer.feature.clients.data

import com.paybille.invoicer.core.database.Cached
import com.paybille.invoicer.core.database.PayloadCache
import com.paybille.invoicer.feature.clients.data.local.ClientDao
import com.paybille.invoicer.feature.clients.data.local.ClientEntity
import com.paybille.invoicer.feature.clients.data.remote.ClientActivityDto
import com.paybille.invoicer.feature.clients.data.remote.ClientForm
import com.paybille.invoicer.feature.clients.data.remote.ClientRecordDto
import com.paybille.invoicer.feature.clients.data.remote.ClientsRemoteDataSource
import com.paybille.invoicer.feature.clients.data.remote.PartyBalanceDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Clock

/**
 * Clientes: la lista vive en Room (copia completa de la tienda, hasta [MAX_PAGES] páginas) y se
 * busca en el teléfono, sin red y en cualquier parte del nombre — el `like` del servidor solo
 * busca por el principio. Los saldos y la actividad de cada cliente se guardan como JSON.
 */
class ClientsRepository(
    private val dao: ClientDao,
    private val remote: ClientsRemoteDataSource,
    private val cache: PayloadCache,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    fun observe(idMarket: Int, query: String): Flow<List<ClientEntity>> = dao.observe(idMarket, query.trim())

    fun observeOne(id: Int): Flow<ClientEntity?> = dao.observeOne(id)

    /** @throws com.paybille.invoicer.core.network.ApiException sin red o si el servidor falla. */
    suspend fun sync(idMarket: Int) {
        val rows = mutableListOf<ClientEntity>()
        var page = 1
        do {
            val result = remote.page(idMarket, page, PAGE_SIZE)
            rows += result.items.map { it.toEntity(idMarket, now()) }
            page++
        } while (result.hasNextPage && page <= MAX_PAGES)
        dao.replaceAll(idMarket, rows)
    }

    /** Saldo por cobrar de cada cliente (`PartyKey` → fila). */
    fun observeBalances(idMarket: Int): Flow<Cached<List<PartyBalanceDto>>?> =
        cache.observe(idMarket, BALANCES_KEY, ListSerializer(PartyBalanceDto.serializer()))

    suspend fun refreshBalances(idMarket: Int) {
        val rows = mutableListOf<PartyBalanceDto>()
        var page = 1
        do {
            val (items, hasMore) = remote.balances(page, PAGE_SIZE)
            rows += items
            page++
        } while (hasMore && page <= MAX_PAGES)
        cache.put(idMarket, BALANCES_KEY, ListSerializer(PartyBalanceDto.serializer()), rows)
    }

    fun observeActivity(idMarket: Int, idClient: Int): Flow<Cached<ClientActivityDto>?> =
        cache.observe(idMarket, activityKey(idClient), ClientActivityDto.serializer())

    suspend fun refreshActivity(idMarket: Int, idClient: Int) {
        val activity = ClientActivityDto(docs = remote.openDocs(idClient), sales = remote.sales(idMarket, idClient))
        cache.put(idMarket, activityKey(idClient), ClientActivityDto.serializer(), activity)
    }

    /** Crea (`id == null`) o actualiza, y deja la fila al día en Room. Necesita red. */
    suspend fun save(idMarket: Int, id: Int?, form: ClientForm): ClientEntity {
        val saved = if (id == null) remote.create(idMarket, form) else remote.update(idMarket, id, form)
        val entity = saved.toEntity(idMarket, now())
        dao.upsert(listOf(entity))
        return entity
    }

    suspend fun clear() = dao.clear()

    private fun activityKey(idClient: Int) = "client:$idClient"

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 50
        const val BALANCES_KEY = "balances:Cobrar"
    }
}

private fun ClientRecordDto.toEntity(fallbackMarket: Int, syncedAt: Long) = ClientEntity(
    id = id,
    idMarket = idMarket ?: fallbackMarket,
    firstName = firstName?.trim().orEmpty(),
    lastName = lastName?.trim()?.takeIf { it.isNotEmpty() },
    identifyType = identifyType,
    identify = identify?.trim()?.takeIf { it.isNotEmpty() },
    phone = phone?.trim()?.takeIf { it.isNotEmpty() },
    whatsapp = whatsapp == true,
    email = email?.trim()?.takeIf { it.isNotEmpty() },
    address = address?.trim()?.takeIf { it.isNotEmpty() },
    discount = discount,
    payItbis = payItbis == true,
    syncedAt = syncedAt,
)
