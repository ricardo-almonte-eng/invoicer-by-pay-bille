package com.paybille.invoicer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.domain.SessionState
import com.paybille.invoicer.feature.auth.presentation.LoginScreen
import com.paybille.invoicer.feature.main.MainScreen
import org.koin.compose.koinInject

/**
 * Raíz compartida por Android e iOS. La pantalla la decide la sesión guardada en Room,
 * no un `navigate()`: al entrar aparece la fila y se pasa al Inicio; al cerrar sesión
 * desaparece y se vuelve al login. Así no hay forma de quedar en el Inicio sin sesión.
 */
@Composable
fun App() {
    PbTheme {
        val sessions = koinInject<SessionRepository>()
        val state by sessions.state.collectAsState(SessionState.Loading)

        Box(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
            when (val current = state) {
                // Leer Room tarda milisegundos: basta el lienzo vacío, sin indicador.
                SessionState.Loading -> Unit
                SessionState.SignedOut -> Navigator(LoginScreen)
                // Otra tienda = otro armazón: `key` desmonta el Navigator (que desecha los modelos
                // de la tienda anterior) y `MainScreen(idMarket)` trae una clave nueva, así que
                // los modelos del armazón nuevo nacen limpios.
                is SessionState.SignedIn -> {
                    val idMarket = current.session.idMarket
                    key(idMarket) { Navigator(MainScreen(idMarket)) }
                }
            }
        }
    }
}
