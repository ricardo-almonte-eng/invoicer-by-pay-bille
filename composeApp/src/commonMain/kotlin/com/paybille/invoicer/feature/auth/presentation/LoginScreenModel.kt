package com.paybille.invoicer.feature.auth.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.domain.LoginResult
import com.paybille.invoicer.feature.auth.domain.StoreOption
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val usernameError: String? = null,
    val passwordError: String? = null,
    /** No nulo = segunda fase: el usuario elige tienda. */
    val stores: List<StoreOption>? = null,
    val submitting: Boolean = false,
    val submittingStoreId: Int? = null,
    val message: LoginMessage? = null,
)

data class LoginMessage(val text: String, val offline: Boolean)

/**
 * Login en una o dos fases, como `pages/credentials/login.vue` del POS. Las credenciales
 * se quedan aquí mientras se elige tienda: la segunda llamada las repite.
 *
 * Al entrar no se navega desde aquí: la sesión aparece en Room y `App` cambia de
 * pantalla sola.
 */
class LoginScreenModel(
    private val sessions: SessionRepository,
) : StateScreenModel<LoginUiState>(LoginUiState()) {

    fun onUsernameChange(value: String) = mutableState.update {
        it.copy(username = value, usernameError = null, message = null)
    }

    fun onPasswordChange(value: String) = mutableState.update {
        it.copy(password = value, passwordError = null, message = null)
    }

    fun submit() {
        val current = state.value
        if (current.submitting) return

        val usernameError = if (current.username.isBlank()) "Escribe tu usuario." else null
        val passwordError = if (current.password.isEmpty()) "Escribe tu contraseña." else null
        if (usernameError != null || passwordError != null) {
            mutableState.update { it.copy(usernameError = usernameError, passwordError = passwordError) }
            return
        }
        login(storeId = null)
    }

    fun chooseStore(store: StoreOption) {
        if (state.value.submitting) return
        login(storeId = store.id)
    }

    fun backToCredentials() = mutableState.update { it.copy(stores = null, message = null) }

    private fun login(storeId: Int?) {
        val current = state.value
        mutableState.update { it.copy(submitting = true, submittingStoreId = storeId, message = null) }

        screenModelScope.launch {
            try {
                when (val result = sessions.login(current.username, current.password, storeId)) {
                    LoginResult.Success -> Unit
                    is LoginResult.StoreRequired -> mutableState.update {
                        if (result.stores.isEmpty()) {
                            it.copy(message = LoginMessage("Tu usuario no tiene tiendas activas.", offline = false))
                        } else {
                            it.copy(stores = result.stores)
                        }
                    }
                    LoginResult.InvalidCredentials -> mutableState.update {
                        it.copy(
                            stores = null,
                            password = "",
                            message = LoginMessage("Usuario o contraseña incorrectos.", offline = false),
                        )
                    }
                }
            } catch (e: ApiException) {
                mutableState.update {
                    it.copy(message = LoginMessage(e.message ?: "No se pudo iniciar sesión.", e.isConnectivity))
                }
            } finally {
                mutableState.update { it.copy(submitting = false, submittingStoreId = null) }
            }
        }
    }
}
