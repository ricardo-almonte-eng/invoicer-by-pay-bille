package com.paybille.invoicer.core.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbSpinnerRow
import com.paybille.invoicer.core.designsystem.components.PbStatusBanner
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.network.ApiException
import kotlin.time.Clock

/**
 * Cómo va la puesta al día de una pantalla con la API. La pantalla pinta siempre lo que hay en
 * Room (offline first); esto solo dice si se está refrescando, o por qué no se pudo.
 */
data class SyncState(
    val syncing: Boolean = false,
    /** Ya se intentó al menos una vez (con o sin éxito). */
    val attempted: Boolean = false,
    val lastSuccessAt: Long? = null,
    val error: String? = null,
    val offline: Boolean = false,
) {
    /** Hay que refrescar al mostrarse: nunca se intentó, o lo último es viejo y no falló. */
    fun isStale(staleAfterMs: Long, now: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        if (syncing) return false
        if (!attempted) return true
        val last = lastSuccessAt ?: return false
        return !offline && error == null && now - last > staleAfterMs
    }

    fun started() = copy(syncing = true, error = null, offline = false)

    fun succeeded() = copy(syncing = false, attempted = true, lastSuccessAt = Clock.System.now().toEpochMilliseconds())

    fun failed(e: ApiException, fallback: String) = copy(
        syncing = false,
        attempted = true,
        offline = e.isConnectivity,
        error = if (e.isConnectivity) null else e.message ?: fallback,
    )
}

/**
 * Primera fila de una lista según [SyncState]: cargando (si no hay nada que enseñar), aviso de
 * sin conexión o de error con "Reintentar", o un indicador discreto si ya hay filas.
 */
fun LazyListScope.syncStatusItem(
    state: SyncState,
    hasRows: Boolean,
    onRetry: () -> Unit,
    offlineEmpty: String = "Sin conexión. Todavía no hay nada guardado en el teléfono.",
) {
    when {
        state.syncing && !hasRows -> item(key = "loading") { PbSpinnerRow(Modifier.padding(top = PbSpace.s10)) }
        state.offline -> item(key = "offline") {
            PbStatusBanner(
                message = if (hasRows) "Sin conexión. Mostrando lo guardado en el teléfono." else offlineEmpty,
                tone = PbBannerTone.Offline,
                onRetry = onRetry,
            )
        }
        state.error != null -> item(key = "error") {
            PbStatusBanner(message = state.error, tone = PbBannerTone.Error, onRetry = onRetry)
        }
        state.syncing -> item(key = "refreshing") { PbSpinnerRow(Modifier.padding(vertical = PbSpace.s1)) }
    }
}
