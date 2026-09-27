package com.paybille.invoicer.feature.invoice

import com.paybille.invoicer.feature.auth.domain.Session
import com.paybille.invoicer.feature.auth.domain.StoreProfile
import com.paybille.invoicer.feature.invoice.data.local.DraftEntity
import com.paybille.invoicer.feature.invoice.data.local.InvoiceDao
import com.paybille.invoicer.feature.invoice.data.local.OutboxEntity
import com.paybille.invoicer.feature.invoice.data.local.OutboxState
import com.paybille.invoicer.feature.sales.data.local.SaleEntity
import com.paybille.invoicer.feature.sales.data.local.SalesDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Base local en memoria para las pruebas: mismas reglas que las consultas de Room. */
class FakeInvoiceDao : InvoiceDao {
    val drafts = MutableStateFlow<Map<String, DraftEntity>>(emptyMap())
    val outboxRows = MutableStateFlow<Map<String, OutboxEntity>>(emptyMap())

    override fun observeDraft(kind: String): Flow<DraftEntity?> = drafts.map { it[kind] }
    override suspend fun getDraft(kind: String): DraftEntity? = drafts.value[kind]
    override suspend fun upsertDraft(draft: DraftEntity) { drafts.value = drafts.value + (draft.kind to draft) }
    override suspend fun deleteDraft(kind: String) { drafts.value = drafts.value - kind }

    override fun observeOutbox(idMarket: Int): Flow<List<OutboxEntity>> =
        outboxRows.map { rows -> rows.values.filter { it.idMarket == idMarket }.sortedByDescending { it.createdAt } }
    override suspend fun outbox(): List<OutboxEntity> = outboxRows.value.values.sortedBy { it.createdAt }
    override fun observeOutboxCount(): Flow<Int> = outboxRows.map { it.size }
    override suspend fun getOutbox(localId: String): OutboxEntity? = outboxRows.value[localId]
    override suspend fun upsertOutbox(item: OutboxEntity) { outboxRows.value = outboxRows.value + (item.localId to item) }
    override suspend fun deleteOutbox(localId: String) { outboxRows.value = outboxRows.value - localId }
    override suspend fun resetInterrupted() {
        outboxRows.value = outboxRows.value.mapValues { (_, row) ->
            if (row.state == OutboxState.SENDING) row.copy(state = OutboxState.PENDING) else row
        }
    }
    override suspend fun clearDrafts() { drafts.value = emptyMap() }
    override suspend fun clearOutbox() { outboxRows.value = emptyMap() }
}

class FakeSalesDao : SalesDao {
    val rows = MutableStateFlow<Map<Int, SaleEntity>>(emptyMap())

    override fun observe(idMarket: Int, statuses: List<String>): Flow<List<SaleEntity>> =
        rows.map { all -> all.values.filter { it.idMarket == idMarket && it.status in statuses }.sortedByDescending { it.id } }
    override suspend fun upsert(sales: List<SaleEntity>) { rows.value = rows.value + sales.associateBy { it.id } }
    override suspend fun deleteMissing(idMarket: Int, statuses: List<String>, oldestId: Int, keepIds: List<Int>) {
        rows.value = rows.value.filterValues {
            !(it.idMarket == idMarket && it.status in statuses && it.id >= oldestId && it.id !in keepIds)
        }
    }
    override suspend fun deleteAll(idMarket: Int, statuses: List<String>) {
        rows.value = rows.value.filterValues { !(it.idMarket == idMarket && it.status in statuses) }
    }
    override suspend fun clear() { rows.value = emptyMap() }
}

fun testSession() = Session(
    userId = 7,
    username = "ana",
    idPerson = 3,
    idMarket = 12,
    torning = "5",
    firstName = "Ana",
    lastName = "Pérez",
    roleName = null,
    store = StoreProfile(
        id = 12,
        name = "Colmado Ana",
        address = null,
        phone = null,
        rnc = null,
        image = null,
        taxValue = 0.18,
        taxLabel = "ITBIS",
        taxType = "with_tax",
        timeZone = "America/Santo_Domingo",
    ),
    settings = null,
    loggedInAt = 0,
    profileSyncedAt = 0,
)
