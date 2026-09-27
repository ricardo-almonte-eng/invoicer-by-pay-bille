package com.paybille.invoicer.feature.products.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.paybille.invoicer.core.designsystem.components.PbIcon
import com.paybille.invoicer.core.designsystem.components.PbOption
import com.paybille.invoicer.core.designsystem.components.PbOptionSheet
import com.paybille.invoicer.core.designsystem.components.PbSelectField
import com.paybille.invoicer.core.designsystem.components.PbSwitchRow
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbIconSize
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.ui.ImageField
import com.paybille.invoicer.core.ui.RemoteImage
import com.paybille.invoicer.feature.products.data.remote.CatalogItemDto
import com.paybille.invoicer.feature.products.data.remote.ProductCatalog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbCard
import com.paybille.invoicer.core.designsystem.components.PbFormScreen
import com.paybille.invoicer.core.designsystem.components.PbIconButton
import com.paybille.invoicer.core.designsystem.components.PbItemRow
import com.paybille.invoicer.core.designsystem.components.PbListHeader
import com.paybille.invoicer.core.designsystem.components.PbListMaxWidth
import com.paybille.invoicer.core.designsystem.components.PbStackHeader
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.components.PbTextField
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.format.formatPercent
import com.paybille.invoicer.core.format.formatQuantity
import com.paybille.invoicer.core.format.formatShortDate
import com.paybille.invoicer.core.format.parseApiTimestamp
import com.paybille.invoicer.core.ui.syncStatusItem
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.presentation.InvoiceEditorScreen
import com.paybille.invoicer.feature.products.data.remote.ProductDetailDto
import com.paybille.invoicer.feature.products.data.unitTax
import org.koin.core.parameter.parametersOf

/** Ficha de un producto: precio, existencia, impuesto informativo y su historial de inventario. */
data class ProductDetailScreen(val idProduct: Int) : Screen {
    override val key: String = "product-$idProduct"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<ProductDetailScreenModel> { parametersOf(idProduct) }
        val state by model.state.collectAsState()
        val product by model.product.collectAsState()
        val cached by model.detail.collectAsState()
        val catalogs by model.catalogs.collectAsState()
        val detail = cached?.value

