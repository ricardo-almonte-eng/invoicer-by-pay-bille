package com.paybille.invoicer.feature.banks.data

import com.paybille.invoicer.core.format.DateRange
import com.paybille.invoicer.core.format.epochBounds
import com.paybille.invoicer.core.format.parseApiTimestamp
import com.paybille.invoicer.core.network.LenientBooleanSerializer
import com.paybille.invoicer.core.network.LenientDoubleSerializer
import com.paybille.invoicer.core.network.LenientIntSerializer
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.decodeApi
import com.paybille.invoicer.core.network.decodeApiList
import com.paybille.invoicer.feature.auth.domain.Session
import com.paybille.invoicer.feature.banks.data.local.AccountMovementEntity
import com.paybille.invoicer.feature.banks.data.local.BankAccountEntity
import com.paybille.invoicer.feature.banks.data.local.BankDao
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock

/** Tipos de `cuentas.Type` (`sql/F1_esquema_cuentas.sql`). `Nomina` **sin tilde**: es el valor del ENUM. */
val ACCOUNT_TYPES = listOf(
    "Caja" to "Efectivo (caja)",
    "Ahorros" to "Ahorros",
    "Corriente" to "Corriente",
    "Cheque" to "Cheque",
    "Nomina" to "Nómina",
    "Empresarial" to "Empresarial",
)

/** Bancos que ofrece el POS (`Models/Bancos.js`), con el mismo texto que guarda. */
val BANKS = listOf(
    "APAP", "Azul", "Banreservas", "BHD", "Cardnet", "Caribe", "Cibao", "MIO",
    "Popular", "Proamérica", "Qik", "Santa Cruz", "Scotiabank", "Vimenca",
)

@Serializable
data class CuentaDto(
    val id: Int,
    @SerialName("IdMarket") @Serializable(LenientIntSerializer::class) val idMarket: Int? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("BankName") val bankName: String? = null,
    @SerialName("AccountNumber") val accountNumber: String? = null,
    /** Titular (API `F4`): lo que pide una transferencia desde otro banco. */
    @SerialName("HolderName") val holderName: String? = null,
    /** Cédula o RNC del titular. */
    @SerialName("HolderId") val holderId: String? = null,
    @SerialName("Description") val description: String? = null,
    @SerialName("Active") @Serializable(LenientBooleanSerializer::class) val active: Boolean? = null,
    @SerialName("Balance") @Serializable(LenientDoubleSerializer::class) val balance: Double? = null,
)

@Serializable
data class MovementDto(
    val id: Int,
    @SerialName("IdCuenta") @Serializable(LenientIntSerializer::class) val idCuenta: Int? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Amount") @Serializable(LenientDoubleSerializer::class) val amount: Double? = null,
    @SerialName("BalanceBefore") @Serializable(LenientDoubleSerializer::class) val balanceBefore: Double? = null,
    @SerialName("BalanceAfter") @Serializable(LenientDoubleSerializer::class) val balanceAfter: Double? = null,
    @SerialName("Description") val description: String? = null,
    @SerialName("Reference") val reference: String? = null,
    @SerialName("ReferenceType") val referenceType: String? = null,
    @SerialName("Username") val username: String? = null,
    val createdAt: String? = null,
)

/** El formulario de una cuenta. `Balance` no está: lo escribe solo el servidor. */
data class AccountForm(
    val name: String,
    val type: String,
    val bankName: String?,
    val accountNumber: String?,
    val description: String?,
    val active: Boolean,
    val holderName: String? = null,
    val holderId: String? = null,
)

data class MovementsPage(val items: List<MovementDto>, val hasNextPage: Boolean)

class BanksRemoteDataSource(private val api: PayBilleApi) {

    /** Todas las cuentas de la tienda, activas o no. */
    suspend fun accounts(idMarket: Int): List<CuentaDto> =
        decodeApiList(CuentaDto.serializer(), api.getGenericPage("cuentas", buildJsonObject { put("IdMarket", idMarket) }, page = 1, pageSize = 100).items)

    /**
     * Movimientos de una cuenta en el rango. ⚠️ El controlador (`controllers/cuentas.js`) solo lee
     * `params` del cuerpo y la página de la **query**: el POS manda `dateFrom`/`page` en el cuerpo
     * y por eso enseña siempre los 10 más recientes. Aquí la página va en la query y las fechas
     * como `createdAt__gte`/`__lte` dentro de `params`.
     */
    suspend fun movements(idCuenta: Int, range: DateRange, page: Int, pageSize: Int): MovementsPage {
        val result = api.getPage(
            "cuentas",
            buildJsonObject {
                putJsonObject("params") {
                    put("createdAt__gte", "${range.start} 00:00:00")
                    put("createdAt__lte", "${range.end} 23:59:59")
                }
            },
            route = "$idCuenta/movimientos",
            page = page,
            pageSize = pageSize,
        )
        return MovementsPage(decodeApiList(MovementDto.serializer(), result.items), result.hasNextPage)
    }

    suspend fun createAccount(idMarket: Int, form: AccountForm): CuentaDto =
        decodeApi(CuentaDto.serializer(), api.postGeneric("cuentas", form.toBody(idMarket, newAccount = true)))

    suspend fun updateAccount(idMarket: Int, id: Int, form: AccountForm) {
        api.put("cuentas", id, form.toBody(idMarket, newAccount = false))
    }

