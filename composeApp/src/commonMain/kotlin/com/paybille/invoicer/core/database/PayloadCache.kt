package com.paybille.invoicer.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import com.paybille.invoicer.core.network.PayBilleJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlin.time.Clock

/**
 * Respuesta de la API guardada tal cual (JSON), por clave: el Resumen, cada reporte con su
 * rango, la ficha de un producto… Lo que no se consulta por columnas no necesita tabla propia,
 * y así se ve sin red lo último que se cargó (offline first).
 */
@Entity(tableName = "cached_payloads", primaryKeys = ["idMarket", "key"])
data class CachedPayloadEntity(
    val idMarket: Int,
    val key: String,
    val json: String,
    val syncedAt: Long,
)

@Dao
interface PayloadDao {
    @Query("SELECT * FROM cached_payloads WHERE idMarket = :idMarket AND `key` = :key")
    fun observe(idMarket: Int, key: String): Flow<CachedPayloadEntity?>

    @Upsert
    suspend fun upsert(row: CachedPayloadEntity)

    @Query("DELETE FROM cached_payloads")
    suspend fun clear()
}

/** Un valor guardado y cuándo llegó del servidor. */
data class Cached<T>(val value: T, val syncedAt: Long)

class PayloadCache(private val dao: PayloadDao) {

    /** Lo guardado bajo `key`, o null. Un JSON que ya no se entiende (cambió el DTO) cuenta como vacío. */
    fun <T> observe(idMarket: Int, key: String, serializer: KSerializer<T>): Flow<Cached<T>?> =
        dao.observe(idMarket, key).map { row ->
            row?.let { r ->
                runCatching { PayBilleJson.decodeFromString(serializer, r.json) }.getOrNull()?.let { Cached(it, r.syncedAt) }
            }
        }

    suspend fun <T> put(idMarket: Int, key: String, serializer: KSerializer<T>, value: T) {
        dao.upsert(
            CachedPayloadEntity(
                idMarket = idMarket,
                key = key,
                json = PayBilleJson.encodeToString(serializer, value),
                syncedAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }

    suspend fun clear() = dao.clear()
}
