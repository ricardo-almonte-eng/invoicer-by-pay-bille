package com.paybille.invoicer.feature.reports.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.paybille.invoicer.core.designsystem.components.PbChipGroup
import com.paybille.invoicer.core.designsystem.components.PbFabClearance
import com.paybille.invoicer.core.designsystem.components.PbItemRow
import com.paybille.invoicer.core.designsystem.components.PbListHeader
import com.paybille.invoicer.core.designsystem.components.PbListMaxWidth
import com.paybille.invoicer.core.designsystem.components.PbListRow
import com.paybille.invoicer.core.designsystem.components.PbRowAmount
import com.paybille.invoicer.core.designsystem.components.PbStackHeader
import com.paybille.invoicer.core.designsystem.components.PbStatTile
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.format.formatQuantity
import com.paybille.invoicer.core.format.formatShortDate
import com.paybille.invoicer.core.format.label
import com.paybille.invoicer.core.format.parseApiTimestamp
import com.paybille.invoicer.core.ui.syncStatusItem
import com.paybille.invoicer.feature.clients.data.remote.PartyBalanceDto
import com.paybille.invoicer.feature.clients.presentation.ClientDetailScreen
import com.paybille.invoicer.feature.detail.presentation.SaleDetailScreen
import com.paybille.invoicer.feature.main.TabHeader
import com.paybille.invoicer.feature.reports.data.InventoryHistoryDto
import com.paybille.invoicer.feature.reports.data.ReportsRemoteDataSource
import com.paybille.invoicer.feature.reports.data.SalesReportDto
import com.paybille.invoicer.feature.reports.data.SoldProductsReportDto
import com.paybille.invoicer.feature.sales.data.toSummary
import com.paybille.invoicer.feature.sales.presentation.SaleRow
import org.koin.core.parameter.parametersOf

/** Destino Reportes: los cuatro reportes del POS que sirven en el teléfono. */
@Composable
fun ReportsTab(onOpenReport: (ReportKind) -> Unit) {
    Column(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
        TabHeader(title = "Reportes")
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = PbListMaxWidth),
                contentPadding = PaddingValues(start = PbSpace.s6, end = PbSpace.s6, top = PbSpace.s6, bottom = PbFabClearance),
                verticalArrangement = Arrangement.spacedBy(PbSpace.s3),
            ) {
                items(ReportKind.entries, key = { it.name }) { kind ->
                    PbListRow(title = kind.title, subtitle = kind.description, leadingIcon = kind.icon, onClick = { onOpenReport(kind) })
                }
            }
        }
    }
}

/** Un reporte, con su rango arriba (si lo tiene) y el resultado debajo. */
data class ReportScreen(val kind: ReportKind) : Screen {
    override val key: String = "report-${kind.name}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<ReportScreenModel> { parametersOf(kind) }
        val state by model.state.collectAsState()
        val sales by model.sales.collectAsState()
        val sold by model.soldProducts.collectAsState()
        val inventory by model.inventory.collectAsState()
        val balances by model.balances.collectAsState()
        val sync = state.syncOf(kind)
        val hasData = when (kind) {
            ReportKind.Sales -> sales != null
            ReportKind.SoldProducts -> sold != null
            ReportKind.Balances -> balances != null
            ReportKind.InventoryHistory -> inventory != null
        }

