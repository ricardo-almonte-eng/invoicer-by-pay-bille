package com.paybille.invoicer.core.database

import com.paybille.invoicer.core.platform.ReminderScheduler
import com.paybille.invoicer.feature.auth.data.local.SessionDao
import com.paybille.invoicer.feature.banks.data.local.BankDao
import com.paybille.invoicer.feature.clients.data.local.ClientDao
import com.paybille.invoicer.feature.products.data.local.ProductDao
import com.paybille.invoicer.feature.detail.data.InvoicePdfStore
import com.paybille.invoicer.feature.detail.data.local.DetailDao
import com.paybille.invoicer.feature.invoice.data.local.InvoiceDao
import com.paybille.invoicer.feature.sales.data.local.SalesDao

/**
 * Borra todo lo del usuario al cerrar sesión. **Cada tabla, archivo o aviso nuevo con datos
 * del negocio se añade aquí**, o el siguiente usuario del teléfono vería lo del anterior.
 *
 * La sesión se borra la última: al desaparecer, `App` vuelve al login.
 */
class UserDataCleaner(
    private val sessionDao: SessionDao,
    private val salesDao: SalesDao,
    private val invoiceDao: InvoiceDao,
    private val detailDao: DetailDao,
    private val payloads: PayloadDao,
    private val products: ProductDao,
    private val clients: ClientDao,
    private val banks: BankDao,
    private val pdfs: InvoicePdfStore,
    private val reminders: ReminderScheduler,
) {
    suspend fun clearAll() {
        clearStoreData()
        sessionDao.clearSessionTables()
    }

    /** Documentos guardados en el teléfono que todavía no llegaron al servidor. */
    suspend fun pendingDocuments(): Int = invoiceDao.outbox().size

    /**
     * Todo lo del negocio, SIN la sesión: lo usa también el cambio de tienda, que conserva al
     * usuario pero no puede enseñar ventas, catálogos ni avisos de la tienda anterior.
     */
    suspend fun clearStoreData() {
        // Incluye la cola de envíos: quien cierra sesión con documentos sin enviar los pierde.
        // Mi perfil lo avisa antes, y el cambio de tienda ni siquiera empieza si hay alguno.
        invoiceDao.clearOutbox()
        invoiceDao.clearDrafts()
        detailDao.clearDetails()
        detailDao.clearReceivables()
        salesDao.clear()
        payloads.clear()
        products.clear()
        clients.clear()
        banks.clearMovements()
        banks.clearAccounts()
        runCatching { pdfs.clearAll() }
        // Un aviso de "hoy vence" de la cuenta o tienda anterior no debe sonar.
        runCatching { reminders.cancelAll() }
    }
}
