package com.paybille.invoicer.feature.notifications.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.paybille.invoicer.core.designsystem.components.PbEmptyState
import com.paybille.invoicer.core.designsystem.components.PbIconButton
import com.paybille.invoicer.core.designsystem.components.PbListCard
import com.paybille.invoicer.core.designsystem.components.PbListHeader
import com.paybille.invoicer.core.designsystem.components.PbListMaxWidth
import com.paybille.invoicer.core.designsystem.components.PbStackHeader
import com.paybille.invoicer.core.designsystem.components.PbTag
import com.paybille.invoicer.core.designsystem.components.PbTagTone
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.ui.syncStatusItem
import com.paybille.invoicer.feature.detail.domain.DueState
import com.paybille.invoicer.feature.detail.presentation.SaleDetailScreen
import com.paybille.invoicer.feature.notifications.domain.Notice
import com.paybille.invoicer.feature.sales.presentation.PendingRow
import com.paybille.invoicer.feature.sales.presentation.dueLine

/** Notificaciones: lo que pide atención, sin tener que recorrer las listas. */
data object NotificationsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<NotificationsScreenModel>()
        val state by model.state.collectAsState()
        val items by model.items.collectAsState()
        val unsent = items.filterIsInstance<Notice.Unsent>()
        val due = items.filterIsInstance<Notice.Due>()

        Column(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
            PbStackHeader(title = "Notificaciones", onBack = { navigator.pop() })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().widthIn(max = PbListMaxWidth),
                    contentPadding = PaddingValues(top = PbSpace.s2, bottom = PbSpace.s10),
                ) {
                    syncStatusItem(state.sync, hasRows = items.isNotEmpty(), onRetry = model::refresh)
                    if (unsent.isNotEmpty()) {
                        item(key = "unsent-h") { PbListHeader("Sin enviar") }
                        items(unsent, key = { it.key }) { notice ->
                            PendingRow(notice.doc, state.timeZone, onRetry = { model.retry(notice.doc.localId) })
                        }
                    }
                    if (due.isNotEmpty()) {
                        item(key = "due-h") { PbListHeader("Por cobrar") }
                        items(due, key = { it.key }) { notice ->
                            DueRow(notice, onClick = notice.debt.saleId?.let { id -> { navigator.push(SaleDetailScreen(saleId = id)) } })
                        }
                    }
                    if (items.isEmpty() && !(state.sync.syncing && !state.sync.attempted)) {
                        item(key = "empty") {
                            PbEmptyState(
                                icon = PbSymbols.CheckCircle,
                                title = "Todo al día",
                                message = "Aquí verás las facturas vencidas o por vencer y lo que no se pudo enviar.",
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ```
 * ▌Tony Stark                          $ 850.00
 * ▌Factura #0009                       [Vencida]
 * ▌Venció hace 7 días · 20 sep.
 * ```
 */
@Composable
private fun DueRow(notice: Notice.Due, onClick: (() -> Unit)?) {
    val colors = PbTheme.colors
    val overdue = notice.due is DueState.Overdue
    val debt = notice.debt
    PbListCard(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        onClick = onClick,
        accent = if (overdue) colors.error else colors.accent,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PbSpace.s5)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PbSpace.s1)) {
                PbText(
                    text = debt.partyName ?: "Cliente",
                    style = PbTheme.typography.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PbText(
                    text = debt.number?.let { "Factura #$it" } ?: "Factura",
                    style = PbTheme.typography.caption.copy(fontFeatureSettings = "tnum"),
                    color = colors.muted,
                )
                PbText(
                    text = dueLine(notice.due, debt.dueDate),
                    style = PbTheme.typography.caption,
                    color = if (overdue) colors.error else colors.accentText,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(PbSpace.s2)) {
                PbText(
                    text = formatMoney(debt.balance),
                    style = PbTheme.typography.amountTitle,
                    color = if (overdue) colors.error else colors.accentText,
                    textAlign = TextAlign.End,
                )
                when {
                    overdue -> PbTag(text = "Vencida", tone = PbTagTone.Danger)
                    notice.due == DueState.Today -> PbTag(text = "Vence hoy", tone = PbTagTone.Warning)
                    else -> PbTag(text = "Por vencer", tone = PbTagTone.Warning)
                }
            }
        }
    }
}

/**
 * La campana de la cabecera de Facturas. Con algo urgente (vencida, vence hoy, rechazada) lleva
 * un punto rojo; el número va en la descripción para el lector de pantalla.
 */
@Composable
fun NotificationsBell(urgent: Int, onClick: () -> Unit) {
    val colors = PbTheme.colors
    Box {
        PbIconButton(
            icon = if (urgent > 0) PbSymbols.NotificationsActive else PbSymbols.Notifications,
            contentDescription = when (urgent) {
                0 -> "Notificaciones"
                1 -> "Notificaciones: 1 pide atención"
                else -> "Notificaciones: $urgent piden atención"
            },
            onClick = onClick,
        )
        if (urgent > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(10.dp)
                    .background(colors.error, CircleShape)
                    .border(1.5.dp, colors.island, CircleShape),
            )
        }
    }
}
