package com.paybille.invoicer.core.di

import androidx.room.Room
import androidx.room.RoomDatabase
import com.paybille.invoicer.core.database.DATABASE_NAME
import com.paybille.invoicer.core.database.InvoicerDatabase
import com.paybille.invoicer.core.platform.AndroidDocumentPlatform
import com.paybille.invoicer.core.platform.AndroidReminderScheduler
import com.paybille.invoicer.core.platform.DocumentPlatform
import com.paybille.invoicer.core.platform.ReminderScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<DocumentPlatform> { AndroidDocumentPlatform(androidContext().applicationContext) }
    single<ReminderScheduler> { AndroidReminderScheduler(androidContext().applicationContext) }
    single<RoomDatabase.Builder<InvoicerDatabase>> {
        val context = androidContext().applicationContext
        Room.databaseBuilder<InvoicerDatabase>(
            context = context,
            name = context.getDatabasePath(DATABASE_NAME).absolutePath,
        )
    }
}
