package com.paybille.invoicer.feature.auth.data.remote

import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.PayBilleJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

/** Respuesta ya interpretada de `POST users/login`. */
sealed interface RemoteLogin {
    data class Authenticated(val token: String, val user: UserDto) : RemoteLogin
    data class MarketRequired(val markets: List<MarketOptionDto>) : RemoteLogin
    data object InvalidCredentials : RemoteLogin
}

/**
 * Llamadas de sesión, las mismas que encadena `PayBille_POS/stores/data/user.js`.
 *
 * Los métodos del perfil reciben el token explícito: durante el login todavía no hay
 * sesión guardada de la que el cliente HTTP pueda leerlo.
 */
class AuthRemoteDataSource(private val api: PayBilleApi) {

    /**
     * Login multitienda en una o dos fases (`PayBille_API/src/adapters/controllers/users.js`).
     *
     * `IdMarket` solo va en la segunda fase. `RapidLogin` NO se manda nunca: el backend lo
     * escribe en `Users.IndRapidLogin` en cuanto viene, aunque sea `false`, y esa preferencia
     * es la misma que usa el POS. Mandarlo desde aquí se la cambiaría a escondidas.
     */
    suspend fun login(username: String, password: String, idMarket: Int?): RemoteLogin {
        val body = buildJsonObject {
            put("key", api.apiKey)
            put("username", username)
            put("password", password)
            if (idMarket != null) put("IdMarket", idMarket)
        }
        val data = api.get("users", body, route = "login")

        // Credenciales malas: HTTP 200 con un string en `data`. Así responde la API.
        if (data is JsonPrimitive) return RemoteLogin.InvalidCredentials

        val response = decode<LoginResponseDto>(data)
        val token = response.token
        return when {
            response.requiresMarket -> RemoteLogin.MarketRequired(response.markets)
            !token.isNullOrBlank() && response.user != null -> RemoteLogin.Authenticated(token, response.user)
            else -> throw ApiException("El servidor no devolvió una sesión.", ApiException.Kind.Unexpected)
        }
    }

    /**
     * Tiendas del usuario en sesión (`marketbyuser/mine`), para el selector de la cabecera.
     * Solo las que tiene activas; un usuario "heredado" sin filas en `MarketByUser` recibe la
     * suya y nada más.
     */
    suspend fun myMarkets(): MyMarketsDto = decode(api.post("marketbyuser", JsonObject(emptyMap()), route = "mine"))

    /**
     * Cambia la tienda activa (`marketbyuser/switch`) y devuelve un TOKEN NUEVO: el servidor
     * filtra cada consulta por el `IdMarket` que va dentro del JWT, así que sin él la API
     * seguiría respondiendo con la tienda anterior.
     *
     * Igual que en el login, `RapidLogin` NO se manda: el backend solo lo escribe si viene, y
     * es la preferencia del POS. Sí cambia `Users.IdMarket`: el POS con login rápido entrará
     * después en la tienda elegida aquí (lo mismo que hace la segunda fase del login).
     */
    suspend fun switchMarket(idMarket: Int): RemoteLogin.Authenticated {
        val data = api.post("marketbyuser", buildJsonObject { put("IdMarket", idMarket) }, route = "switch")
        val response = decode<SwitchMarketDto>(data)
        val token = response.token
        if (token.isNullOrBlank() || response.user == null) {
            throw ApiException("El servidor no devolvió la sesión de la tienda.", ApiException.Kind.Unexpected)
        }
        return RemoteLogin.Authenticated(token, response.user)
    }

    suspend fun person(id: Int, token: String?): PersonDto =
        decode(api.getById("persons", id, token))

    suspend fun role(id: Int, token: String?): RoleDto =
        decode(api.getById("roles", id, token))

    suspend fun market(id: Int, token: String?): MarketDto =
        decode(api.getById("markets", id, token))

    /** `Settings` por tienda. El POS la crea si no existe; aquí solo se lee. */
    suspend fun settings(idMarket: Int, token: String?): SettingsDto? {
        val data = api.getGeneric("Settings", buildJsonObject { put("IdMarket", idMarket) }, token = token)
        val first = (data as? JsonArray)?.firstOrNull() ?: return null
        return decode(first)
    }

    private inline fun <reified T> decode(element: JsonElement): T {
        if (element !is JsonObject && element !is JsonArray) {
            throw ApiException("El servidor respondió algo inesperado.", ApiException.Kind.Unexpected)
        }
        return try {
            PayBilleJson.decodeFromJsonElement<T>(element)
        } catch (e: SerializationException) {
            throw ApiException("El servidor respondió algo inesperado.", ApiException.Kind.Unexpected, cause = e)
        } catch (e: IllegalArgumentException) {
            throw ApiException("El servidor respondió algo inesperado.", ApiException.Kind.Unexpected, cause = e)
        }
    }
}
