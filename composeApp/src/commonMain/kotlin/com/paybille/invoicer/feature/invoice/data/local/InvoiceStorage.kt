package com.paybille.invoicer.feature.invoice.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Borrador en curso. Uno por tipo de documento: empezar una cotización no pisa la factura
 * que se estaba escribiendo. `json` es un `InvoiceDraft` serializado.
 */
@Entity(tableName = "invoice_drafts")
data class DraftEntity(
    @PrimaryKey val kind: String,
    val json: String,
    val updatedAt: Long,
)

object OutboxState {
    /** Esperando a enviarse (o a que vuelva la red). */
    const val PENDING = "pending"

    /** Enviándose ahora mismo. */
    const val SENDING = "sending"

    /** El servidor lo rechazó: hace falta que el usuario lo mire. Se reintenta a mano. */
    const val FAILED = "failed"
}

/**
 * Cola de envíos: un documento guardado sin llegar todavía al servidor.
 *
 * El envío son varias llamadas que no forman una transacción (guía 02), así que [progress]
 * recuerda qué pasos ya se hicieron: al reintentar no se pide otra secuencia, no se quema
 * otro NCF y no se duplican líneas ni descuentos de inventario.
 */
@Entity(tableName = "invoice_outbox")
data class OutboxEntity(
    @PrimaryKey val localId: String,
    val kind: String,
    /** `InvoiceDraft` tal como estaba al pulsar Guardar. */
    val draftJson: String,
    /** `SendProgress` serializado. */
    val progress: String,
    val state: String,
    val attempts: Int,
    val lastError: String?,
    /** Epoch ms del momento en que el usuario guardó: es la fecha del documento. */
    val createdAt: Long,
    val idMarket: Int,
    val total: Double,
    val clientName: String,
)

@Dao
interface InvoiceDao {

    @Query("SELECT * FROM invoice_drafts WHERE kind = :kind")
    fun observeDraft(kind: String): Flow<DraftEntity?>

    @Query("SELECT * FROM invoice_drafts WHERE kind = :kind")
    suspend fun getDraft(kind: String): DraftEntity?

    @Upsert
    suspend fun upsertDraft(draft: DraftEntity)

    @Query("DELETE FROM invoice_drafts WHERE kind = :kind")
    suspend fun deleteDraft(kind: String)

    @Query("SELECT * FROM invoice_outbox WHERE idMarket = :idMarket ORDER BY createdAt DESC")
    fun observeOutbox(idMarket: Int): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM invoice_outbox ORDER BY createdAt ASC")
    suspend fun outbox(): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM invoice_outbox")
    fun observeOutboxCount(): Flow<Int>

    @Query("SELECT * FROM invoice_outbox WHERE localId = :localId")
    suspend fun getOutbox(localId: String): OutboxEntity?

    @Upsert
    suspend fun upsertOutbox(item: OutboxEntity)

    @Query("DELETE FROM invoice_outbox WHERE localId = :localId")
    suspend fun deleteOutbox(localId: String)

    /** Un envío que quedó a medias porque la app murió vuelve a la cola. */
    @Query("UPDATE invoice_outbox SET state = '${OutboxState.PENDING}' WHERE state = '${OutboxState.SENDING}'")
    suspend fun resetInterrupted()

    @Query("DELETE FROM invoice_drafts")
    suspend fun clearDrafts()

    @Query("DELETE FROM invoice_outbox")
    suspend fun clearOutbox()
}
