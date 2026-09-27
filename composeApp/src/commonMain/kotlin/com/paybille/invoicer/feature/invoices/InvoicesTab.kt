package com.paybille.invoicer.feature.invoices

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbEmptyState
import com.paybille.invoicer.core.designsystem.components.PbFabSize
import com.paybille.invoicer.core.designsystem.components.PbSpinner
import com.paybille.invoicer.core.designsystem.components.PbTabRow
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbMotion
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbol
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.sales.domain.SalesFilter
import com.paybille.invoicer.feature.sales.presentation.PendingRow
import com.paybille.invoicer.feature.sales.presentation.RowDebt
import com.paybille.invoicer.feature.sales.presentation.SaleRow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Destino Facturas (el principal) de [com.paybille.invoicer.feature.main.MainScreen]: pestañas
 * Todas / Ventas / Cotizaciones bajo la cabecera fija. Se desliza entre pestañas o se toca el
 * título.
 *
 * `onFilterChange` avisa de la pestaña visible (el "+" crea factura o cotización según ella).
 */
@Composable
fun InvoicesTab(
    model: InvoicesScreenModel,
    onFilterChange: (SalesFilter) -> Unit,
    onOpenSale: (Int) -> Unit,
) {
    val state by model.state.collectAsState()
    InvoicesContent(
        state = state,
        model = model,
        onFilterChange = onFilterChange,
        onOpenSale = onOpenSale,
    )
}

private fun SalesFilter.title() = when (this) {
    SalesFilter.All -> "Todas"
    SalesFilter.Sales -> "Ventas"
    SalesFilter.Quotes -> "Cotizaciones"
}

@Composable
private fun InvoicesContent(
    state: InvoicesUiState,
    model: InvoicesScreenModel,
    onFilterChange: (SalesFilter) -> Unit,
    onOpenSale: (Int) -> Unit,
) {
    val pager = rememberPagerState { INVOICE_TABS.size }
    val scope = rememberCoroutineScope()

    // Cada vez que una pestaña queda a la vista, se pone al día (si hace falta).
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect {
            model.onTabVisible(INVOICE_TABS[it])
            onFilterChange(INVOICE_TABS[it])
        }
    }

    Column(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
        Column(
            Modifier
                .background(PbTheme.colors.island)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            PbTabRow(
                tabs = INVOICE_TABS.map { it.title() },
                selectedIndex = pager.currentPage,
                // Desliza como los destinos: misma duración y curva (guía 05).
                onSelect = {
                    scope.launch {
                        pager.animateScrollToPage(it, animationSpec = tween(PbMotion.PAGE_MS, easing = PbMotion.ease))
                    }
                },
            )
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { INVOICE_TABS[it].name },
        ) { page ->
            val filter = INVOICE_TABS[page]
            SalesTab(
                filter = filter,
                tab = state.tab(filter),
                timeZone = state.timeZone,
                todayIso = state.todayIso,
                model = model,
                onOpenSale = onOpenSale,
            )
        }
    }
}

