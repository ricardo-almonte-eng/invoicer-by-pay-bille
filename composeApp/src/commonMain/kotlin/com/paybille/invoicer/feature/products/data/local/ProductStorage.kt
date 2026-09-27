package com.paybille.invoicer.feature.products.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Un producto del inventario tal como lo lista el POS (`productinventory/allgrouped`): las filas
 * de `warehouse` **agrupadas por nombre**, con la existencia sumada y el rango de precios.
 * `idProduct` es el menor de los que comparten nombre.
 */
@Entity(tableName = "products", primaryKeys = ["idMarket", "idProduct"])
data class ProductEntity(
    val idMarket: Int,
    val idProduct: Int,
    val name: String,
    val quantity: Double,
    val minPrice: Double,
    val maxPrice: Double,
    /** Con código por unidad (IMEI, serie). */
    val unique: Boolean,
    val brand: String?,
    val syncedAt: Long,
)

@Dao
interface ProductDao {

    /**
     * `stock`: 0 todos · 1 con existencia · 2 agotados (0 o menos: la existencia puede quedar
     * negativa, decisión 2026-09-06). Busca en cualquier parte del nombre o la marca.
     */
    @Query(
        """
        SELECT * FROM products WHERE idMarket = :idMarket
            AND (:query = '' OR name LIKE '%' || :query || '%' OR IFNULL(brand, '') LIKE '%' || :query || '%')
            AND (:stock = 0 OR (:stock = 1 AND quantity > 0) OR (:stock = 2 AND quantity <= 0))
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun observe(idMarket: Int, query: String, stock: Int): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE idMarket = :idMarket AND idProduct = :idProduct")
    fun observeOne(idMarket: Int, idProduct: Int): Flow<ProductEntity?>

    @Upsert
    suspend fun upsert(rows: List<ProductEntity>)

    @Transaction
    suspend fun replaceAll(idMarket: Int, rows: List<ProductEntity>) {
        deleteAll(idMarket)
        upsert(rows)
    }

    @Query("DELETE FROM products WHERE idMarket = :idMarket")
    suspend fun deleteAll(idMarket: Int)

    @Query("DELETE FROM products")
    suspend fun clear()
}
