package com.paybille.invoicer.core.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.paybille.invoicer.feature.auth.data.local.MarketEntity
import com.paybille.invoicer.feature.banks.data.local.AccountMovementEntity
import com.paybille.invoicer.feature.banks.data.local.BankAccountEntity
import com.paybille.invoicer.feature.banks.data.local.BankDao
import com.paybille.invoicer.feature.clients.data.local.ClientDao
import com.paybille.invoicer.feature.clients.data.local.ClientEntity
import com.paybille.invoicer.feature.products.data.local.ProductDao
import com.paybille.invoicer.feature.products.data.local.ProductEntity
import com.paybille.invoicer.feature.auth.data.local.SessionDao
import com.paybille.invoicer.feature.auth.data.local.SessionEntity
import com.paybille.invoicer.feature.auth.data.local.SettingsEntity
import com.paybille.invoicer.feature.detail.data.local.DetailDao
import com.paybille.invoicer.feature.detail.data.local.ReceivableEntity
import com.paybille.invoicer.feature.detail.data.local.SaleDetailEntity
import com.paybille.invoicer.feature.invoice.data.local.DraftEntity
import com.paybille.invoicer.feature.invoice.data.local.InvoiceDao
import com.paybille.invoicer.feature.invoice.data.local.OutboxEntity
import com.paybille.invoicer.feature.sales.data.local.SaleEntity
import com.paybille.invoicer.feature.sales.data.local.SalesDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

const val DATABASE_NAME = "invoicer.db"

/**
 * Base local: la fuente de verdad de la app (offline first). La API se consulta para
 * refrescarla y para enviarle lo que se hizo sin red, nunca para pintar una pantalla.
 *
 * Al añadir una tabla, sube `version` y escribe la migración: la sesión vive aquí y
 * `fallbackToDestructiveMigration` cerraría la sesión del usuario en cada actualización.
 */
@Database(
    entities = [
        SessionEntity::class,
        MarketEntity::class,
        SettingsEntity::class,
        SaleEntity::class,
        DraftEntity::class,
        OutboxEntity::class,
        SaleDetailEntity::class,
        ReceivableEntity::class,
        CachedPayloadEntity::class,
        ProductEntity::class,
        ClientEntity::class,
        BankAccountEntity::class,
        AccountMovementEntity::class,
    ],
    version = 6,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // + sales
        AutoMigration(from = 2, to = 3), // + invoice_drafts, invoice_outbox
        AutoMigration(from = 3, to = 4), // + sale_details, receivables
        AutoMigration(from = 4, to = 5), // + cached_payloads, products, clients, bank_accounts, account_movements
        AutoMigration(from = 5, to = 6), // + bank_accounts.holderName, holderId
    ],
)
@ConstructedBy(InvoicerDatabaseConstructor::class)
abstract class InvoicerDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun salesDao(): SalesDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun detailDao(): DetailDao
    abstract fun payloadDao(): PayloadDao
    abstract fun productDao(): ProductDao
    abstract fun clientDao(): ClientDao
    abstract fun bankDao(): BankDao
}

// Room genera los `actual` de cada plataforma.
@Suppress("KotlinNoActualForExpect")
expect object InvoicerDatabaseConstructor : RoomDatabaseConstructor<InvoicerDatabase> {
    override fun initialize(): InvoicerDatabase
}

/** Cada plataforma aporta dónde vive el archivo (ver `platformModule`); lo demás es común. */
fun buildInvoicerDatabase(builder: RoomDatabase.Builder<InvoicerDatabase>): InvoicerDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
