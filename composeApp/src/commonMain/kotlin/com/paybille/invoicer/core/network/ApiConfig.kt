package com.paybille.invoicer.core.network

import com.paybille.invoicer.BuildKonfig

/**
 * Las dos bases de la API de PayBille (la misma del POS):
 * - `baseUrl`: endpoints de negocio (`users/login`, `accountdocs/…`, `nfc/…`).
 * - `genericUrl`: CRUD por modelo (`get/{modelo}`, `like/{modelo}`, `{modelo}/{id}`).
 *
 * Se configuran en `local.properties` (`paybille.apiBaseUrl`, `paybille.apiKey`).
 *
 * `webBaseUrl` es la web del POS (`paybille.webBaseUrl`): no es la API, es donde se abre el
 * catálogo público que se comparte.
 */
data class ApiConfig(
    val baseUrl: String,
    val apiKey: String,
    val webBaseUrl: String = DEFAULT_WEB_BASE_URL,
) {
    val genericUrl: String get() = "$baseUrl/generic"

    companion object {
        fun fromBuild() = ApiConfig(
            baseUrl = BuildKonfig.API_BASE_URL.trimEnd('/'),
            apiKey = BuildKonfig.API_KEY,
            webBaseUrl = BuildKonfig.WEB_BASE_URL.trimEnd('/'),
        )

        const val DEFAULT_WEB_BASE_URL = "https://paybille.com"
    }
}