    /**
     * Movimiento manual (`NuevoMovimientoCuenta.vue`): el servidor bloquea la cuenta, apunta el
     * balance de antes y de después y actualiza `Cuentas.Balance`, todo en una transacción.
     */
    suspend fun createMovement(body: JsonObject) {
        api.post("cuentas", body, route = "movimientos")
    }

    private fun AccountForm.toBody(idMarket: Int, newAccount: Boolean) = buildJsonObject {
        put("Name", name.trim())
        put("Type", type)
        put("BankName", bankName?.trim()?.takeIf { it.isNotEmpty() })
        put("AccountNumber", accountNumber?.trim()?.takeIf { it.isNotEmpty() })
        put("HolderName", holderName?.trim()?.takeIf { it.isNotEmpty() })
        put("HolderId", holderId?.trim()?.takeIf { it.isNotEmpty() })
        put("Description", description?.trim()?.takeIf { it.isNotEmpty() })
        put("Active", active)
        put("IdMarket", idMarket)
        // Solo al crear, como el POS: después el balance lo mueven los movimientos.
        if (newAccount) put("Balance", 0)
    }
}

/**
 * Cuentas de dinero y sus movimientos, en Room. Crear, editar y el movimiento manual necesitan
 * red; después se relee lo que cambió.
 */
class BanksRepository(
    private val dao: BankDao,
    private val remote: BanksRemoteDataSource,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    fun observeAccounts(idMarket: Int): Flow<List<BankAccountEntity>> = dao.observeAccounts(idMarket)

    fun observeAccount(id: Int): Flow<BankAccountEntity?> = dao.observeAccount(id)

    fun observeMovements(idCuenta: Int, range: DateRange, timeZone: String): Flow<List<AccountMovementEntity>> {
        val (from, to) = range.epochBounds(timeZone)
        return dao.observeMovements(idCuenta, from, to)
    }

    suspend fun syncAccounts(idMarket: Int) {
        dao.replaceAccounts(idMarket, remote.accounts(idMarket).map { it.toEntity(idMarket, now()) })
    }

    /** Todo el rango (hasta [MAX_PAGES] páginas): los totales de ingresos y egresos salen de aquí. */
    suspend fun syncMovements(idMarket: Int, idCuenta: Int, range: DateRange, timeZone: String) {
        val rows = mutableListOf<AccountMovementEntity>()
        var page = 1
        do {
            val result = remote.movements(idCuenta, range, page, PAGE_SIZE)
            rows += result.items.map { it.toEntity(idMarket, idCuenta, now()) }
            page++
        } while (result.hasNextPage && page <= MAX_PAGES)
        val (from, to) = range.epochBounds(timeZone)
        dao.replaceMovements(idCuenta, from, to, rows)
    }

    suspend fun save(idMarket: Int, id: Int?, form: AccountForm): Int {
        val savedId = if (id == null) remote.createAccount(idMarket, form).id else id.also { remote.updateAccount(idMarket, it, form) }
        runCatching { syncAccounts(idMarket) }
        return savedId
    }

    /** `amount` siempre positivo: el signo lo da `type` (`Ingreso` / `Egreso`). */
    suspend fun addMovement(session: Session, idCuenta: Int, type: String, amount: Double, description: String?, reference: String?) {
        remote.createMovement(
            buildJsonObject {
                put("IdCuenta", idCuenta)
                put("Type", type)
                put("Amount", amount)
                put("Description", description?.trim()?.takeIf { it.isNotEmpty() } ?: "Movimiento manual")
                put("Reference", reference?.trim()?.takeIf { it.isNotEmpty() })
                put("ReferenceType", "Ajuste")
                put("IdMarket", session.idMarket)
                put("IdUser", session.userId)
                put("Username", session.displayName)
            },
        )
        runCatching { syncAccounts(session.idMarket) }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 10
    }
}

private fun CuentaDto.toEntity(fallbackMarket: Int, syncedAt: Long) = BankAccountEntity(
    id = id,
    idMarket = idMarket ?: fallbackMarket,
    name = name?.trim().orEmpty().ifBlank { "Cuenta $id" },
    type = type ?: "Ahorros",
    bankName = bankName?.trim()?.takeIf { it.isNotEmpty() },
    accountNumber = accountNumber?.trim()?.takeIf { it.isNotEmpty() },
    description = description?.trim()?.takeIf { it.isNotEmpty() },
    active = active != false,
    holderName = holderName?.trim()?.takeIf { it.isNotEmpty() },
    holderId = holderId?.trim()?.takeIf { it.isNotEmpty() },
    balance = balance ?: 0.0,
    syncedAt = syncedAt,
)

private fun MovementDto.toEntity(idMarket: Int, idCuenta: Int, syncedAt: Long) = AccountMovementEntity(
    id = id,
    idMarket = idMarket,
    idCuenta = this.idCuenta ?: idCuenta,
    type = type ?: "Ingreso",
    amount = amount ?: 0.0,
    balanceBefore = balanceBefore,
    balanceAfter = balanceAfter,
    description = description?.trim()?.takeIf { it.isNotEmpty() },
    reference = reference?.trim()?.takeIf { it.isNotEmpty() },
    referenceType = referenceType,
    username = username,
    createdAt = parseApiTimestamp(createdAt),
    syncedAt = syncedAt,
)
