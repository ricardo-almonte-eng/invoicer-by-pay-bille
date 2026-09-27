package com.paybille.invoicer.feature.products.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.components.PbChipGroup
import com.paybille.invoicer.core.designsystem.components.PbEmptyState
import com.paybille.invoicer.core.designsystem.components.PbFabClearance
import com.paybille.invoicer.core.designsystem.components.PbIconButton
import com.paybille.invoicer.core.designsystem.components.PbItemRow
import com.paybille.invoicer.core.designsystem.components.PbListMaxWidth
import com.paybille.invoicer.core.designsystem.components.PbSearchField
import com.paybille.invoicer.core.designsystem.components.PbTag
import com.paybille.invoicer.core.designsystem.components.PbTagTone
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.format.formatQuantity
import com.paybille.invoicer.core.ui.syncStatusItem
import com.paybille.invoicer.feature.main.TabHeader
import com.paybille.invoicer.feature.products.data.local.ProductEntity
import com.paybille.invoicer.feature.products.data.remote.InventoryInfoDto

/** Destino Productos: existencias y precios, como "Productos & Servicios" del POS. */
@Composable
fun ProductsTab(
    model: ProductsScreenModel,
    onOpenProduct: (Int) -> Unit,
) {
    val state by model.state.collectAsState()
    val products by model.products.collectAsState()
    val info by model.info.collectAsState()

    LaunchedEffect(Unit) { model.onVisible() }

    Column(Modifier.fillMaxSize().background(PbTheme.colors.canvas).imePadding()) {
        TabHeader(title = "Productos") {
            PbSearchField(query = state.query, onQueryChange = model::onQueryChange, placeholder = "Nombre o marca")
            PbChipGroup(
                options = StockFilter.entries,
                selected = state.stock,
                label = { it.label },
                onSelect = model::onStockChange,
                modifier = Modifier.padding(start = PbSpace.s6, end = PbSpace.s6, bottom = PbSpace.s5),
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = PbListMaxWidth),
                contentPadding = PaddingValues(top = PbSpace.s2, bottom = PbFabClearance),
            ) {
                syncStatusItem(state.sync, hasRows = products.isNotEmpty(), onRetry = model::refresh)
                info?.value?.let { i -> if (state.query.isBlank()) item(key = "info") { InventoryInfo(i) } }
                items(products, key = { it.idProduct }) { product ->
                    ProductRow(product, onClick = { onOpenProduct(product.idProduct) })
                }
                if (products.isEmpty() && state.sync.attempted && !state.sync.syncing && !state.sync.offline && state.sync.error == null) {
                    item(key = "empty") {
                        if (state.query.isBlank() && state.stock == StockFilter.All) {
                            PbEmptyState(
                                icon = PbSymbols.Inventory2,
                                title = "Todavía no hay productos",
                                message = "Toca el icono de arriba para dar de alta el primero.",
                            )
                        } else {
                            PbEmptyState(icon = PbSymbols.Search, title = "Sin resultados", message = "Prueba con otra parte del nombre o cambia el filtro.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryInfo(info: InventoryInfoDto) {
    val parts = listOfNotNull(
        info.products?.let { if (it == 1) "1 producto" else "$it productos" },
        info.soldOut?.takeIf { it > 0 }?.let { if (it == 1) "1 agotado" else "$it agotados" },
        info.saleValue?.let { "valen ${formatMoney(it)}" },
    )
    if (parts.isEmpty()) return
    PbText(
        text = parts.joinToString(" · "),
        style = PbTheme.typography.caption,
        color = PbTheme.colors.muted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PbSpace.s6, vertical = PbSpace.s4),
    )
}

@Composable
private fun ProductRow(product: ProductEntity, onClick: () -> Unit) {
    PbItemRow(
        title = product.name,
        subtitle = listOfNotNull(product.brand, "Único".takeIf { product.unique }).joinToString(" · "),
        icon = PbSymbols.Inventory2,
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PbText(text = priceText(product), style = PbTheme.typography.amount, maxLines = 1)
            StockTag(product.quantity)
        }
    }
}

internal fun priceText(product: ProductEntity): String =
    if (product.maxPrice - product.minPrice > 0.004) "${formatMoney(product.minPrice)} – ${formatMoney(product.maxPrice)}" else formatMoney(product.minPrice)

/**
 * Existencia: normal, agotado o **negativa**. Vender sin existencia se deja (decisión
 * 2026-09-06), así que lo negativo es un aviso, no un error.
 */
@Composable
internal fun StockTag(quantity: Double) {
    when {
        quantity > 0 -> PbText(text = "Hay ${formatQuantity(quantity)}", style = PbTheme.typography.caption, color = PbTheme.colors.muted)
        quantity == 0.0 -> PbTag(text = "Agotado", tone = PbTagTone.Warning)
        else -> PbTag(text = "Hay ${formatQuantity(quantity)}", tone = PbTagTone.Warning)
    }
}
