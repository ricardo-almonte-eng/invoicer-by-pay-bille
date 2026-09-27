package com.paybille.invoicer.feature.invoice.presentation

import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.paybille.invoicer.feature.clients.presentation.ClientEditorScreen
import com.paybille.invoicer.feature.products.presentation.ProductEditorScreen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbEmptyState
import com.paybille.invoicer.core.designsystem.components.PbIcon
import com.paybille.invoicer.core.designsystem.components.PbItemRow
import com.paybille.invoicer.core.designsystem.components.PbIconButton
import com.paybille.invoicer.core.designsystem.components.PbSpinner
import com.paybille.invoicer.core.designsystem.components.PbTag
import com.paybille.invoicer.core.designsystem.components.PbTagTone
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.components.PbTextField
import com.paybille.invoicer.core.designsystem.components.PbTopBar
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.format.formatQuantity
import com.paybille.invoicer.feature.invoice.data.remote.ClientDto
import com.paybille.invoicer.feature.invoice.data.remote.SaleableItemDto
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import com.paybille.invoicer.core.designsystem.theme.PbSymbol
import org.koin.core.parameter.parametersOf

/** Buscar y agregar un producto al borrador de [kind]. */
data class ProductPickerScreen(val kind: DocumentKind) : Screen {
    override val key: String = "product-picker-${kind.name}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<ProductPickerScreenModel> { parametersOf(kind) }
        val state by model.state.collectAsState()
        SearchContent(
            title = "Agregar producto",
            placeholder = "Nombre o código de barras",
            emptyIcon = PbSymbols.Inventory2,
            emptyTitle = if (state.query.isBlank()) "No hay productos para vender" else "Sin resultados",
            emptyMessage = if (state.query.isBlank()) {
                "Toca el icono de arriba para dar de alta uno."
            } else {
                "El nombre se busca por el principio: prueba con la primera palabra o con el código."
            },
            state = state,
            onQueryChange = model::onQueryChange,
            onRetry = model::retry,
            onLoadMore = model::loadMore,
            itemKey = { it.id },
            actions = {
                PbIconButton(
                    icon = PbSymbols.AddBox,
                    contentDescription = "Nuevo producto",
                    onClick = { navigator.push(ProductEditorScreen(pickFor = kind)) },
                )
            },
        ) { item -> ProductRow(item, onClick = { model.select(item) }) }
    }
}

/** Buscar y elegir el cliente del borrador de [kind]. */
data class ClientPickerScreen(val kind: DocumentKind) : Screen {
    override val key: String = "client-picker-${kind.name}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<ClientPickerScreenModel> { parametersOf(kind) }
        val state by model.state.collectAsState()
        SearchContent(
            title = "Elegir cliente",
            placeholder = "Nombre, teléfono o cédula",
            emptyIcon = PbSymbols.Group,
            emptyTitle = if (state.query.isBlank()) "Todavía no hay clientes" else "Sin resultados",
            emptyMessage = if (state.query.isBlank()) "Toca el icono de arriba para añadir uno." else null,
            state = state,
            onQueryChange = model::onQueryChange,
            onRetry = model::retry,
            onLoadMore = model::loadMore,
            itemKey = { it.id },
            actions = {
                PbIconButton(
                    icon = PbSymbols.PersonAdd,
                    contentDescription = "Nuevo cliente",
                    onClick = { navigator.push(ClientEditorScreen(pickFor = kind)) },
                )
            },
        ) { client -> ClientRow(client, onClick = { model.select(client) }) }
    }
}

