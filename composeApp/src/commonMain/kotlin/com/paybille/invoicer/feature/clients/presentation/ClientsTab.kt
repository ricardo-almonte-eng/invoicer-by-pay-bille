package com.paybille.invoicer.feature.clients.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.paybille.invoicer.core.designsystem.components.PbEmptyState
import com.paybille.invoicer.core.designsystem.components.PbFabClearance
import com.paybille.invoicer.core.designsystem.components.PbIconButton
import com.paybille.invoicer.core.designsystem.components.PbItemRow
import com.paybille.invoicer.core.designsystem.components.PbListMaxWidth
import com.paybille.invoicer.core.designsystem.components.PbRowAmount
import com.paybille.invoicer.core.designsystem.components.PbSearchField
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.formatCedula
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.ui.syncStatusItem
import com.paybille.invoicer.feature.clients.data.local.ClientEntity
import com.paybille.invoicer.feature.main.TabHeader

/** Destino Clientes: buscar en la copia del teléfono, ver quién debe y dar de alta. */
@Composable
fun ClientsTab(
    model: ClientsScreenModel,
    onOpenClient: (Int) -> Unit,
) {
    val state by model.state.collectAsState()
    val clients by model.clients.collectAsState()
    val balances by model.balances.collectAsState()

    LaunchedEffect(Unit) { model.onVisible() }

    Column(Modifier.fillMaxSize().background(PbTheme.colors.canvas).imePadding()) {
        TabHeader(title = "Clientes") {
            PbSearchField(query = state.query, onQueryChange = model::onQueryChange, placeholder = "Nombre, teléfono o cédula")
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = PbListMaxWidth),
                contentPadding = PaddingValues(top = PbSpace.s2, bottom = PbFabClearance),
            ) {
                syncStatusItem(state.sync, hasRows = clients.isNotEmpty(), onRetry = model::refresh)
                items(clients, key = { it.id }) { client ->
                    ClientRow(client, balance = balances[client.id], onClick = { onOpenClient(client.id) })
                }
                if (clients.isEmpty() && state.sync.attempted && !state.sync.syncing && !state.sync.offline && state.sync.error == null) {
                    item(key = "empty") {
                        if (state.query.isBlank()) {
                            PbEmptyState(
                                icon = PbSymbols.Group,
                                title = "Todavía no hay clientes",
                                message = "Toca el icono de arriba para añadir el primero.",
                            )
                        } else {
                            PbEmptyState(icon = PbSymbols.Search, title = "Sin resultados", message = "Prueba con otra parte del nombre o con el teléfono.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientRow(client: ClientEntity, balance: Double?, onClick: () -> Unit) {
    PbItemRow(
        title = client.fullName.ifBlank { "Cliente ${client.id}" },
        subtitle = listOfNotNull(client.phone, client.identify?.let(::formatCedula)).joinToString(" · "),
        icon = PbSymbols.Person,
        onClick = onClick,
    ) {
        if (balance != null && balance > 0.004) PbRowAmount(amount = formatMoney(balance), caption = "Te debe")
    }
}
