package com.paybille.invoicer.feature.sales.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Copia local de la cabecera de `sales`, lo justo para la lista. La API es la dueña de
 * estas filas: se reemplazan cada vez que llega una página.
 *
 * Los gastos (`Gasto = true`) nunca entran: se filtran en la petición.
 */
@Entity(
    tableName = "sales",
    indices = [Index(value = ["idMarket", "status"])],
)
data class SaleEntity(
    @PrimaryKey val id: Int,
    val idMarket: Int,
    val secuency: String?,
    val client: String?,
    val status: String,
    val total: Double,
    /** Epoch ms de `createdAt`. `sales.Date` no es una fecha y no sirve para ordenar. */
    val createdAt: Long?,
    val displayDate: String?,
    val ncf: String?,
    /** Epoch ms de la última vez que esta fila llegó del servidor. */
    val syncedAt: Long,
)

@Dao
interface SalesDao {

    // El orden es el mismo que usa la API (`id DESC`): así la página siguiente siempre
    // continúa donde termina lo que se ve.
    @Query("SELECT * FROM sales WHERE idMarket = :idMarket AND status IN (:statuses) ORDER BY id DESC")
    fun observe(idMarket: Int, statuses: List<String>): Flow<List<SaleEntity>>

    @Upsert
    suspend fun upsert(sales: List<SaleEntity>)

    /**
     * Tras recargar la PRIMERA página, borra las filas de ese filtro que ya no vinieron y
     * son más nuevas que la última recibida: se anularon o se borraron en el servidor.
     * Lo más viejo se conserva (sigue en páginas que aún no se han pedido).
     */
    @Query(
        "DELETE FROM sales WHERE idMarket = :idMarket AND status IN (:statuses) " +
            "AND id >= :oldestId AND id NOT IN (:keepIds)",
    )
    suspend fun deleteMissing(idMarket: Int, statuses: List<String>, oldestId: Int, keepIds: List<Int>)

    @Query("DELETE FROM sales WHERE idMarket = :idMarket AND status IN (:statuses)")
    suspend fun deleteAll(idMarket: Int, statuses: List<String>)

    @Query("DELETE FROM sales")
    suspend fun clear()
}
