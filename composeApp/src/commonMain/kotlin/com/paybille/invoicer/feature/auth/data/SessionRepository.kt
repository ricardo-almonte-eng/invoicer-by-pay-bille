package com.paybille.invoicer.feature.auth.data

import com.paybille.invoicer.core.database.UserDataCleaner
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.feature.auth.data.local.SessionDao
import com.paybille.invoicer.feature.auth.data.local.SessionEntity
import com.paybille.invoicer.feature.auth.data.remote.AuthRemoteDataSource
import com.paybille.invoicer.feature.auth.data.remote.MarketDto
import com.paybille.invoicer.feature.auth.data.remote.PersonDto
import com.paybille.invoicer.feature.auth.data.remote.RemoteLogin
import com.paybille.invoicer.feature.auth.data.remote.RoleDto
import com.paybille.invoicer.feature.auth.data.remote.SettingsDto
import com.paybille.invoicer.feature.auth.domain.LoginResult
import com.paybille.invoicer.feature.auth.domain.ProfileSyncResult
import com.paybille.invoicer.feature.auth.domain.Session
import com.paybille.invoicer.feature.auth.domain.SessionState
import com.paybille.invoicer.feature.auth.domain.StoreOption
import com.paybille.invoicer.feature.auth.domain.StoreSwitchBlockedException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Dueño de la sesión. Offline first:
 *
 * - **La base local es la verdad.** La app arranca leyendo Room, sin red, y la pantalla
 *   cambia sola cuando la fila de sesión aparece o desaparece ([state]).
 * - **La sesión no vence.** Solo [logout] la borra. Un 401/403 del servidor NO cierra la
 *   sesión: se informa y el usuario decide (decisión del 2026-09-16).
 * - El login sí necesita red, por definición. Todo lo demás (perfil, tienda, ajustes) se
 *   refresca en segundo plano con [refreshProfile] y, si falla, se sigue con lo guardado.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class SessionRepository(
    private val dao: SessionDao,
    private val remote: AuthRemoteDataSource,
    private val tokens: SessionTokenStore,
    private val cleaner: UserDataCleaner,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    val state: Flow<SessionState> = dao.observeSession()
        .flatMapLatest { row ->
            if (row == null) {
                flowOf(SessionState.SignedOut)
            } else {
                combine(dao.observeMarket(row.idMarket), dao.observeSettings(row.idMarket)) { market, settings ->
                    SessionState.SignedIn(row.toDomain(market, settings))
                }
            }
        }
        .onStart { emit(SessionState.Loading) }
        .distinctUntilChanged()

    /**
     * Primera fase con `storeId = null`. Si responde [LoginResult.StoreRequired], se repite
     * con la tienda elegida: el `IdMarket` viaja DENTRO del token y no se puede emitir
     * antes de saberlo.
     *
     * @throws ApiException sin red, por timeout o si el servidor rechaza la tienda.
     */
    suspend fun login(username: String, password: String, storeId: Int? = null): LoginResult =
        when (val result = remote.login(username.trim(), password, storeId)) {
            RemoteLogin.InvalidCredentials -> LoginResult.InvalidCredentials
            is RemoteLogin.MarketRequired -> LoginResult.StoreRequired(result.markets.map { it.toOption() })
            is RemoteLogin.Authenticated -> {
                saveAuthenticated(result)
                LoginResult.Success
            }
        }

    /** Tiendas entre las que puede cambiar el usuario en sesión. Necesita red. */
    suspend fun availableStores(): List<StoreOption> = remote.myMarkets().markets.map { it.toOption() }

    /**
     * Cambia de tienda SIN volver a pedir la contraseña: `marketbyuser/switch` devuelve un
     * token nuevo con el `IdMarket` dentro, y con él se rehace la cascada del perfil.
     *
     * Los datos locales de la tienda anterior (ventas, catálogos, borradores, avisos) se
     * borran justo antes de guardar la sesión nueva; la sesión no desaparece en ningún momento,
     * así que la app no pasa por el login. `App` recrea el armazón al cambiar el `IdMarket`.
     *
     * @throws StoreSwitchBlockedException si hay documentos sin enviar: saldrían con el token
     *   nuevo y acabarían en la otra tienda.
     * @throws ApiException sin red o si el servidor rechaza la tienda.
     */
    suspend fun switchStore(idMarket: Int) {
        val pending = cleaner.pendingDocuments()
        if (pending > 0) throw StoreSwitchBlockedException(pending)
        val previousToken = dao.getSession()?.token
        val auth = remote.switchMarket(idMarket)
        saveAuthenticated(auth, previousToken = previousToken, beforeSave = { cleaner.clearStoreData() })
    }

    /** La sesión abierta, esperando a que Room responda; `null` si no hay. */
    suspend fun currentSession(): Session? =
        (state.first { it !is SessionState.Loading } as? SessionState.SignedIn)?.session

    /** Borra la sesión y todos los datos locales del usuario. Es la ÚNICA forma de salir. */
    suspend fun logout() {
        cleaner.clearAll()
        tokens.set(null)
    }

    /**
     * Refresca persona, rol, tienda y ajustes. Lo que llegue se guarda aunque otra parte
     * falle: una tienda actualizada vale más que ninguna.
     */
    suspend fun refreshProfile(): ProfileSyncResult {
        val row = dao.getSession() ?: return ProfileSyncResult.Synced
        val profile = fetchProfile(row.idPerson, row.idRol, row.idMarket, token = null)

        val updated = row.copy(
            firstName = profile.person?.firstName ?: row.firstName,
            lastName = profile.person?.lastName ?: row.lastName,
            roleName = profile.role?.name ?: row.roleName,
            profileSyncedAt = if (profile.failure == null) now() else row.profileSyncedAt,
        )
        // Si el usuario cerró sesión mientras tanto, no se resucita la fila.
        dao.saveProfileIfCurrent(
            token = row.token,
            session = updated,
            market = profile.market?.toEntity(),
            settings = profile.settings?.toEntity(row.idMarket),
        )

        val failure = profile.failure ?: return ProfileSyncResult.Synced
        return if (failure.isConnectivity) {
            ProfileSyncResult.Offline
        } else {
            ProfileSyncResult.Failed(failure.message ?: "No se pudo actualizar la tienda.")
        }
    }

    /**
     * @param previousToken el token que debe quedar si guardar falla (cambio de tienda); en el
     *   login no hay ninguno.
     * @param beforeSave se ejecuta con el token nuevo ya en la caché y justo antes de escribir
     *   la sesión.
     */
    private suspend fun saveAuthenticated(
        auth: RemoteLogin.Authenticated,
        previousToken: String? = null,
        beforeSave: suspend () -> Unit = {},
    ) {
        val user = auth.user
        val idMarket = user.idMarket
            ?: throw ApiException("Tu usuario no tiene una tienda asignada.", ApiException.Kind.Server)
        val idPerson = user.idPerson
            ?: throw ApiException("Tu usuario no tiene una ficha de persona.", ApiException.Kind.Server)

        // El perfil es "mejor esfuerzo": un login correcto nunca se pierde porque la
        // tienda no cargó. Queda `profileSyncedAt = null` y el Inicio lo reintenta.
        val profile = fetchProfile(idPerson, user.idRol, idMarket, auth.token)
        val loggedInAt = now()

        val session = SessionEntity(
            token = auth.token,
            userId = user.id,
            username = user.username.orEmpty(),
            idPerson = idPerson,
            idRol = user.idRol ?: 0,
            idMarket = idMarket,
            torning = user.torning,
            firstName = profile.person?.firstName,
            lastName = profile.person?.lastName,
            roleName = profile.role?.name,
            loggedInAt = loggedInAt,
            profileSyncedAt = if (profile.failure == null) loggedInAt else null,
        )
        // El token va a la caché ANTES que la fila: en cuanto la fila aparece, el Inicio se
        // monta y pide datos, y no debe encontrarse la caché vacía del arranque sin sesión.
        tokens.set(auth.token)
        try {
            beforeSave()
            dao.saveLogin(session, profile.market?.toEntity(), profile.settings?.toEntity(idMarket))
        } catch (e: Throwable) {
            tokens.set(previousToken)
            throw e
        }
    }

    private suspend fun fetchProfile(idPerson: Int, idRol: Int?, idMarket: Int, token: String?): Profile =
        coroutineScope {
            val personCall = async { attempt { remote.person(idPerson, token) } }
            val roleCall = async {
                if (idRol == null || idRol == 0) Attempt<RoleDto>(null) else attempt { remote.role(idRol, token) }
            }
            val marketCall = async { attempt { remote.market(idMarket, token) } }
            val settingsCall = async { attempt { remote.settings(idMarket, token) } }

            val person = personCall.await()
            val role = roleCall.await()
            val market = marketCall.await()
            val settings = settingsCall.await()
            Profile(
                person = person.value,
                role = role.value,
                market = market.value,
                settings = settings.value,
                failure = person.error ?: role.error ?: market.error ?: settings.error,
            )
        }

    private suspend fun <T> attempt(block: suspend () -> T): Attempt<T> = try {
        Attempt(block())
    } catch (e: ApiException) {
        Attempt(null, e)
    }

    private data class Attempt<T>(val value: T?, val error: ApiException? = null)

    private data class Profile(
        val person: PersonDto?,
        val role: RoleDto?,
        val market: MarketDto?,
        val settings: SettingsDto?,
        val failure: ApiException?,
    )
}
