package com.paybille.invoicer.feature.auth.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM session WHERE id = $SESSION_ROW_ID")
    fun observeSession(): Flow<SessionEntity?>

    @Query("SELECT * FROM session WHERE id = $SESSION_ROW_ID")
    suspend fun getSession(): SessionEntity?

    @Query("SELECT * FROM market WHERE id = :id")
    fun observeMarket(id: Int): Flow<MarketEntity?>

    @Query("SELECT * FROM market_settings WHERE idMarket = :idMarket")
    fun observeSettings(idMarket: Int): Flow<SettingsEntity?>

    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Upsert
    suspend fun upsertMarket(market: MarketEntity)

    @Upsert
    suspend fun upsertSettings(settings: SettingsEntity)

    @Query("UPDATE session SET profileSyncedAt = :at WHERE id = $SESSION_ROW_ID")
    suspend fun markProfileSynced(at: Long)

    /** Guarda la sesión completa de una vez: nunca queda un token sin su tienda. */
    @Transaction
    suspend fun saveLogin(session: SessionEntity, market: MarketEntity?, settings: SettingsEntity?) {
        clearSessionTables()
        upsertSession(session)
        market?.let { upsertMarket(it) }
        settings?.let { upsertSettings(it) }
    }

    /**
     * Guarda un perfil refrescado solo si la sesión sigue siendo la misma. Sin esta
     * comprobación, un refresco que termina después de "Cerrar sesión" la resucitaría.
     */
    @Transaction
    suspend fun saveProfileIfCurrent(
        token: String,
        session: SessionEntity,
        market: MarketEntity?,
        settings: SettingsEntity?,
    ) {
        if (getSession()?.token != token) return
        upsertSession(session)
        market?.let { upsertMarket(it) }
        settings?.let { upsertSettings(it) }
    }

    @Transaction
    suspend fun clearSessionTables() {
        deleteSession()
        deleteMarkets()
        deleteSettings()
    }

    @Query("DELETE FROM session")
    suspend fun deleteSession()

    @Query("DELETE FROM market")
    suspend fun deleteMarkets()

    @Query("DELETE FROM market_settings")
    suspend fun deleteSettings()
}
