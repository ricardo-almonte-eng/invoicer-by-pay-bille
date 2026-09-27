package com.paybille.invoicer.feature.profile

import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.paybille.invoicer.feature.catalog.presentation.CatalogScreen
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbIconButton
import com.paybille.invoicer.core.designsystem.components.PbTopBar
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbCard
import com.paybille.invoicer.core.designsystem.components.PbIcon
import com.paybille.invoicer.core.designsystem.components.PbScreen
import com.paybille.invoicer.core.designsystem.components.PbSpinner
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.theme.PbIconSize
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.formatPercent
import com.paybille.invoicer.feature.auth.domain.Session
import com.paybille.invoicer.feature.store.presentation.StoreSettingsScreen

/** Mi perfil: quién entró, en qué tienda, sincronización y cerrar sesión. */
data object ProfileScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<ProfileScreenModel>()
        val state by model.state.collectAsState()
        ProfileContent(
            state = state,
            onBack = { navigator.pop() },
            onOpenStoreSettings = { navigator.push(StoreSettingsScreen) },
            onOpenCatalog = { navigator.push(CatalogScreen) },
            onRetry = model::refresh,
            onAskLogout = model::askLogout,
            onCancelLogout = model::cancelLogout,
            onConfirmLogout = model::confirmLogout,
        )
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onBack: () -> Unit,
    onOpenStoreSettings: () -> Unit,
    onOpenCatalog: () -> Unit,
    onRetry: () -> Unit,
    onAskLogout: () -> Unit,
    onCancelLogout: () -> Unit,
    onConfirmLogout: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
        PbTopBar(
            title = "Mi perfil",
            navigation = {
                PbIconButton(icon = PbSymbols.ArrowBack, contentDescription = "Volver", onClick = onBack)
            },
            modifier = Modifier
                .background(PbTheme.colors.island)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        )
        Box(Modifier.fillMaxWidth().height(PbControl.border).background(PbTheme.colors.outline))
        ProfileBody(state, onOpenStoreSettings, onOpenCatalog, onRetry, onAskLogout, onCancelLogout, onConfirmLogout)
    }
}

@Composable
private fun ProfileBody(
    state: ProfileUiState,
    onOpenStoreSettings: () -> Unit,
    onOpenCatalog: () -> Unit,
    onRetry: () -> Unit,
    onAskLogout: () -> Unit,
    onCancelLogout: () -> Unit,
    onConfirmLogout: () -> Unit,
) {
    PbScreen(insetTop = false) {
        val session = state.session
        if (session == null) {
            PbSpinner(modifier = Modifier.align(Alignment.CenterHorizontally))
            return@PbScreen
        }

        Greeting(session)
        SyncBanner(state.sync, onRetry)
        StoreCard(
            session,
            syncing = state.sync == SyncStatus.Syncing,
            onRetry = onRetry,
            onOpenSettings = onOpenStoreSettings,
            onOpenCatalog = onOpenCatalog,
        )
        LogoutSection(
            confirming = state.confirmingLogout,
            pendingCount = state.pendingCount,
            loggingOut = state.loggingOut,
            onAsk = onAskLogout,
            onCancel = onCancelLogout,
            onConfirm = onConfirmLogout,
        )
    }
}

