package com.paybille.invoicer.feature.banks.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Una cuenta de dinero (`cuentas`): caja o banco. El balance lo lleva el servidor. */
@Entity(tableName = "bank_accounts", indices = [Index("idMarket")])
data class BankAccountEntity(
    @PrimaryKey val id: Int,
    val idMarket: Int,
    val name: String,
    /** `Ahorros` · `Cheque` · `Corriente` · `Nomina` · `Empresarial` · `Caja`. */
    val type: String,
    val bankName: String?,
    val accountNumber: String?,
    val description: String?,
    val active: Boolean,
    val balance: Double,
    val syncedAt: Long,
    /** Titular y su cédula/RNC (API `F4`). Salen en "Dónde pagar" del PDF. */
    val holderName: String? = null,
    val holderId: String? = null,
) {
    /** Se puede ofrecer para transferir: banco activo y con número (una caja no). */
    val canReceiveTransfers: Boolean get() = active && type != "Caja" && !accountNumber.isNullOrBlank()
}

/** Movimiento de una cuenta (`cuentasMovimientos`), con el balance antes y después. */
@Entity(tableName = "account_movements", indices = [Index("idCuenta")])
data class AccountMovementEntity(
    @PrimaryKey val id: Int,
    val idMarket: Int,
    val idCuenta: Int,
    /** `Ingreso` · `Egreso`. */
    val type: String,
    val amount: Double,
    val balanceBefore: Double?,
    val balanceAfter: Double?,
    val description: String?,
    val reference: String?,
    /** `Venta` · `Compra` · `Ajuste` · `Transferencia` · `Gasto` · `CuentaDoc` · `Cuota`… */
    val referenceType: String?,
    val username: String?,
    val createdAt: Long?,
    val syncedAt: Long,
)

@Dao
interface BankDao {

    @Query("SELECT * FROM bank_accounts WHERE idMarket = :idMarket ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAccounts(idMarket: Int): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM bank_accounts WHERE id = :id")
    fun observeAccount(id: Int): Flow<BankAccountEntity?>

    @Upsert
    suspend fun upsertAccounts(rows: List<BankAccountEntity>)

    @Transaction
    suspend fun replaceAccounts(idMarket: Int, rows: List<BankAccountEntity>) {
        deleteAccounts(idMarket)
        upsertAccounts(rows)
    }

    @Query("DELETE FROM bank_accounts WHERE idMarket = :idMarket")
    suspend fun deleteAccounts(idMarket: Int)

    /** Movimientos de la cuenta en `[from, to)` (epoch ms), los más nuevos primero. */
    @Query(
        """
        SELECT * FROM account_movements
        WHERE idCuenta = :idCuenta AND createdAt >= :from AND createdAt < :to
        ORDER BY createdAt DESC, id DESC
        """,
    )
    fun observeMovements(idCuenta: Int, from: Long, to: Long): Flow<List<AccountMovementEntity>>

    /** Lo descargado de un rango sustituye a lo guardado en ese rango (lo borrado en el POS se va). */
    @Transaction
    suspend fun replaceMovements(idCuenta: Int, from: Long, to: Long, rows: List<AccountMovementEntity>) {
        deleteMovements(idCuenta, from, to)
        upsertMovements(rows)
    }

    @Query("DELETE FROM account_movements WHERE idCuenta = :idCuenta AND createdAt >= :from AND createdAt < :to")
    suspend fun deleteMovements(idCuenta: Int, from: Long, to: Long)

    @Upsert
    suspend fun upsertMovements(rows: List<AccountMovementEntity>)

    @Query("DELETE FROM bank_accounts")
    suspend fun clearAccounts()

    @Query("DELETE FROM account_movements")
    suspend fun clearMovements()
}
