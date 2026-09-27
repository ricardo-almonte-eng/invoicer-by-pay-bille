package com.paybille.invoicer.feature.auth.data

import com.paybille.invoicer.core.network.TokenProvider
import com.paybille.invoicer.feature.auth.data.local.SessionDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Token de la sesión para el cliente HTTP. Vive aparte de [SessionRepository] porque el
 * repositorio depende de la red, y la red depende del token: juntos serían un ciclo.
 *
 * Se cachea en memoria porque el cliente lo pide en cada petición.
 */
class SessionTokenStore(private val dao: SessionDao) : TokenProvider {
    private val lock = Mutex()
    private var cached: String? = null
    private var loaded = false

    override suspend fun currentToken(): String? = lock.withLock {
        if (!loaded) {
            cached = dao.getSession()?.token
            loaded = true
        }
        cached
    }

    suspend fun set(token: String?) = lock.withLock {
        cached = token
        loaded = true
    }
}
