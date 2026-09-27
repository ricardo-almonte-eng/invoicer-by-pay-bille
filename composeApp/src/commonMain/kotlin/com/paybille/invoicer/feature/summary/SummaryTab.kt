package com.paybille.invoicer.feature.summary

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.paybille.invoicer.core.designsystem.components.PbAreaChart
import com.paybille.invoicer.core.designsystem.components.PbCard
import com.paybille.invoicer.core.designsystem.components.PbChipGroup
import com.paybille.invoicer.core.designsystem.components.PbFabClearance
import com.paybille.invoicer.core.designsystem.components.PbListMaxWidth
import com.paybille.invoicer.core.designsystem.components.PbMeterBar
import com.paybille.invoicer.core.designsystem.components.PbStatTile
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.DateRange
import com.paybille.invoicer.core.format.Period
import com.paybille.invoicer.core.format.formatDayMonth
import com.paybille.invoicer.core.format.formatIsoDate
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.format.formatQuantity
import com.paybille.invoicer.core.ui.syncStatusItem
import com.paybille.invoicer.feature.main.TabHeader
import com.paybille.invoicer.feature.summary.data.DashboardSummaryDto
import com.paybille.invoicer.feature.summary.data.TopProductDto
import com.paybille.invoicer.feature.summary.data.fillDays

/**
 * Destino Resumen: el dashboard del POS (`pages/index.vue`) — cuatro cifras, las ventas del
 * periodo en un gráfico y los productos más vendidos. "Turnos activos" se cambió por "Te
 * deben": los turnos de caja no existen en esta app.
 */
@Composable
fun SummaryTab(model: SummaryScreenModel) {
    val state by model.state.collectAsState()
    val cached by model.summary.collectAsState()
    val owed by model.owed.collectAsState()
    val range = state.range
    val sync = state.syncOf(range)

    LaunchedEffect(Unit) { model.onVisible() }

    Column(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
        TabHeader(title = "Resumen") {
            PbChipGroup(
                options = Period.entries,
                selected = state.period,
                label = { it.label },
                onSelect = model::selectPeriod,
                modifier = Modifier.padding(start = PbSpace.s6, end = PbSpace.s6, bottom = PbSpace.s5),
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = PbListMaxWidth),
                contentPadding = PaddingValues(bottom = PbFabClearance),
            ) {
                item(key = "hello") { Greeting(state.firstName, state.today?.toString()) }
                syncStatusItem(
                    state = sync,
                    hasRows = cached != null,
                    onRetry = model::refresh,
                    offlineEmpty = "Sin conexión. El resumen de este periodo todavía no se ha cargado.",
                )
                val summary = cached?.value
                if (summary != null && range != null) {
                    item(key = "tiles") { Tiles(summary, state.period, owed) }
                    item(key = "chart") { SalesChart(summary, range) }
                    item(key = "top") { TopProducts(summary.topProducts) }
                }
            }
        }
    }
}

@Composable
private fun Greeting(firstName: String, todayIso: String?) {
    Column(
        Modifier.fillMaxWidth().padding(start = PbSpace.s6, end = PbSpace.s6, top = PbSpace.s7, bottom = PbSpace.s3),
        verticalArrangement = Arrangement.spacedBy(PbSpace.s1),
    ) {
        PbText(text = if (firstName.isBlank()) "Hola" else "Hola, $firstName", style = PbTheme.typography.title)
        if (todayIso != null) {
            PbText(text = formatIsoDate(todayIso), style = PbTheme.typography.caption, color = PbTheme.colors.muted)
        }
    }
}

