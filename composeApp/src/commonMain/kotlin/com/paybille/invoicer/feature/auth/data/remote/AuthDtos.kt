package com.paybille.invoicer.feature.auth.data.remote

import com.paybille.invoicer.core.network.LenientBooleanSerializer
import com.paybille.invoicer.core.network.LenientDoubleSerializer
import com.paybille.invoicer.core.network.LenientIntSerializer
import com.paybille.invoicer.core.network.LenientStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Formas REALES de la API (PayBille_API/src). Los nombres de campo se escriben como los
 * devuelve el backend, rarezas incluidas: `taxValue` en camelCase, el resto en PascalCase.
 */

/**
 * `users` tal como viaja en la respuesta del login (y dentro del JWT).
 * ⚠️ La API también manda `Password` en claro: aquí NO se declara, así que se descarta
 * al decodificar y nunca llega a la base local.
 */
@Serializable
data class UserDto(
    val id: Int,
    @SerialName("Username") val username: String? = null,
    @SerialName("IdPerson") @Serializable(LenientIntSerializer::class) val idPerson: Int? = null,
    @SerialName("IdRol") @Serializable(LenientIntSerializer::class) val idRol: Int? = null,
    @SerialName("IdMarket") @Serializable(LenientIntSerializer::class) val idMarket: Int? = null,
    @SerialName("Torning") @Serializable(LenientStringSerializer::class) val torning: String? = null,
)

/** Tienda del selector de la primera fase (`MARKET_ATTRS` en el repositorio de la API). */
@Serializable
data class MarketOptionDto(
    val id: Int,
    @SerialName("Name") val name: String = "",
    @SerialName("Address") val address: String? = null,
    @SerialName("Image") val image: String? = null,
)

/**
 * `data` de `POST users/login`. Tres formas posibles:
 * - `{ user, token }` → sesión.
 * - `{ requiresMarket: true, markets, user }` → SIN token; hay que elegir tienda y repetir.
 * - el string `"Incorrect username or password"` (con HTTP 200). Ese caso se detecta antes
 *   de decodificar este DTO.
 */
@Serializable
data class LoginResponseDto(
    val token: String? = null,
    val user: UserDto? = null,
    val requiresMarket: Boolean = false,
    val markets: List<MarketOptionDto> = emptyList(),
)

/**
 * `data` de `POST marketbyuser/mine`: tiendas entre las que puede elegir el usuario del token
 * y la activa. `IndRapidLogin` también viene, pero es la preferencia del POS y aquí no se toca.
 */
@Serializable
data class MyMarketsDto(
    val markets: List<MarketOptionDto> = emptyList(),
    @SerialName("IdMarket") @Serializable(LenientIntSerializer::class) val idMarket: Int? = null,
)

/** `data` de `POST marketbyuser/switch`: la misma forma que un login correcto. */
@Serializable
data class SwitchMarketDto(
    val token: String? = null,
    val user: UserDto? = null,
)

@Serializable
data class PersonDto(
    val id: Int,
    @SerialName("FirstName") val firstName: String? = null,
    @SerialName("LastName") val lastName: String? = null,
)

@Serializable
data class RoleDto(
    val id: Int,
    @SerialName("Name") val name: String? = null,
)

@Serializable
data class MarketDto(
    val id: Int,
    @SerialName("Name") val name: String = "",
    @SerialName("Address") val address: String? = null,
    @SerialName("Phone") val phone: String? = null,
    @SerialName("RNC") val rnc: String? = null,
    @SerialName("Image") val image: String? = null,
    // DECIMAL(5,2): llega como string ("0.18"). Nunca operes con él sin normalizar.
    @Serializable(LenientDoubleSerializer::class) val taxValue: Double? = null,
    val taxLabel: String? = null,
    val taxType: String? = null,
    @SerialName("TimeZone") val timeZone: String? = null,
)

@Serializable
data class SettingsDto(
    val id: Int? = null,
    @SerialName("IdMarket") @Serializable(LenientIntSerializer::class) val idMarket: Int? = null,
    @SerialName("Tax") @Serializable(LenientDoubleSerializer::class) val tax: Double? = null,
    @SerialName("LogoInBill") @Serializable(LenientBooleanSerializer::class) val logoInBill: Boolean? = null,
)
