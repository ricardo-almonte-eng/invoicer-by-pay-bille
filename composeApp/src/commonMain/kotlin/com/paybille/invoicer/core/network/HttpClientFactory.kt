package com.paybille.invoicer.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** De dónde sale el token. Lo implementa la sesión; la red no sabe que existe Room. */
fun interface TokenProvider {
    suspend fun currentToken(): String?
}

val PayBilleJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * Cliente Ktor. Sin `engine` usa el de la plataforma que esté en las dependencias (OkHttp
 * en Android, Darwin en iOS): no hace falta expect/actual. Las pruebas pasan un MockEngine.
 */
fun createHttpClient(tokenProvider: TokenProvider, engine: HttpClientEngine? = null): HttpClient {
    val setup: HttpClientConfig<*>.() -> Unit = { configure(tokenProvider) }
    return if (engine == null) HttpClient(setup) else HttpClient(engine, setup)
}

/**
 * Cliente SIN token, para descargar imágenes. `products.Image` puede apuntar a cualquier sitio
 * (el POS deja pegar imágenes de Google): el token de la sesión solo viaja a nuestra API.
 */
fun createPlainHttpClient(engine: HttpClientEngine? = null): HttpClient {
    val setup: HttpClientConfig<*>.() -> Unit = {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }
    return if (engine == null) HttpClient(setup) else HttpClient(engine, setup)
}

private fun HttpClientConfig<*>.configure(tokenProvider: TokenProvider) {
    // Los códigos de error los traduce PayBilleApi; Ktor no debe lanzar por su cuenta.
    expectSuccess = false

    install(ContentNegotiation) { json(PayBilleJson) }

    install(HttpTimeout) {
        // 2 min por petición, igual que el POS. La conexión, en cambio, falla rápido:
        // sin red el usuario debe seguir con lo local, no mirar un indicador 2 minutos.
        requestTimeoutMillis = 120_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 120_000
    }

    install(Logging) { level = LogLevel.NONE }

    install(
        createClientPlugin("PayBilleAuth") {
            onRequest { request, _ ->
                // El token va CRUDO en Authorization, SIN "Bearer ": así lo espera
                // `auth()` de la API (jsonwebtoken.js) y así lo manda el POS.
                val token = tokenProvider.currentToken()
                if (!token.isNullOrBlank() && !request.headers.contains(HttpHeaders.Authorization)) {
                    request.headers.append(HttpHeaders.Authorization, token)
                }
            }
        },
    )
}