@Composable
private fun Greeting(session: Session) {
    Column(verticalArrangement = Arrangement.spacedBy(PbSpace.s1)) {
        PbText(
            text = "Hola, ${session.displayName}",
            style = PbTheme.typography.title,
            modifier = Modifier.semantics { heading() },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        PbText(
            text = "Tu sesión queda abierta en este teléfono hasta que la cierres.",
            style = PbTheme.typography.body,
            color = PbTheme.colors.muted,
        )
    }
}

@Composable
private fun SyncBanner(sync: SyncStatus, onRetry: () -> Unit) {
    when (sync) {
        SyncStatus.Offline -> PbBanner(
            message = "Sin conexión. Trabajando con los datos guardados en el teléfono.",
            tone = PbBannerTone.Offline,
        )
        is SyncStatus.Failed -> Column(verticalArrangement = Arrangement.spacedBy(PbSpace.s3)) {
            PbBanner(message = sync.message, tone = PbBannerTone.Error)
            PbButton(
                text = "Reintentar",
                onClick = onRetry,
                variant = PbButtonVariant.Outline,
                leadingIcon = PbSymbols.Sync,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SyncStatus.Idle, SyncStatus.Syncing -> Unit
    }
}

@Composable
private fun StoreCard(
    session: Session,
    syncing: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCatalog: () -> Unit,
) {
    val colors = PbTheme.colors
    val store = session.store

    PbCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PbSpace.s4),
        ) {
            PbIcon(icon = PbSymbols.Storefront, contentDescription = null, tint = colors.primary, size = PbIconSize.xl)
            PbText(
                text = store?.name ?: "Tienda #${session.idMarket}",
                style = PbTheme.typography.subtitle,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (syncing) PbSpinner(size = PbIconSize.lg)
        }

        if (store == null) {
            PbText(
                text = "Todavía no se descargaron los datos de la tienda.",
                style = PbTheme.typography.body,
                color = colors.muted,
            )
            if (!syncing) {
                PbButton(
                    text = "Descargar ahora",
                    onClick = onRetry,
                    variant = PbButtonVariant.Outline,
                    leadingIcon = PbSymbols.Sync,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@PbCard
        }

        store.address?.takeIf { it.isNotBlank() }?.let { InfoRow("Dirección", it) }
        store.rnc?.takeIf { it.isNotBlank() }?.let { InfoRow("RNC", it, numeric = true) }
        store.phone?.takeIf { it.isNotBlank() }?.let { InfoRow("Teléfono", it, numeric = true) }
        InfoRow(
            label = "Impuesto por defecto",
            value = "${store.taxLabel} ${formatPercent(session.defaultTaxRate)}",
            numeric = true,
        )
        InfoRow("Usuario", session.username)
        PbButton(
            text = "Configurar tienda",
            onClick = onOpenSettings,
            variant = PbButtonVariant.Outline,
            leadingIcon = PbSymbols.Settings,
            modifier = Modifier.fillMaxWidth(),
        )
        PbButton(
            text = "Catálogo en línea",
            onClick = onOpenCatalog,
            variant = PbButtonVariant.Outline,
            leadingIcon = PbSymbols.Storefront,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, numeric: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PbSpace.s5),
    ) {
        PbText(text = label, style = PbTheme.typography.body, color = PbTheme.colors.muted)
        PbText(
            text = value,
            style = if (numeric) PbTheme.typography.amount else PbTheme.typography.bodyStrong,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LogoutSection(
    confirming: Boolean,
    pendingCount: Int,
    loggingOut: Boolean,
    onAsk: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!confirming) {
        PbButton(
            text = "Cerrar sesión",
            onClick = onAsk,
            variant = PbButtonVariant.Outline,
            leadingIcon = PbSymbols.Logout,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    // Confirmación en línea: cerrar sesión borra la copia local y obliga a volver a entrar.
    PbCard {
        PbText(text = "¿Cerrar sesión?", style = PbTheme.typography.subtitle)
        PbText(
            text = "Tendrás que volver a entrar con tu usuario y contraseña, y con conexión.",
            style = PbTheme.typography.body,
            color = PbTheme.colors.muted,
        )
        if (pendingCount > 0) {
            PbBanner(
                message = if (pendingCount == 1) {
                    "Hay 1 documento sin enviar. Si cierras sesión ahora, se pierde."
                } else {
                    "Hay $pendingCount documentos sin enviar. Si cierras sesión ahora, se pierden."
                },
                tone = PbBannerTone.Error,
            )
        }
        // Apilados y no lado a lado: con la tipografía grande del sistema, dos botones en
        // 360 dp cortan el texto.
        PbButton(
            text = "Sí, cerrar sesión",
            onClick = onConfirm,
            variant = PbButtonVariant.Danger,
            loading = loggingOut,
            modifier = Modifier.fillMaxWidth(),
        )
        PbButton(
            text = "Cancelar",
            onClick = onCancel,
            variant = PbButtonVariant.Outline,
            enabled = !loggingOut,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