@Composable
private fun SalesTab(
    filter: SalesFilter,
    tab: SalesTabState,
    timeZone: String,
    todayIso: String,
    model: InvoicesScreenModel,
    onOpenSale: (Int) -> Unit,
) {
    val dueBySale by model.receivables.collectAsState()
    val sales by model.items.getValue(filter).collectAsState()
    val allPending by model.pending.collectAsState()
    val pending = allPending.filter { doc ->
        when (filter) {
            SalesFilter.All -> true
            SalesFilter.Sales -> doc.kind == DocumentKind.Invoice
            SalesFilter.Quotes -> doc.kind == DocumentKind.Quote
        }
    }
    val listState = rememberLazyListState()

    // Pide la página siguiente cuando faltan pocas filas para el final. La clave lleva el
    // total y `hasMore`: si una página no llena la pantalla, hay que volver a pedir aunque
    // "estar cerca del final" no haya cambiado.
    LaunchedEffect(listState, filter, tab.hasMore) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            (total > 0 && last >= total - PRELOAD_ROWS) to total
        }
            .distinctUntilChanged()
            .filter { (nearEnd, _) -> nearEnd }
            .collect { model.loadMore(filter) }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().widthIn(max = MAX_LIST_WIDTH),
            // Hueco al final para que el "+" no tape la última fila.
            contentPadding = PaddingValues(top = PbSpace.s2, bottom = PbFabSize + PbSpace.s6 * 2),
        ) {
            statusItem(tab, hasRows = sales.isNotEmpty(), onRetry = { model.refresh(filter) })

            items(pending, key = { "local-${it.localId}" }) { doc ->
                PendingRow(doc = doc, timeZone = timeZone, onRetry = { model.retryPending(doc.localId) })
            }

            items(sales, key = { it.id }) { sale ->
                val receivable = dueBySale[sale.id]
                SaleRow(
                    sale = sale,
                    timeZone = timeZone,
                    // Solo con saldo: una pagada o anulada no debe nada.
                    debt = receivable?.takeIf { it.balance > 0.004 && it.status != "Anulado" }?.let { RowDebt(it.balance, it.dueDate) },
                    todayIso = todayIso,
                    onClick = { onOpenSale(sale.id) },
                )
            }

            if (tab.loadingMore) {
                item(key = "more") { CenteredSpinner() }
            }

            if (sales.isEmpty() && pending.isEmpty() && !tab.refreshing && tab.attempted && tab.error == null && !tab.offline) {
                item(key = "empty") { EmptyTab(filter) }
            }
        }
    }
}

private fun LazyListScope.statusItem(
    tab: SalesTabState,
    hasRows: Boolean,
    onRetry: () -> Unit,
) {
    when {
        tab.refreshing && !hasRows -> item(key = "loading") { CenteredSpinner(Modifier.padding(top = PbSpace.s10)) }
        tab.offline -> item(key = "offline") {
            StatusBanner(
                message = if (hasRows) {
                    "Sin conexión. Mostrando lo guardado en el teléfono."
                } else {
                    "Sin conexión. Todavía no hay ventas guardadas en el teléfono."
                },
                tone = PbBannerTone.Offline,
                onRetry = onRetry,
            )
        }
        tab.error != null -> item(key = "error") {
            StatusBanner(message = tab.error, tone = PbBannerTone.Error, onRetry = onRetry)
        }
        tab.refreshing -> item(key = "refreshing") {
            // Ya hay filas: el indicador es discreto y no las tapa.
            CenteredSpinner(Modifier.padding(vertical = PbSpace.s3))
        }
    }
}

@Composable
private fun StatusBanner(message: String, tone: PbBannerTone, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(PbSpace.s6),
        verticalArrangement = Arrangement.spacedBy(PbSpace.s3),
    ) {
        PbBanner(message = message, tone = tone)
        PbButton(
            text = "Reintentar",
            onClick = onRetry,
            variant = PbButtonVariant.Outline,
            leadingIcon = PbSymbols.Sync,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyTab(filter: SalesFilter) {
    val (icon: PbSymbol, title: String, message: String) = when (filter) {
        SalesFilter.All -> Triple(
            PbSymbols.ReceiptLong,
            "Todavía no hay facturas",
            "Aquí verás tus ventas y cotizaciones, también las que hagas desde el POS.",
        )
        SalesFilter.Sales -> Triple(
            PbSymbols.ReceiptLong,
            "Todavía no hay ventas",
            "Las facturas pagadas y las que tienen saldo pendiente aparecen aquí.",
        )
        SalesFilter.Quotes -> Triple(
            PbSymbols.RequestQuote,
            "Todavía no hay cotizaciones",
            "Los presupuestos que envíes a tus clientes aparecen aquí.",
        )
    }
    PbEmptyState(icon = icon, title = title, message = message)
}

@Composable
private fun CenteredSpinner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(PbSpace.s6),
        horizontalArrangement = Arrangement.Center,
    ) {
        PbSpinner()
    }
}

private const val PRELOAD_ROWS = 5

// En tablet la lista no se estira de lado a lado.
private val MAX_LIST_WIDTH = PbControl.maxFormWidth * 1.6f
