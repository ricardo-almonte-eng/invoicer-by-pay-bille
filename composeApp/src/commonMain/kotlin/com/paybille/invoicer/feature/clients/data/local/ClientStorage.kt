package com.paybille.invoicer.feature.clients.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Ficha de `clients`, copiada entera al teléfono: la lista y el buscador funcionan sin red. */
@Entity(tableName = "clients", indices = [Index("idMarket")])
data class ClientEntity(
    @PrimaryKey val id: Int,
    val idMarket: Int,
    val firstName: String,
    val lastName: String?,
    val identifyType: String?,
    val identify: String?,
    val phone: String?,
    /** El teléfono es de WhatsApp (en la API es un booleano, no un número). */
    val whatsapp: Boolean,
    val email: String?,
    val address: String?,
    val discount: Int?,
    val payItbis: Boolean,
    val syncedAt: Long,
) {
    val fullName: String get() = listOfNotNull(firstName, lastName).joinToString(" ") { it.trim() }.trim()
}

@Dao
interface ClientDao {

    /** `query` vacío = todos. Busca en cualquier parte del nombre, teléfono o cédula. */
    @Query(
        """
        SELECT * FROM clients WHERE idMarket = :idMarket AND (
            :query = '' OR
            (firstName || ' ' || IFNULL(lastName, '')) LIKE '%' || :query || '%' OR
            IFNULL(phone, '') LIKE '%' || :query || '%' OR
            REPLACE(IFNULL(identify, ''), '-', '') LIKE '%' || REPLACE(:query, '-', '') || '%'
        )
        ORDER BY firstName COLLATE NOCASE, lastName COLLATE NOCASE
        """,
    )
    fun observe(idMarket: Int, query: String): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :id")
    fun observeOne(id: Int): Flow<ClientEntity?>

    @Upsert
    suspend fun upsert(rows: List<ClientEntity>)

    /** La copia completa sustituye a la anterior: lo borrado en el POS desaparece aquí. */
    @Transaction
    suspend fun replaceAll(idMarket: Int, rows: List<ClientEntity>) {
        deleteAll(idMarket)
        upsert(rows)
    }

    @Query("DELETE FROM clients WHERE idMarket = :idMarket")
    suspend fun deleteAll(idMarket: Int)

    @Query("DELETE FROM clients")
    suspend fun clear()
}
