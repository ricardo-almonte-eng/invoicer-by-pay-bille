package com.paybille.invoicer.feature.auth.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** La sesión es una sola fila: la app es de un solo usuario. */
const val SESSION_ROW_ID = 1

/**
 * Sesión guardada en el teléfono. **No vence**: el JWT de la API se firma a 100 años y la
 * app solo la borra cuando el usuario pulsa "Cerrar sesión".
 *
 * El token es una credencial. Vive en el almacenamiento privado de la app (fuera del
 * alcance de otras apps; en iOS, cifrado por Data Protection) y el manifiesto de
 * Android desactiva las copias de seguridad para que no salga del teléfono.
 */
@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey val id: Int = SESSION_ROW_ID,
    val token: String,
    val userId: Int,
    val username: String,
    val idPerson: Int,
    val idRol: Int,
    val idMarket: Int,
    /** Turno de caja del usuario. Invoicer no tiene turnos, pero la API lo espera en las ventas. */
    val torning: String?,
    val firstName: String?,
    val lastName: String?,
    /** Solo informativo: la app NO se ramifica por rol (guía 08 §8). */
    val roleName: String?,
    /** Epoch ms del inicio de sesión. */
    val loggedInAt: Long,
    /** Epoch ms del último refresco correcto del perfil (persona, tienda, ajustes). */
    val profileSyncedAt: Long?,
)

/** Copia local de la tienda activa. Se refresca en segundo plano cuando hay red. */
@Entity(tableName = "market")
data class MarketEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val address: String?,
    val phone: String?,
    val rnc: String?,
    val image: String?,
    /** Decimal (0.18 = 18 %). Ya normalizado: la API lo manda como string. */
    val taxValue: Double?,
    val taxLabel: String?,
    val taxType: String?,
    val timeZone: String?,
)

/** Fila de `Settings` de la tienda. De ella solo interesan `Tax` y `LogoInBill`. */
@Entity(tableName = "market_settings")
data class SettingsEntity(
    @PrimaryKey val idMarket: Int,
    val remoteId: Int?,
    val tax: Double?,
    val logoInBill: Boolean?,
)
