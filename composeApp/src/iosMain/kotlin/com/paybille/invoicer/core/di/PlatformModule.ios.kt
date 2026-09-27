package com.paybille.invoicer.core.di

import androidx.room.Room
import androidx.room.RoomDatabase
import com.paybille.invoicer.core.database.DATABASE_NAME
import com.paybille.invoicer.core.database.InvoicerDatabase
import com.paybille.invoicer.core.platform.DocumentPlatform
import com.paybille.invoicer.core.platform.IosDocumentPlatform
import com.paybille.invoicer.core.platform.IosReminderScheduler
import com.paybille.invoicer.core.platform.ReminderScheduler
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual val platformModule: Module = module {
    single<DocumentPlatform> { IosDocumentPlatform() }
    single<ReminderScheduler> { IosReminderScheduler() }
    single<RoomDatabase.Builder<InvoicerDatabase>> {
        Room.databaseBuilder<InvoicerDatabase>(name = "${applicationSupportDirectory()}/$DATABASE_NAME")
    }
}

/**
 * `Application Support` y no `Documents`: los datos de la app no deben aparecer en la app
 * Archivos del iPhone. Sigue dentro del sandbox, cifrado por Data Protection.
 */
@OptIn(ExperimentalForeignApi::class)
private fun applicationSupportDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(url?.path) { "No se pudo abrir Application Support" }
}
