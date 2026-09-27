package com.paybille.invoicer.core.platform

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Un aviso local programado (vencimiento o cobro). */
data class Reminder(
    val id: String,
    val title: String,
    val body: String,
    /** Epoch ms en que debe sonar. Los del pasado se descartan. */
    val atEpochMillis: Long,
    /** Venta que se abre al tocar el aviso. */
    val saleId: Int?,
)

/**
 * Avisos locales del teléfono. No hace falta servidor: se programan con los vencimientos
 * que la app ya tiene guardados y se reprograman en cada sincronización.
 */
interface ReminderScheduler {
    /** Borra los avisos programados por la app y deja solo estos. */
    fun replaceAll(reminders: List<Reminder>)

    fun cancelAll()
}

/**
 * Pide permiso para mostrar avisos la primera vez que se compone (Android 13+ e iOS).
 * En Android 12 o menos no hace falta permiso y responde `true` al instante.
 */
@Composable
expect fun NotificationPermissionRequest(onResult: (granted: Boolean) -> Unit)

/**
 * Canal entre el aviso del sistema y la navegación: al tocar un aviso, la plataforma deja
 * aquí la venta y `MainScreen` abre su detalle.
 */
object NotificationRouter {
    private val pending = MutableStateFlow<Int?>(null)
    val saleToOpen: StateFlow<Int?> = pending.asStateFlow()

    fun open(saleId: Int) {
        pending.value = saleId
    }

    fun consumed() {
        pending.value = null
    }
}
