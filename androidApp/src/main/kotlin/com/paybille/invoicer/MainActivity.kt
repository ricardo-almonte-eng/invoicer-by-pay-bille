package com.paybille.invoicer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.paybille.invoicer.core.platform.EXTRA_SALE_ID
import com.paybille.invoicer.core.platform.NotificationRouter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        routeNotification(intent)
        setContent { App() }
    }

    // La app ya estaba abierta y se tocó un aviso de vencimiento.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeNotification(intent)
    }

    private fun routeNotification(intent: Intent?) {
        val saleId = intent?.getIntExtra(EXTRA_SALE_ID, -1) ?: -1
        if (saleId > 0) {
            NotificationRouter.open(saleId)
            intent?.removeExtra(EXTRA_SALE_ID)
        }
    }
}
