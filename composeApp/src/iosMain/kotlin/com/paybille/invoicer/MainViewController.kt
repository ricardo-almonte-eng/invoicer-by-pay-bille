package com.paybille.invoicer

import androidx.compose.ui.window.ComposeUIViewController
import com.paybille.invoicer.core.di.initKoin
import com.paybille.invoicer.core.platform.IosNotificationDelegate
import platform.UIKit.UIViewController
import platform.UserNotifications.UNUserNotificationCenter

// Referencia fuerte: `UNUserNotificationCenter.delegate` es `weak`.
private val notificationDelegate = IosNotificationDelegate()

// Koin se arranca una sola vez aunque SwiftUI vuelva a crear el controlador.
private val koin by lazy {
    // El toque en un aviso llega por este delegado (abre la factura).
    UNUserNotificationCenter.currentNotificationCenter().delegate = notificationDelegate
    initKoin()
}

@Suppress("FunctionName", "unused") // Se llama desde Swift: MainViewControllerKt.MainViewController()
fun MainViewController(): UIViewController {
    koin
    return ComposeUIViewController { App() }
}
