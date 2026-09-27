package com.paybille.invoicer

import android.app.Application
import com.paybille.invoicer.core.di.initKoin
import org.koin.android.ext.koin.androidContext

class InvoicerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@InvoicerApp) }
    }
}