        Column(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
            PbStackHeader(title = kind.title, onBack = { navigator.pop() }) {
                if (kind.hasRange) {
                    PbChipGroup(
                        options = ReportPeriod.entries,
                        selected = state.period,
                        label = { it.label },
                        onSelect = model::selectPeriod,
                        modifier = Modifier.padding(start = PbSpace.s6, end = PbSpace.s6, bottom = PbSpace.s3),
                    )
                    state.range?.let {
                        PbText(
                            text = it.label(),
                            style = PbTheme.typography.caption,
                            color = PbTheme.colors.muted,
                            modifier = Modifier.padding(start = PbSpace.s6, end = PbSpace.s6, bottom = PbSpace.s5),
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().widthIn(max = PbListMaxWidth),
                    contentPadding = PaddingValues(bottom = PbSpace.s10),
                ) {
                    syncStatusItem(
                        sync,
                        hasRows = hasData,
                        onRetry = model::refresh,
                        offlineEmpty = "Sin conexión. Este reporte todavía no se ha cargado en el teléfono.",
                    )
                    when (kind) {
                        ReportKind.Sales -> sales?.value?.let { salesReport(it, state.timeZone, navigator) }
                        ReportKind.SoldProducts -> sold?.value?.let { soldProductsReport(it) }
                        ReportKind.Balances -> balances?.value?.let { balancesReport(it, navigator) }
                        ReportKind.InventoryHistory -> inventory?.value?.let { inventoryReport(it, state.timeZone) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TilePair(left: Pair<String, String>, right: Pair<String, String>) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = PbSpace.s6, vertical = PbSpace.s2),
        horizontalArrangement = Arrangement.spacedBy(PbSpace.s5),
    ) {
        PbStatTile(label = left.first, value = left.second, modifier = Modifier.weight(1f).fillMaxHeight())
        PbStatTile(label = right.first, value = right.second, modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

private fun LazyListScope.emptyNote(key: String, text: String) = item(key = key) {
    PbText(
        text = text,
        style = PbTheme.typography.body,
        color = PbTheme.colors.muted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PbSpace.s6, vertical = PbSpace.s3),
    )
}

private fun LazyListScope.salesReport(report: SalesReportDto, timeZone: String, navigator: Navigator) {
    val t = report.totals
    item(key = "t1") {
        Column(Modifier.padding(top = PbSpace.s4)) {
            TilePair("Vendido" to formatMoney(t.totalVendido ?: 0.0), "Ventas" to (t.cantidadTransacciones ?: 0).toString())
            TilePair("Impuestos" to formatMoney(t.totalImpuestos ?: 0.0), "Descuentos" to formatMoney(t.totalDescuento ?: 0.0))
            TilePair("Efectivo" to formatMoney((t.totalEfectivo ?: 0.0) - (t.totalEfectivoRetornado ?: 0.0)), "Transferencia" to formatMoney(t.totalDeposito ?: 0.0))
            TilePair("Tarjeta" to formatMoney(t.totalCredito ?: 0.0), "Gastos" to formatMoney(t.totalGastos ?: 0.0))
        }
    }
    item(key = "list-h") { PbListHeader("Ventas pagadas") }
    if (report.sales.isEmpty()) emptyNote("none", "No hay ventas pagadas en este periodo.")
    items(report.sales, key = { it.id }) { sale ->
        SaleRow(sale = sale.toSummary(), timeZone = timeZone, onClick = { navigator.push(SaleDetailScreen(saleId = sale.id)) })
    }
    if (report.truncated) emptyNote("more", "Se muestran las ${ReportsRemoteDataSource.MAX_SALES} más recientes. Los totales sí son de todo el periodo.")
}

private fun LazyListScope.soldProductsReport(report: SoldProductsReportDto) {
    val t = report.totals
    item(key = "t1") {
        Column(Modifier.padding(top = PbSpace.s4)) {
            TilePair("Vendido" to formatMoney(t.totalPrice ?: 0.0), "Costo" to formatMoney(t.totalCost ?: 0.0))
            TilePair("Descuentos" to formatMoney(t.totalDiscount ?: 0.0), "Ganancia" to formatMoney(t.totalProfit ?: 0.0))
        }
    }
    item(key = "list-h") { PbListHeader("Por producto") }
    if (report.products.isEmpty()) emptyNote("none", "No se vendieron productos en este periodo.")
    items(report.products, key = { "${it.idProduct}-${it.name}" }) { p ->
        PbItemRow(
            title = p.name ?: "Producto",
            subtitle = "${formatQuantity(p.totalAmount ?: 0.0)} u. · ganancia ${formatMoney(p.profit ?: 0.0)}",
            icon = PbSymbols.Inventory2,
        ) {
            PbRowAmount(amount = formatMoney(p.totalPrice ?: 0.0))
        }
    }
}

private fun LazyListScope.balancesReport(rows: List<PartyBalanceDto>, navigator: Navigator) {
    val withBalance = rows.filter { (it.balance ?: 0.0) > 0.004 }
    item(key = "t1") {
        PbStatTile(
            label = "Te deben",
            value = formatMoney(withBalance.sumOf { it.balance ?: 0.0 }),
            hint = if (withBalance.size == 1) "1 cliente" else "${withBalance.size} clientes",
            modifier = Modifier.fillMaxWidth().padding(PbSpace.s6),
        )
    }
    if (withBalance.isEmpty()) emptyNote("none", "Nadie te debe.")
    items(withBalance, key = { "${it.partyKey}-${it.partyName}" }) { p ->
        val days = p.daysOverdue ?: 0
        val clientId = p.partyKey?.takeIf { it > 0 }
        PbItemRow(
            title = p.partyName ?: "Sin nombre",
            subtitle = listOfNotNull(
                if ((p.docs ?: 0) == 1) "1 factura" else "${p.docs ?: 0} facturas",
                when {
                    days <= 0 -> null
                    days == 1 -> "vencida hace 1 día"
                    else -> "vencida hace $days días"
                },
            ).joinToString(" · "),
            icon = PbSymbols.Person,
            iconTint = if (days > 0) PbTheme.colors.error else PbTheme.colors.primary,
            onClick = clientId?.let { id -> { navigator.push(ClientDetailScreen(id, p.partyName)) } },
        ) {
            PbRowAmount(amount = formatMoney(p.balance ?: 0.0), caption = p.total?.let { "de ${formatMoney(it)}" })
        }
    }
}

private fun LazyListScope.inventoryReport(report: InventoryHistoryDto, timeZone: String) {
    if (report.moves.isEmpty()) emptyNote("none", "Sin entradas ni salidas en este periodo.")
    items(report.moves, key = { it.id }) { move ->
        PbItemRow(
            title = move.name ?: "Producto",
            subtitle = listOfNotNull(
                parseApiTimestamp(move.createdAt)?.let { formatShortDate(it, timeZone) },
                move.comment?.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            icon = PbSymbols.SwapVert,
        ) {
            if (move.before != null || move.after != null) {
                PbText(
                    text = "${move.before?.let(::formatQuantity) ?: "—"} → ${move.after?.let(::formatQuantity) ?: "—"}",
                    style = PbTheme.typography.amount,
                )
            }
        }
    }
    if (report.truncated) emptyNote("more", "Se muestran los ${ReportsRemoteDataSource.MAX_MOVES} más recientes.")
}
