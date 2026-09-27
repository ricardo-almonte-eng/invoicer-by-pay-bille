package com.paybille.invoicer.feature.detail.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Detalle de una venta ya visto: se puede volver a abrir sin red. `json` = `SaleDetail`. */
@Entity(tableName = "sale_details")
data class SaleDetailEntity(
    @PrimaryKey val id: Int,
    val idMarket: Int,
    val json: String,
    val syncedAt: Long,
)

/**
 * Cuentas por cobrar con saldo (`accountdocs/get`). Alimentan los vencimientos del Inicio y
 * los avisos del teléfono. Se reemplazan enteras en cada sincronización.
 */
@Entity(tableName = "receivables")
data class ReceivableEntity(
    @PrimaryKey val docId: Int,
    val idMarket: Int,
    val idSale: Int?,
    val number: String?,
    val partyName: String?,
    val balance: Double,
    /** `YYYY-MM-DD`: el próximo vencimiento (cuota más antigua o `DueDate`). */
    val dueDate: String?,
    val status: String,
    val syncedAt: Long,
)

@Dao
interface DetailDao {

    @Query("SELECT * FROM sale_details WHERE id = :id")
    fun observeDetail(id: Int): Flow<SaleDetailEntity?>

    @Upsert
    suspend fun upsertDetail(detail: SaleDetailEntity)

    @Query("SELECT * FROM receivables WHERE idMarket = :idMarket ORDER BY dueDate")
    fun observeReceivables(idMarket: Int): Flow<List<ReceivableEntity>>

    @Query("SELECT * FROM receivables WHERE idMarket = :idMarket")
    suspend fun receivables(idMarket: Int): List<ReceivableEntity>

    @Transaction
    suspend fun replaceReceivables(idMarket: Int, rows: List<ReceivableEntity>) {
        deleteReceivables(idMarket)
        upsertReceivables(rows)
    }

    @Upsert
    suspend fun upsertReceivables(rows: List<ReceivableEntity>)

    @Query("DELETE FROM receivables WHERE idMarket = :idMarket")
    suspend fun deleteReceivables(idMarket: Int)

    @Query("DELETE FROM receivables WHERE docId = :docId")
    suspend fun deleteReceivable(docId: Int)

    @Query("DELETE FROM sale_details")
    suspend fun clearDetails()

    @Query("DELETE FROM receivables")
    suspend fun clearReceivables()
}