        Column(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
            PbStackHeader(
                title = detail?.product?.name ?: product?.name ?: "Producto",
                onBack = { navigator.pop() },
                actions = {
                    if (detail?.isEditable() == true) {
                        PbIconButton(icon = PbSymbols.Edit, contentDescription = "Editar", onClick = { navigator.push(ProductEditorScreen(idProduct)) })
                    }
                },
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().widthIn(max = PbListMaxWidth),
                    contentPadding = PaddingValues(bottom = PbSpace.s10),
                ) {
                    syncStatusItem(
                        state.sync,
                        hasRows = detail != null,
                        onRetry = model::refresh,
                        offlineEmpty = "Sin conexión. La ficha de este producto se ve con internet.",
                    )
                    if (detail != null) {
                        detail.product.image?.takeIf { it.isNotBlank() }?.let { url ->
                            item(key = "image") { ProductImage(url, detail.product.name) }
                        }
                        item(key = "card") { PriceCard(detail, state.taxRate, state.taxLabel, catalogs) }
                        if (!detail.isEditable()) {
                            item(key = "pos") {
                                PbBanner(
                                    message = if (detail.warehouses.any { it.unique == true }) {
                                        "Producto con código por unidad (IMEI o serie): se edita en el POS."
                                    } else {
                                        "Este producto tiene ${detail.warehouses.size} lotes con su propio precio: se edita en el POS."
                                    },
                                    tone = PbBannerTone.Offline,
                                    modifier = Modifier.padding(horizontal = PbSpace.s6),
                                )
                            }
                        }
                        item(key = "history-h") { PbListHeader("Entradas y salidas") }
                        if (detail.history.isEmpty()) {
                            item(key = "history-none") {
                                PbText(
                                    text = "Sin movimientos.",
                                    style = PbTheme.typography.body,
                                    color = PbTheme.colors.muted,
                                    modifier = Modifier.padding(horizontal = PbSpace.s6),
                                )
                            }
                        }
                        items(detail.history, key = { it.id }) { move ->
                            PbItemRow(
                                title = move.comment?.takeIf { it.isNotBlank() } ?: "Movimiento",
                                subtitle = listOfNotNull(
                                    parseApiTimestamp(move.createdAt)?.let { formatShortDate(it, state.timeZone) },
                                    move.user,
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
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceCard(
    detail: ProductDetailDto,
    rate: Double,
    taxLabel: String,
    catalogs: Map<ProductCatalog, List<CatalogItemDto>>,
) {
    val warehouse = detail.warehouses.singleOrNull()
    val price = warehouse?.price ?: detail.warehouses.mapNotNull { it.price }.minOrNull() ?: 0.0
    val stock = detail.warehouses.sumOf { it.amount ?: 0.0 }
    PbCard(Modifier.fillMaxWidth().padding(PbSpace.s6), verticalArrangement = Arrangement.spacedBy(PbSpace.s4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PbText(text = "Precio", style = PbTheme.typography.caption, color = PbTheme.colors.muted)
                PbText(text = formatMoney(price), style = PbTheme.typography.amountLarge)
            }
            Column(horizontalAlignment = Alignment.End) {
                PbText(text = "Existencia", style = PbTheme.typography.caption, color = PbTheme.colors.muted)
                StockTag(stock)
            }
        }
        if (warehouse != null) {
            // Informativo: el precio ya lo incluye (regla crítica 1).
            val tax = unitTax(price, detail.product.taxType, rate)
            val cost = warehouse.cost ?: 0.0
            Line("$taxLabel ${formatPercent(rate)} incluido", formatMoney(tax))
            Line("Costo", formatMoney(cost))
            Line("Ganancia por unidad", formatMoney(price - tax - cost))
            warehouse.minAmount?.takeIf { it > 0 }?.let { Line("Avisar con menos de", formatQuantity(it)) }
            warehouse.barcode?.takeIf { it.isNotBlank() }?.let { Line("Código de barras", it) }
            fun nameIn(kind: ProductCatalog, id: Int?) = id?.takeIf { it > 0 }?.let { i -> catalogs[kind]?.firstOrNull { it.id == i }?.name }
            nameIn(ProductCatalog.Categories, detail.product.idCategory)?.let { Line("Categoría", it) }
            nameIn(ProductCatalog.Brands, warehouse.idBrand)?.let { Line("Marca", it) }
            (nameIn(ProductCatalog.Colors, warehouse.idColor) ?: warehouse.color?.takeIf { it.isNotBlank() })?.let { Line("Color", it) }
        }
        detail.product.description?.takeIf { it.isNotBlank() }?.let {
            PbText(text = it, style = PbTheme.typography.body, color = PbTheme.colors.ink2)
        }
    }
}

/** La imagen del producto a lo ancho, cuadrada como en el catálogo del POS. Sin red, su hueco. */
@Composable
private fun ProductImage(url: String, name: String?) {
    val shape = RoundedCornerShape(PbRadius.lg)
    RemoteImage(
        url = url,
        contentDescription = name?.let { "Imagen de $it" } ?: "Imagen del producto",
        modifier = Modifier
            .padding(start = PbSpace.s6, end = PbSpace.s6, top = PbSpace.s6)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(PbTheme.colors.surface2, shape)
            .border(PbControl.border, PbTheme.colors.outline, shape),
        placeholder = { PbIcon(icon = PbSymbols.Image, contentDescription = null, tint = PbTheme.colors.muted2, size = PbIconSize.xl) },
    )
}

@Composable
private fun Line(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PbText(text = label, style = PbTheme.typography.body, color = PbTheme.colors.ink2, modifier = Modifier.weight(1f))
        PbText(text = value, style = PbTheme.typography.amount)
    }
}

/**
 * Alta rápida (`idProduct == null`) y edición de un producto general. `pickFor`: se abrió desde
 * el buscador del editor de factura; al crearlo queda como línea y se vuelve al editor.
 */
data class ProductEditorScreen(val idProduct: Int? = null, val pickFor: DocumentKind? = null) : Screen {
    override val key: String = "product-editor-${idProduct ?: "new"}-${pickFor?.name}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<ProductEditorScreenModel> { parametersOf(idProduct, pickFor) }
        val state by model.state.collectAsState()
        val form = state.form
        var more by rememberSaveable { mutableStateOf(idProduct != null) }

        LaunchedEffect(state.done) {
            if (!state.done) return@LaunchedEffect
            if (pickFor != null) navigator.popUntil { it is InvoiceEditorScreen } else navigator.pop()
        }

        PbFormScreen(
            title = if (idProduct == null) "Nuevo producto" else "Editar producto",
            saveLabel = if (idProduct == null) "Crear producto" else "Guardar cambios",
            dirty = state.dirty,
            saving = state.saving,
            error = state.error,
            saveEnabled = state.loaded,
            onSave = model::save,
            onBack = { navigator.pop() },
            overlay = {
                val kind = state.picking
                PbOptionSheet(
                    visible = kind != null,
                    title = kind?.singular ?: "",
                    options = kind?.let { k -> state.catalogs[k].orEmpty().map { PbOption(it.id, it.name) } }.orEmpty(),
                    selectedId = kind?.let(state::selectedId),
                    onSelect = model::selectCatalogItem,
                    onDismiss = model::closeCatalog,
                    noneLabel = kind?.let { if (it == ProductCatalog.Categories) "Sin categoría" else "Sin ${it.singular.lowercase()}" },
                    onCreate = model::createCatalogItem,
                    creating = state.creatingItem,
                    loading = state.catalogsLoading,
                    message = state.catalogMessage,
                )
            },
        ) {
            // Como la ficha rápida del POS: la imagen arriba, lo demás debajo.
            ImageField(
                label = "Imagen",
                url = form.image,
                picked = state.newImage,
                onPicked = model::pickImage,
                onRemove = model::removeImage,
                onError = model::imageFailed,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
            PbTextField(
                value = form.name,
                onValueChange = { v -> model.update { it.copy(name = v) } },
                label = "Nombre",
                required = true,
                error = state.nameError,
                modifier = Modifier.fillMaxWidth(),
            )
            PbTextField(
                value = form.price,
                onValueChange = { v -> model.update { it.copy(price = decimalInput(v)) } },
                label = "Precio de venta",
                required = true,
                prefix = "$",
                numeric = true,
                error = state.priceError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            form.price.toDoubleOrNull()?.takeIf { it > 0 }?.let { price ->
                PbText(
                    text = "Incluye ${formatMoney(unitTax(price, state.taxType, state.taxRate))} de ${state.taxLabel} (${formatPercent(state.taxRate)}).",
                    style = PbTheme.typography.caption,
                    color = PbTheme.colors.muted,
                )
            }
            PbTextField(
                value = form.amount,
                onValueChange = { v -> model.update { it.copy(amount = decimalInput(v, allowNegative = true)) } },
                label = if (idProduct == null) "Cantidad que tienes" else "Existencia",
                numeric = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            PbTextField(
                value = form.cost,
                onValueChange = { v -> model.update { it.copy(cost = decimalInput(v)) } },
                label = "Costo",
                prefix = "$",
                numeric = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            // Los mismos catálogos que el POS (`categories`, `brands`, `colors` de la tienda).
            PbSelectField(
                label = "Categoría",
                value = state.nameOf(ProductCatalog.Categories),
                placeholder = "Sin categoría",
                leadingIcon = PbSymbols.Category,
                onClick = { model.openCatalog(ProductCatalog.Categories) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
            PbSelectField(
                label = "Marca",
                value = state.nameOf(ProductCatalog.Brands),
                placeholder = "Sin marca",
                leadingIcon = PbSymbols.Sell,
                onClick = { model.openCatalog(ProductCatalog.Brands) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
            PbSelectField(
                label = "Color",
                value = state.nameOf(ProductCatalog.Colors),
                placeholder = "Sin color",
                leadingIcon = PbSymbols.Palette,
                onClick = { model.openCatalog(ProductCatalog.Colors) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
            PbSwitchRow(
                title = "Mostrar en el catálogo",
                subtitle = "Sale en el catálogo en línea que compartes con tus clientes.",
                checked = form.showOnCatalog == true,
                onCheckedChange = { v -> model.update { it.copy(showOnCatalog = v) } },
            )
            if (more) {
                PbTextField(
                    value = form.minAmount,
                    onValueChange = { v -> model.update { it.copy(minAmount = decimalInput(v)) } },
                    label = "Avisar cuando queden menos de",
                    numeric = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                PbTextField(
                    value = form.barcode,
                    onValueChange = { v -> model.update { it.copy(barcode = v.trim()) } },
                    label = "Código de barras",
                    modifier = Modifier.fillMaxWidth(),
                )
                PbTextField(
                    value = form.description,
                    onValueChange = { v -> model.update { it.copy(description = v) } },
                    label = "Descripción",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PbButton(
                    text = "Más datos (código de barras, mínimo, descripción)",
                    onClick = { more = true },
                    variant = PbButtonVariant.Ghost,
                    leadingIcon = PbSymbols.ExpandMore,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Deja escribir un número decimal (con punto); lo demás se descarta. */
private fun decimalInput(raw: String, allowNegative: Boolean = false): String {
    val negative = allowNegative && raw.trimStart().startsWith("-")
    val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    val number = if (firstDot < 0) cleaned else cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "").take(2)
    return if (negative) "-$number" else number
}
