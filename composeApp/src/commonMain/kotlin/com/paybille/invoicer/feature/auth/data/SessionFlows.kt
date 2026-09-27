package com.paybille.invoicer.feature.auth.data

import com.paybille.invoicer.feature.auth.domain.Session
import com.paybille.invoicer.feature.auth.domain.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

/** La sesión abierta (espera mientras se lee la base local). */
fun SessionRepository.signedIn(): Flow<Session> = state.filterIsInstance<SessionState.SignedIn>().map { it.session }

/** La tienda de la sesión abierta; solo emite cuando cambia. */
fun SessionRepository.idMarketFlow(): Flow<Int> = signedIn().map { it.idMarket }.distinctUntilChanged()
