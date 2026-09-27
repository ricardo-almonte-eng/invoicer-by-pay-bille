package com.paybille.invoicer.feature.auth.domain

/** Tasa por defecto cuando la tienda no tiene `taxValue`. Solo prellena: la tasa se elige por factura. */
const val DEFAULT_TAX_RATE = 0.18

data class Session(
    val userId: Int,
    val username: String,
    val idPerson: Int,
    val idMarket: Int,
    val torning: String?,
    val firstName: String?,
    val lastName: String?,
    val roleName: String?,
    val store: StoreProfile?,
    val settings: StoreSettings?,
    val loggedInAt: Long,
    val profileSyncedAt: Long?,
) {
    /** `Username` que la API espera en las ventas: `${FirstName} ${LastName}`. */
    val displayName: String
        get() = listOfNotNull(firstName, lastName)
            .joinToString(" ") { it.trim() }
            .trim()
            .ifBlank { username }

    /** Tasa por DEFECTO de una factura nueva (decimal: 0.18 = 18 %). */
    val defaultTaxRate: Double get() = store?.taxValue ?: DEFAULT_TAX_RATE
}

data class StoreProfile(
    val id: Int,
    val name: String,
    val address: String?,
    val phone: String?,
    val rnc: String?,
    val image: String?,
    val taxValue: Double?,
    /** `ITBIS` salvo que la tienda diga otra cosa. */
    val taxLabel: String,
    val taxType: String?,
    val timeZone: String,
)

data class StoreSettings(
    val tax: Double?,
    val logoInBill: Boolean,
)

/** Tienda que el usuario puede elegir al entrar. */
data class StoreOption(
    val id: Int,
    val name: String,
    val address: String?,
)

sealed interface SessionState {
    /** Leyendo la base local. Dura milisegundos: no depende de la red. */
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val session: Session) : SessionState
}

sealed interface LoginResult {
    data object Success : LoginResult

    /** El usuario tiene varias tiendas: hay que elegir una y repetir el login con ella. */
    data class StoreRequired(val stores: List<StoreOption>) : LoginResult
    data object InvalidCredentials : LoginResult
}

/** No se cambia de tienda con documentos sin enviar: saldrían a nombre de la otra. */
class StoreSwitchBlockedException(val pending: Int) :
    IllegalStateException("Hay $pending documentos sin enviar.")

/** Resultado de refrescar el perfil en segundo plano. */
sealed interface ProfileSyncResult {
    data object Synced : ProfileSyncResult

    /** Sin red: se sigue con la copia local, que es lo normal en offline first. */
    data object Offline : ProfileSyncResult
    data class Failed(val message: String) : ProfileSyncResult
}