@Composable
private fun Tiles(summary: DashboardSummaryDto, period: Period, owed: Pair<Double, Int>) {
    val today = summary.today
    val range = summary.range
    Column(
        Modifier.fillMaxWidth().padding(horizontal = PbSpace.s6, vertical = PbSpace.s3),
        verticalArrangement = Arrangement.spacedBy(PbSpace.s5),
    ) {
        TileRow(
            left = {
                PbStatTile(
                    label = "Ventas de hoy",
                    value = formatMoney(today.totalVendido ?: 0.0),
                    hint = transactions(today.cantidadTransacciones ?: 0),
                    modifier = it,
                )
            },
            right = { PbStatTile(label = "Gastos de hoy", value = formatMoney(today.totalGastos ?: 0.0), modifier = it) },
        )
        TileRow(
            left = {
                PbStatTile(
                    label = "Te deben",
                    value = formatMoney(owed.first),
                    hint = when (owed.second) {
                        0 -> "Nada pendiente"
                        1 -> "1 factura"
                        else -> "${owed.second} facturas"
                    },
                    modifier = it,
                )
            },
            right = {
                PbStatTile(
                    label = if (period == Period.Today) "Total de hoy" else "Total · ${period.label}",
                    value = formatMoney(range.totalVendido ?: 0.0),
                    hint = sales(range.cantidadTransacciones ?: 0),
                    modifier = it,
                )
            },
        )
    }
}

/** Dos tarjetas lado a lado con la misma altura (el texto grande puede partir una en dos líneas). */
@Composable
private fun TileRow(left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(PbSpace.s5)) {
        left(Modifier.weight(1f).fillMaxHeight())
        right(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun SalesChart(summary: DashboardSummaryDto, range: DateRange) {
    val values = fillDays(range, summary.series)
    val total = summary.range.totalVendido ?: 0.0
    PbCard(Modifier.fillMaxWidth().padding(horizontal = PbSpace.s6, vertical = PbSpace.s3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PbText(text = "Ventas del periodo", style = PbTheme.typography.subtitle, modifier = Modifier.weight(1f))
            PbText(text = formatMoney(total), style = PbTheme.typography.amount)
        }
        if (values.all { it <= 0.0 }) {
            PbText(text = "Sin ventas en este periodo", style = PbTheme.typography.body, color = PbTheme.colors.muted)
        } else {
            PbAreaChart(
                values = values,
                description = "Ventas del ${formatDayMonth(range.start)} al ${formatDayMonth(range.end)}: ${formatMoney(total)}",
            )
            Row {
                PbText(text = formatDayMonth(range.start), style = PbTheme.typography.caption, color = PbTheme.colors.muted, modifier = Modifier.weight(1f))
                if (range.start != range.end) {
                    PbText(text = formatDayMonth(range.end), style = PbTheme.typography.caption, color = PbTheme.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun TopProducts(products: List<TopProductDto>) {
    PbCard(Modifier.fillMaxWidth().padding(horizontal = PbSpace.s6, vertical = PbSpace.s3)) {
        PbText(text = "Productos más vendidos", style = PbTheme.typography.subtitle)
        if (products.isEmpty()) {
            PbText(text = "Todavía no hay ventas en este periodo.", style = PbTheme.typography.body, color = PbTheme.colors.muted)
            return@PbCard
        }
        val maxQty = products.maxOf { it.qty ?: 0.0 }.coerceAtLeast(1.0)
        products.forEachIndexed { index, product ->
            val qty = product.qty ?: 0.0
            Column(verticalArrangement = Arrangement.spacedBy(PbSpace.s2)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PbSpace.s3)) {
                    PbText(text = "${index + 1}.", style = PbTheme.typography.caption, color = PbTheme.colors.muted)
                    PbText(
                        text = product.name ?: "Producto",
                        style = PbTheme.typography.bodyStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    PbText(text = formatMoney(product.total ?: 0.0), style = PbTheme.typography.amount)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PbSpace.s3)) {
                    // Mínimo 8 %: el último del ranking también se ve (como en el POS).
                    PbMeterBar(fraction = (qty / maxQty).toFloat().coerceAtLeast(0.08f), modifier = Modifier.weight(1f))
                    PbText(text = "${formatQuantity(qty)} u.", style = PbTheme.typography.caption, color = PbTheme.colors.muted)
                }
            }
        }
    }
}

private fun transactions(n: Int) = if (n == 1) "1 transacción" else "$n transacciones"

private fun sales(n: Int) = if (n == 1) "1 venta" else "$n ventas"
