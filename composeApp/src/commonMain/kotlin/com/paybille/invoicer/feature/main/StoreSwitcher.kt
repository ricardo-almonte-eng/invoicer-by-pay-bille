package com.paybille.invoicer.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbIcon
import com.paybille.invoicer.core.designsystem.components.PbOverline
import com.paybille.invoicer.core.designsystem.components.PbSheet
import com.paybille.invoicer.core.designsystem.components.PbSpinner
import com.paybille.invoicer.core.designsystem.components.PbSpinnerRow
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.components.PbTextField
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbIconSize
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.domain.Session
import com.paybille.invoicer.feature.auth.domain.SessionState
import com.paybille.invoicer.feature.auth.domain.StoreOption
import com.paybille.invoicer.feature.auth.domain.StoreSwitchBlockedException
import com.paybille.invoicer.feature.invoice.data.InvoiceRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StoreSwitcherUiState(
    val session: Session? = null,
    val open: Boolean = false,
    /** `null` mientras no se han pedido; la tienda activa se enseña igual con la sesión. */
    val stores: List<StoreOption>? = null,
    val loading: Boolean = false,
    /** Tienda a la que se está cambiando: bloquea la hoja hasta que termine. */
    val switchingTo: Int? = null,
    val message: String? = null,
    val offline: Boolean = false,
    /** Documentos sin enviar: mientras haya, no se cambia de tienda. */
    val pendingCount: Int = 0,
)

/**
 * Cambio de tienda desde la cabecera, sin volver a pedir la contraseña
 * ([SessionRepository.switchStore]). Vive en [MainScreen]: al cambiar el `IdMarket`, `App`
 * recrea el armazón entero y este modelo se va con él.
 */
class StoreSwitcherScreenModel(
    private val sessions: SessionRepository,
    private val invoices: InvoiceRepository,
) : StateScreenModel<StoreSwitcherUiState>(StoreSwitcherUiState()) {

    init {
        screenModelScope.launch {
            sessions.state.filterIsInstance<SessionState.SignedIn>().collect { signedIn ->
                mutableState.update { it.copy(session = signedIn.session) }
            }
        }
        screenModelScope.launch {
            invoices.observePendingCount().collect { count -> mutableState.update { it.copy(pendingCount = count) } }
        }
    }

    fun open() {
        mutableState.update { it.copy(open = true, message = null, offline = false) }
        load()
    }

    fun close() {
        // A mitad de un cambio la hoja no se cierra: el usuario tiene que ver en qué quedó.
        if (state.value.switchingTo != null) return
        mutableState.update { it.copy(open = false) }
    }

    fun load() {
        if (state.value.loading) return
        mutableState.update { it.copy(loading = true, message = null, offline = false) }
        screenModelScope.launch {
            try {
                val stores = sessions.availableStores()
                mutableState.update { it.copy(stores = stores, loading = false) }
            } catch (e: ApiException) {
                mutableState.update {
                    it.copy(
                        loading = false,
                        offline = e.isConnectivity,
                        message = if (e.isConnectivity) OFFLINE_MESSAGE else e.message ?: "No se pudieron cargar tus tiendas.",
                    )
                }
            }
        }
    }

    fun choose(store: StoreOption) {
        val current = state.value
        if (current.switchingTo != null) return
        if (store.id == current.session?.idMarket) {
            close()
            return
        }
        mutableState.update { it.copy(switchingTo = store.id, message = null, offline = false) }
        screenModelScope.launch {
            try {
                // Borrar lo local y guardar la sesión nueva van juntos: si la pantalla se
                // desmonta a mitad, el cambio termina igual.
                withContext(NonCancellable) { sessions.switchStore(store.id) }
                // Éxito: la hoja se cierra aquí mismo. `App` recrea además el armazón con la
                // tienda nueva, pero la hoja no depende de eso para desaparecer.
                mutableState.update { it.copy(switchingTo = null, open = false, stores = null) }
            } catch (e: StoreSwitchBlockedException) {
                mutableState.update { it.copy(switchingTo = null, message = pendingMessage(e.pending)) }
            } catch (e: ApiException) {
                mutableState.update {
                    it.copy(
                        switchingTo = null,
                        offline = e.isConnectivity,
                        message = if (e.isConnectivity) OFFLINE_MESSAGE else e.message ?: "No se pudo cambiar de tienda.",
                    )
                }
            }
        }
    }

    private companion object {
        const val OFFLINE_MESSAGE = "Sin conexión. Para cambiar de tienda hace falta internet."
    }
}

internal fun pendingMessage(count: Int): String {
    val documents = if (count == 1) "1 documento sin enviar" else "$count documentos sin enviar"
    return "Tienes $documents. Cuando lleguen al servidor podrás cambiar de tienda."
}

/**
 * Centro de la cabecera: quién está dentro y en qué tienda. Tocarlo abre el selector de
 * tienda. Dos líneas centradas que se recortan si no caben (360 dp, letra grande).
 */