@Composable
private fun <T> SearchContent(
    title: String,
    placeholder: String,
    emptyIcon: PbSymbol,
    emptyTitle: String,
    emptyMessage: String?,
    state: SearchUiState<T>,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    itemKey: (T) -> Any,
    actions: @Composable RowScope.() -> Unit = {},
    row: @Composable (T) -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }

    LaunchedEffect(state.done) { if (state.done) navigator.pop() }
    // El teclado se abre solo: aquí se viene a escribir.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(listState, state.hasMore) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            (total > 0 && last >= total - 4) to total
        }
            .distinctUntilChanged()
            .filter { (nearEnd, _) -> nearEnd }
            .collect { onLoadMore() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PbTheme.colors.canvas)
            .imePadding(),
    ) {
        Column(
            Modifier
                .background(PbTheme.colors.island)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            PbTopBar(
                title = title,
                navigation = {
                    PbIconButton(icon = PbSymbols.ArrowBack, contentDescription = "Volver", onClick = { navigator.pop() })
                },
                actions = actions,
            )
            PbTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = "Buscar",
                placeholder = placeholder,
                leadingIcon = PbSymbols.Search,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = PbSpace.s6, end = PbSpace.s6, bottom = PbSpace.s5),
                fieldModifier = Modifier.focusRequester(focus),
            )
            Box(Modifier.fillMaxWidth().height(PbControl.border).background(PbTheme.colors.outline))
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().widthIn(max = 700.dp),
                contentPadding = PaddingValues(bottom = PbSpace.s8),
            ) {
                statusItems(state, onRetry)
                items(state.items, key = itemKey) { row(it) }
                if (state.loadingMore) item(key = "more") { SpinnerRow() }
                if (!state.loading && state.items.isEmpty() && state.error == null && !state.offline) {
                    item(key = "empty") { PbEmptyState(icon = emptyIcon, title = emptyTitle, message = emptyMessage) }
                }
            }
        }
    }
}

private fun <T> LazyListScope.statusItems(state: SearchUiState<T>, onRetry: () -> Unit) {
    when {
        state.loading && state.items.isEmpty() -> item(key = "loading") { SpinnerRow(Modifier.padding(top = PbSpace.s8)) }
        state.loading -> item(key = "refreshing") { SpinnerRow() }
        state.offline || state.error != null -> item(key = "error") {
            Column(
                Modifier.fillMaxWidth().padding(PbSpace.s6),
                verticalArrangement = Arrangement.spacedBy(PbSpace.s3),
            ) {
                PbBanner(
                    message = state.error ?: "Sin conexión. Buscar necesita internet por ahora.",
                    tone = if (state.offline) PbBannerTone.Offline else PbBannerTone.Error,
                )
                PbButton(
                    text = "Reintentar",
                    onClick = onRetry,
                    variant = PbButtonVariant.Outline,
                    leadingIcon = PbSymbols.Sync,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SpinnerRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(PbSpace.s6), horizontalArrangement = Arrangement.Center) { PbSpinner() }
}

@Composable
private fun PickerRow(
    icon: PbSymbol,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    PbItemRow(title = title, subtitle = subtitle, icon = icon, onClick = onClick, trailing = trailing)
}

@Composable
private fun ProductRow(item: SaleableItemDto, onClick: () -> Unit) {
    val stock = item.amount
    val stockText = when {
        item.infinityAmount == true -> "Servicio"
        item.unique == true -> "Único"
        stock != null -> "Hay ${formatQuantity(stock)}"
        else -> null
    }
    PickerRow(
        icon = PbSymbols.Inventory2,
        title = item.displayName,
        subtitle = listOfNotNull(item.barcode?.takeIf { it.isNotBlank() }, stockText).joinToString(" · "),
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(PbSpace.s1)) {
            PbText(text = formatMoney(item.price ?: 0.0), style = PbTheme.typography.amount)
            // Se deja vender sin existencia (decisión 2026-09-06), pero se ve.
            if (item.infinityAmount != true && item.unique != true && stock != null && stock <= 0.0) {
                PbTag(text = "Agotado", tone = PbTagTone.Warning)
            }
        }
    }
}

@Composable
private fun ClientRow(client: ClientDto, onClick: () -> Unit) {
    PickerRow(
        icon = PbSymbols.Person,
        title = client.fullName.ifBlank { "Cliente ${client.id}" },
        subtitle = listOfNotNull(client.phone?.takeIf { it.isNotBlank() }, client.identify?.takeIf { it.isNotBlank() })
            .joinToString(" · "),
        onClick = onClick,
    )
}
