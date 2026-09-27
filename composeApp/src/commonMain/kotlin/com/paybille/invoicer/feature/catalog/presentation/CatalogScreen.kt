package com.paybille.invoicer.feature.catalog.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbCard
import com.paybille.invoicer.core.designsystem.components.PbCheckRow
import com.paybille.invoicer.core.designsystem.components.PbChipGroup
import com.paybille.invoicer.core.designsystem.components.PbEmptyState
import com.paybille.invoicer.core.designsystem.components.PbIcon
import com.paybille.invoicer.core.designsystem.components.PbListCard
import com.paybille.invoicer.core.designsystem.components.PbListHeader
import com.paybille.invoicer.core.designsystem.components.PbListMaxWidth
import com.paybille.invoicer.core.designsystem.components.PbOverline
import com.paybille.invoicer.core.designsystem.components.PbSearchField
import com.paybille.invoicer.core.designsystem.components.PbSpinner
import com.paybille.invoicer.core.designsystem.components.PbStackHeader
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.theme.PbIconSize
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.format.formatQuantity
import com.paybille.invoicer.core.ui.syncStatusItem

/**
 * Catálogo en línea: el enlace para compartir y qué productos salen en él. Se abre desde
 * Productos (icono de tienda de la cabecera) y desde Mi perfil.
 */
data object CatalogScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<CatalogScreenModel>()
        val state by model.state.collectAsState()
        val items by model.items.collectAsState()
        val shownIds by model.shownIds.collectAsState()
        val uriHandler = LocalUriHandler.current

        Column(Modifier.fillMaxSize().background(PbTheme.colors.canvas).imePadding()) {
            PbStackHeader(title = "Catálogo en línea", onBack = { navigator.pop() }) {
                PbSearchField(query = state.query, onQueryChange = model::onQueryChange, placeholder = "Nombre o marca")
                PbChipGroup(
                    options = CatalogFilter.entries,
                    selected = state.filter,
                    label = { it.label },
                    onSelect = model::onFilterChange,
                    modifier = Modifier.padding(start = PbSpace.s6, end = PbSpace.s6, bottom = PbSpace.s5),
                )
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().widthIn(max = PbListMaxWidth),
                    contentPadding = PaddingValues(bottom = PbSpace.s10),
                ) {
                    item(key = "link") {
                        LinkCard(
                            state = state,
                            shownCount = shownIds?.size,
                            onShare = model::share,
                            onOpen = { state.url?.let(uriHandler::openUri) },
                        )
                    }
                    state.message?.let { message ->
                        item(key = "message") {
                            Column(Modifier.padding(horizontal = PbSpace.s6, vertical = PbSpace.s2)) {
                                PbBanner(message = message, tone = PbBannerTone.Error)
                            }
                        }
                    }
                    syncStatusItem(
                        state.sync,
                        hasRows = shownIds != null,
                        onRetry = model::refresh,
                        offlineEmpty = "Sin conexión. Para ver qué sale en el catálogo hace falta internet la primera vez.",
                    )
                    item(key = "header") {
                        PbListHeader("Productos") {
                            PbText(text = "${items.size}", style = PbTheme.typography.amount, color = PbTheme.colors.muted)
                        }
                    }
                    items(items, key = { it.product.idProduct }) { item ->
                        val busy = item.product.idProduct in state.busy
                        PbListCard(contentPadding = PaddingValues(horizontal = PbSpace.s5, vertical = PbSpace.s1)) {
                            PbCheckRow(
                                title = item.product.name,
                                subtitle = subtitleOf(item),
                                checked = item.shown,
                                enabled = !busy && shownIds != null,
                                onCheckedChange = { model.toggle(item.product.idProduct, it) },
                                leading = if (busy) {
                                    { PbSpinner(size = PbIconSize.lg) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    if (items.isEmpty() && shownIds != null) {
                        item(key = "empty") {
                            PbEmptyState(
                                icon = PbSymbols.Storefront,
                                title = when (state.filter) {
                                    CatalogFilter.Shown -> "Todavía no hay productos en el catálogo"
                                    CatalogFilter.Hidden -> "Todo tu inventario está en el catálogo"
                                    CatalogFilter.All -> "No hay productos"
                                },
                                message = if (state.filter == CatalogFilter.Shown) "Marca los que quieras enseñar a tus clientes." else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkCard(state: CatalogUiState, shownCount: Int?, onShare: () -> Unit, onOpen: () -> Unit) {
    val colors = PbTheme.colors
    PbCard(modifier = Modifier.padding(start = PbSpace.s6, end = PbSpace.s6, top = PbSpace.s6)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PbSpace.s4)) {
            PbIcon(icon = PbSymbols.Link, contentDescription = null, tint = colors.primary)
            PbOverline(text = "Tu enlace", modifier = Modifier.weight(1f))
        }
        val url = state.url
        if (url == null) {
            val chars = state.invalidCharacters.joinToString(" ") { "“$it”" }
            PbBanner(
                message = if (state.storeName == null) {
                    "Todavía no se descargaron los datos de la tienda."
                } else {
                    "El nombre de la tienda tiene caracteres que el enlace no admite ($chars). " +
                        "Cámbialo en Configurar tienda para poder compartir el catálogo."
                },
                tone = PbBannerTone.Error,
            )
            return@PbCard
        }
        PbText(
            text = url,
            style = PbTheme.typography.bodyStrong,
            color = colors.primary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        PbText(
            text = when (shownCount) {
                null -> "Solo salen los productos marcados abajo."
                0 -> "Todavía no sale ningún producto: marca los que quieras enseñar."
                1 -> "Sale 1 producto. Tus clientes ven precio, marca, color y si hay existencia."
                else -> "Salen $shownCount productos. Tus clientes ven precio, marca, color y si hay existencia."
            },
            style = PbTheme.typography.caption,
            color = colors.muted,
        )
        // Apilados: con la letra grande del sistema, dos botones lado a lado cortan el texto a 360 dp.
        PbButton(text = "Compartir enlace", onClick = onShare, leadingIcon = PbSymbols.Share, modifier = Modifier.fillMaxWidth())
        PbButton(
            text = "Ver como cliente",
            onClick = onOpen,
            variant = PbButtonVariant.Outline,
            leadingIcon = PbSymbols.OpenInNew,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun subtitleOf(item: CatalogItem): String {
    val p = item.product
    val price = when {
        p.maxPrice <= 0.0 -> "Sin precio"
        p.maxPrice > p.minPrice -> "Desde ${formatMoney(p.minPrice)}"
        else -> formatMoney(p.minPrice)
    }
    val stock = if (p.quantity > 0) "${formatQuantity(p.quantity)} en existencia" else "Agotado"
    return listOfNotNull(p.brand, price, stock).joinToString(" · ")
}