@Composable
fun StoreSwitcherButton(userName: String, storeName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PbTheme.colors
    Column(
        modifier = modifier
            .heightIn(min = PbControl.minTouch)
            .clip(RoundedCornerShape(PbRadius.md))
            .clickable(role = Role.Button, onClickLabel = "Cambiar de tienda", onClick = onClick)
            .padding(horizontal = PbSpace.s3, vertical = PbSpace.s1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PbText(
            text = userName,
            style = PbTheme.typography.bodyStrong,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            PbText(
                text = storeName,
                style = PbTheme.typography.caption,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            PbIcon(icon = PbSymbols.ExpandMore, contentDescription = null, tint = colors.muted, size = PbIconSize.md)
        }
    }
}

/** Hoja "Cambiar de tienda". Va al final del `Box` raíz de [MainScreen]. */
@Composable
fun StoreSwitcherSheet(state: StoreSwitcherUiState, model: StoreSwitcherScreenModel) {
    PbSheet(visible = state.open, title = "Cambiar de tienda", onDismiss = model::close) {
        val session = state.session
        if (session != null) {
            PbText(
                text = "Entraste como ${session.displayName}.",
                style = PbTheme.typography.body,
                color = PbTheme.colors.muted,
            )
        }

        if (state.pendingCount > 0) {
            PbBanner(message = pendingMessage(state.pendingCount), tone = PbBannerTone.Offline)
        }
        state.message?.let {
            PbBanner(message = it, tone = if (state.offline) PbBannerTone.Offline else PbBannerTone.Error)
        }

        PbOverline(text = "Tus tiendas")
        val currentId = session?.idMarket
        // Sin red todavía se enseña la tienda activa: la hoja nunca queda en blanco.
        val stores = state.stores
            ?: session?.store?.let { listOf(StoreOption(it.id, it.name, it.address)) }
            ?: emptyList()
        if (state.loading && state.stores == null) PbSpinnerRow()
        // Un Master ve TODAS las tiendas activas: con muchas, se busca en vez de desplazar.
        var query by rememberSaveable { mutableStateOf("") }
        if (stores.size > SEARCH_FROM) {
            PbTextField(
                value = query,
                onValueChange = { query = it },
                label = "Buscar tienda",
                placeholder = "Nombre o dirección",
                leadingIcon = PbSymbols.Search,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val needle = query.trim()
        val visible = if (needle.isEmpty()) {
            stores
        } else {
            stores.filter { it.name.contains(needle, ignoreCase = true) || it.address?.contains(needle, ignoreCase = true) == true }
        }
        if (visible.isEmpty() && stores.isNotEmpty()) {
            PbText(text = "Ninguna tienda coincide con “$needle”.", style = PbTheme.typography.body, color = PbTheme.colors.muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(PbSpace.s3)) {
            visible.forEach { store ->
                StoreRow(
                    store = store,
                    current = store.id == currentId,
                    switching = store.id == state.switchingTo,
                    enabled = state.switchingTo == null && state.pendingCount == 0,
                    onClick = { model.choose(store) },
                )
            }
        }

        if (state.offline) {
            PbButton(text = "Reintentar", onClick = model::load, variant = PbButtonVariant.Outline, modifier = Modifier.fillMaxWidth())
        }

        PbText(
            text = "Al cambiar se descargan las ventas, productos y clientes de la tienda elegida, " +
                "y queda como tu tienda activa también en el POS.",
            style = PbTheme.typography.caption,
            color = PbTheme.colors.muted,
        )
    }
}

private const val SEARCH_FROM = 6

@Composable
private fun StoreRow(
    store: StoreOption,
    current: Boolean,
    switching: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = PbTheme.colors
    val shape = RoundedCornerShape(PbRadius.lg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (current) colors.primaryTint else colors.island, shape)
            .border(
                if (current) PbControl.borderFocus else PbControl.border,
                if (current) colors.primary else colors.outline,
                shape,
            )
            .clickable(enabled = enabled || current, role = Role.Button, onClick = onClick)
            .semantics { selected = current }
            .padding(horizontal = PbSpace.s5, vertical = PbSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PbSpace.s4),
    ) {
        PbIcon(
            icon = if (current) PbSymbols.CheckCircle else PbSymbols.Storefront,
            contentDescription = null,
            tint = if (current) colors.primary else colors.muted,
            size = PbIconSize.lg,
            filled = current,
        )
        Column(Modifier.weight(1f)) {
            PbText(
                text = store.name,
                style = PbTheme.typography.bodyStrong,
                color = if (enabled || current) colors.ink else colors.disabledFg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = if (current) "Tienda activa" else store.address
            if (detail != null) {
                PbText(text = detail, style = PbTheme.typography.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (switching) {
            Box(Modifier.size(PbIconSize.lg), contentAlignment = Alignment.Center) { PbSpinner() }
        }
    }
}
