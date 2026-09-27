package com.paybille.invoicer.feature.invoice.data

import com.paybille.invoicer.core.billing.TaxType
import com.paybille.invoicer.core.network.PayBilleJson
import com.paybille.invoicer.feature.auth.domain.Session
import com.paybille.invoicer.feature.invoice.data.local.DraftEntity
import com.paybille.invoicer.feature.invoice.data.local.InvoiceDao
import com.paybille.invoicer.feature.invoice.data.local.OutboxEntity
import com.paybille.invoicer.feature.invoice.data.local.OutboxState
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.domain.DraftLine
import com.paybille.invoicer.feature.invoice.domain.InvoiceDraft
import com.paybille.invoicer.feature.invoice.domain.PayTo
import com.paybille.invoicer.feature.invoice.domain.PendingDocument
import com.paybille.invoicer.core.database.PayloadCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Lo último que se eligió en "Dónde pagar", por tienda: la siguiente factura empieza igual. */
interface PayToMemory {
    suspend fun load(idMarket: Int): PayTo?

    suspend fun save(idMarket: Int, payTo: PayTo)

    /** Sin memoria (pruebas). */
    object None : PayToMemory {
        override suspend fun load(idMarket: Int): PayTo? = null

        override suspend fun save(idMarket: Int, payTo: PayTo) = Unit
    }
}

/** [PayToMemory] en `cached_payloads`: vive en el teléfono, se borra al cerrar sesión. */
class CachedPayToMemory(private val cache: PayloadCache) : PayToMemory {
    override suspend fun load(idMarket: Int): PayTo? = cache.observe(idMarket, KEY, PayTo.serializer()).first()?.value

    override suspend fun save(idMarket: Int, payTo: PayTo) = cache.put(idMarket, KEY, PayTo.serializer(), payTo)

    private companion object {
        const val KEY = "invoice:payTo"
    }
}

/**
 * Borradores y cola de envíos. Todo en Room: el editor observa el borrador y cada cambio
 * se escribe al momento (si la app muere a media factura, al volver está todo).
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class InvoiceRepository(
    private val dao: InvoiceDao,
    private val payToMemory: PayToMemory = PayToMemory.None,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    // Las ediciones llegan muy seguidas (cada tecla): se serializan para no perder ninguna.
    private val writeLock = Mutex()

    fun observeDraft(kind: DocumentKind): Flow<InvoiceDraft?> =
        dao.observeDraft(kind.name).map { entity -> entity?.let { decodeDraft(it) } }

    /** Crea el borrador si no existe, con los valores por defecto de la tienda. */
    suspend fun ensureDraft(kind: DocumentKind, session: Session) = writeLock.withLock {
        if (dao.getDraft(kind.name)?.let { decodeDraft(it) } != null) return@withLock
        val draft = InvoiceDraft(
            kind = kind,
            // La tasa y el tipo de impuesto se eligen por factura; la tienda solo los prellena.
            taxRate = session.defaultTaxRate,
            taxType = TaxType.fromApi(session.store?.taxType),
            // "Dónde pagar" casi nunca cambia de una factura a otra.
            payTo = payToMemory.load(session.idMarket) ?: PayTo(),
        )
        save(draft)
    }

    suspend fun update(kind: DocumentKind, change: (InvoiceDraft) -> InvoiceDraft) = writeLock.withLock {
        val current = dao.getDraft(kind.name)?.let { decodeDraft(it) } ?: return@withLock
        val next = change(current)
        if (next != current) save(next)
    }

    /** Agrega un producto. Si ya estaba (y no es único), suma una unidad. */
    suspend fun addLine(kind: DocumentKind, line: DraftLine) = update(kind) { draft ->
        val existing = draft.lines.firstOrNull { it.idWarehouse != null && it.idWarehouse == line.idWarehouse }
        when {
            existing == null -> draft.copy(lines = draft.lines + line)
            existing.unique -> draft
            else -> draft.copy(
                lines = draft.lines.map { if (it.key == existing.key) it.copy(quantity = it.quantity + 1) else it },
            )
        }
    }

    suspend fun discard(kind: DocumentKind) = writeLock.withLock { dao.deleteDraft(kind.name) }

    /**
     * Pasa el borrador a la cola de envíos y lo borra. A partir de aquí el documento existe
     * para el usuario aunque no haya red: se enviará cuando la haya.
     */
    suspend fun enqueue(kind: DocumentKind, idMarket: Int): String? = writeLock.withLock {
        val draft = dao.getDraft(kind.name)?.let { decodeDraft(it) } ?: return@withLock null
        if (!draft.canSave) return@withLock null
        val localId = Uuid.random().toString()
        dao.upsertOutbox(
            OutboxEntity(
                localId = localId,
                kind = kind.name,
                draftJson = PayBilleJson.encodeToString(InvoiceDraft.serializer(), draft),
                progress = PayBilleJson.encodeToString(SendProgress.serializer(), SendProgress()),
                state = OutboxState.PENDING,
                attempts = 0,
                lastError = null,
                createdAt = now(),
                idMarket = idMarket,
                total = draft.totals.total,
                clientName = draft.client?.name ?: NO_CLIENT,
            ),
        )
        dao.deleteDraft(kind.name)
        if (draft.asksForPayment) payToMemory.save(idMarket, draft.payTo)
        localId
    }

    fun observePending(idMarket: Int): Flow<List<PendingDocument>> =
        dao.observeOutbox(idMarket).map { rows ->
            rows.map { row ->
                PendingDocument(
                    localId = row.localId,
                    kind = DocumentKind.valueOf(row.kind),
                    clientName = row.clientName,
                    total = row.total,
                    createdAt = row.createdAt,
                    failed = row.state == OutboxState.FAILED,
                    sending = row.state == OutboxState.SENDING,
                    error = row.lastError,
                    status = decodeDraft(row.draftJson).status,
                )
            }
        }

    fun observePendingCount(): Flow<Int> = dao.observeOutboxCount()

    /** Un documento que el servidor rechazó vuelve a la cola para otro intento. */
    suspend fun retry(localId: String) {
        val row = dao.getOutbox(localId) ?: return
        if (row.state == OutboxState.FAILED) dao.upsertOutbox(row.copy(state = OutboxState.PENDING))
    }

    suspend fun clear() {
        dao.clearDrafts()
        dao.clearOutbox()
    }

    private suspend fun save(draft: InvoiceDraft) {
        val stamped = draft.copy(updatedAt = now())
        dao.upsertDraft(
            DraftEntity(
                kind = draft.kind.name,
                json = PayBilleJson.encodeToString(InvoiceDraft.serializer(), stamped),
                updatedAt = stamped.updatedAt,
            ),
        )
    }

    // Un borrador ilegible (p. ej. tras cambiar el modelo) se descarta en vez de romper la app.
    private fun decodeDraft(entity: DraftEntity): InvoiceDraft? = runCatching { decodeDraft(entity.json) }.getOrNull()

    companion object {
        /** En el POS una venta sin cliente es "Consumidor final". */
        const val NO_CLIENT = "Consumidor final"

        internal fun decodeDraft(json: String): InvoiceDraft =
            PayBilleJson.decodeFromString(InvoiceDraft.serializer(), json)
    }
}
