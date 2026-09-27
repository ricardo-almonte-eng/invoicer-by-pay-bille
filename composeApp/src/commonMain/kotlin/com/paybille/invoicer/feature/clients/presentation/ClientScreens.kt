package com.paybille.invoicer.feature.clients.presentation

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbCard
import com.paybille.invoicer.core.designsystem.components.PbChipGroup
import com.paybille.invoicer.core.designsystem.components.PbFormScreen
import com.paybille.invoicer.core.designsystem.components.PbIconButton
import com.paybille.invoicer.core.designsystem.components.PbItemRow
import com.paybille.invoicer.core.designsystem.components.PbListHeader
import com.paybille.invoicer.core.designsystem.components.PbListMaxWidth
import com.paybille.invoicer.core.designsystem.components.PbRowAmount
import com.paybille.invoicer.core.designsystem.components.PbSpinnerRow
import com.paybille.invoicer.core.designsystem.components.PbStackHeader
import com.paybille.invoicer.core.designsystem.components.PbSwitchRow
import com.paybille.invoicer.core.designsystem.components.PbTag
import com.paybille.invoicer.core.designsystem.components.PbTagTone
import com.paybille.invoicer.core.designsystem.components.PbOverline
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.components.PbTextField
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.formatCedula
import com.paybille.invoicer.core.format.formatIsoDate
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.ui.syncStatusItem
import com.paybille.invoicer.feature.clients.data.local.ClientEntity
import com.paybille.invoicer.feature.detail.presentation.SaleDetailScreen
import com.paybille.invoicer.feature.invoice.domain.DocumentKind
import com.paybille.invoicer.feature.invoice.presentation.InvoiceEditorScreen
import com.paybille.invoicer.feature.sales.data.toSummary
import com.paybille.invoicer.feature.sales.presentation.SaleRow
import org.koin.core.parameter.parametersOf

/** Ficha de un cliente: sus datos, lo que debe y sus facturas. `name`: por si aún no está en el teléfono. */
data class ClientDetailScreen(val id: Int, val name: String? = null) : Screen {
    override val key: String = "client-$id"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<ClientDetailScreenModel> { parametersOf(id) }
        val state by model.state.collectAsState()
        val client by model.client.collectAsState()
        val activity by model.activity.collectAsState()
        val docs = activity?.value?.docs.orEmpty()
        val sales = activity?.value?.sales.orEmpty()
        val owed = docs.sumOf { it.balance ?: 0.0 }

        Column(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
            PbStackHeader(
                title = client?.fullName?.ifBlank { null } ?: name ?: "Cliente",
                onBack = { navigator.pop() },
                actions = {
                    if (client != null) {
                        PbIconButton(icon = PbSymbols.Edit, contentDescription = "Editar", onClick = { navigator.push(ClientEditorScreen(id)) })
                    }
                },
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().widthIn(max = PbListMaxWidth),
                    contentPadding = PaddingValues(bottom = PbSpace.s10),
                ) {
                    client?.let { c -> item(key = "card") { ContactCard(c) } }
                    item(key = "invoice") {
                        PbButton(
                            text = "Nueva factura",
                            onClick = { model.newInvoice { navigator.push(InvoiceEditorScreen(DocumentKind.Invoice)) } },
                            variant = PbButtonVariant.Outline,
                            leadingIcon = PbSymbols.Add,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = PbSpace.s6),
                        )
                    }
                    syncStatusItem(
                        state.sync,
                        hasRows = activity != null,
                        onRetry = model::refresh,
                        offlineEmpty = "Sin conexión. Lo que debe y sus facturas se ven con internet.",
                    )
                    if (activity != null) {
                        item(key = "owed-h") {
                            PbListHeader("Te debe") {
                                PbText(text = formatMoney(owed), style = PbTheme.typography.amount, color = if (owed > 0.004) PbTheme.colors.error else PbTheme.colors.ink)
                            }
                        }
                        if (docs.isEmpty()) {
                            item(key = "owed-none") { Muted("No tiene nada pendiente.") }
                        }
                        items(docs, key = { "doc-${it.id}" }) { doc ->
                            PbItemRow(
                                title = "Factura #${doc.secuency ?: doc.idSale ?: doc.id}",
                                subtitle = (doc.nextDueDate ?: doc.dueDate)?.let { "Vence ${formatIsoDate(it)}" },
                                icon = PbSymbols.RequestQuote,
                                onClick = doc.idSale?.let { sale -> { navigator.push(SaleDetailScreen(saleId = sale)) } },
                            ) {
                                PbRowAmount(amount = formatMoney(doc.balance ?: 0.0), caption = "de ${formatMoney(doc.total ?: 0.0)}")
                            }
                        }
                        item(key = "sales-h") { PbListHeader("Facturas y cotizaciones") }
                        if (sales.isEmpty()) {
                            item(key = "sales-none") { Muted("Todavía no tiene facturas.") }
                        }
                        items(sales, key = { "sale-${it.id}" }) { sale ->
                            SaleRow(
                                sale = sale.toSummary(),
                                timeZone = state.timeZone,
                                onClick = { navigator.push(SaleDetailScreen(saleId = sale.id)) },
                            )
                        }
                    } else if (state.sync.syncing) {
                        item(key = "wait") { PbSpinnerRow() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Muted(text: String) {
    PbText(
        text = text,
        style = PbTheme.typography.body,
        color = PbTheme.colors.muted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PbSpace.s6, vertical = PbSpace.s3),
    )
}

@Composable
private fun ContactCard(client: ClientEntity) {
    PbCard(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(PbSpace.s6),
        verticalArrangement = Arrangement.spacedBy(PbSpace.s3),
    ) {
        Field("Teléfono", client.phone) { if (client.whatsapp) PbTag(text = "WhatsApp", tone = PbTagTone.Success) }
        Field(IDENTIFY_TYPES.firstOrNull { it.first == client.identifyType }?.second ?: "Documento", client.identify?.let(::formatCedula))
        Field("Correo", client.email)
        Field("Dirección", client.address)
        client.discount?.takeIf { it > 0 }?.let { Field("Descuento", "$it %") }
        if (client.phone == null && client.identify == null && client.email == null && client.address == null) {
            PbText(text = "Sin datos de contacto.", style = PbTheme.typography.body, color = PbTheme.colors.muted)
        }
    }
}

@Composable
private fun Field(label: String, value: String?, trailing: @Composable () -> Unit = {}) {
    if (value.isNullOrBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PbSpace.s3)) {
        Column(Modifier.weight(1f)) {
            PbText(text = label, style = PbTheme.typography.caption, color = PbTheme.colors.muted)
            PbText(text = value, style = PbTheme.typography.body)
        }
        trailing()
    }
}

/**
 * Alta (`id == null`) y edición de un cliente (`CreateClient.vue` sin los datos financieros, que
 * son de financiamientos). `pickFor`: se abrió desde el buscador del editor de factura; al
 * guardar queda puesto en el borrador y se vuelve al editor.
 */
data class ClientEditorScreen(val id: Int? = null, val pickFor: DocumentKind? = null) : Screen {
    override val key: String = "client-editor-${id ?: "new"}-${pickFor?.name}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<ClientEditorScreenModel> { parametersOf(id, pickFor) }
        val state by model.state.collectAsState()
        val form = state.form
        var more by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(state.done) {
            if (!state.done) return@LaunchedEffect
            if (pickFor != null) navigator.popUntil { it is InvoiceEditorScreen } else navigator.pop()
        }

        PbFormScreen(
            title = if (id == null) "Nuevo cliente" else "Editar cliente",
            saveLabel = if (id == null) "Crear cliente" else "Guardar cambios",
            dirty = state.dirty,
            saving = state.saving,
            error = state.error,
            saveEnabled = state.loaded,
            onSave = model::save,
            onBack = { navigator.pop() },
        ) {
            PbTextField(
                value = form.firstName,
                onValueChange = { v -> model.update { it.copy(firstName = v) } },
                label = "Nombre",
                required = true,
                error = state.nameError,
                modifier = Modifier.fillMaxWidth(),
            )
            PbTextField(
                value = form.lastName,
                onValueChange = { v -> model.update { it.copy(lastName = v) } },
                label = "Apellido",
                modifier = Modifier.fillMaxWidth(),
            )
            PbTextField(
                value = form.phone,
                onValueChange = { v -> model.update { it.copy(phone = v) } },
                label = "Teléfono",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            PbSwitchRow(
                title = "Tiene WhatsApp",
                checked = form.whatsapp,
                onCheckedChange = { v -> model.update { it.copy(whatsapp = v) } },
            )
            PbOverline(text = "Documento")
            PbChipGroup(
                options = IDENTIFY_TYPES.map { it.first },
                selected = form.identifyType,
                label = { type -> IDENTIFY_TYPES.first { it.first == type }.second },
                onSelect = { v -> model.update { it.copy(identifyType = v) } },
            )
            val isCedula = form.identifyType == IDENTIFY_TYPES.first().first
            PbTextField(
                value = form.identify,
                onValueChange = { v -> model.update { it.copy(identify = v) } },
                label = IDENTIFY_TYPES.first { it.first == form.identifyType }.second,
                placeholder = if (isCedula) "000-0000000-0" else null,
                keyboardOptions = KeyboardOptions(keyboardType = if (isCedula) KeyboardType.Number else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            // Lo que casi nunca se toca va plegado, y se abre solo si ya trae algo (guía 07).
            val hasMore = form.email.isNotBlank() || form.address.isNotBlank() || form.discount.isNotBlank() || !form.payItbis
            if (more || hasMore) {
                PbTextField(
                    value = form.email,
                    onValueChange = { v -> model.update { it.copy(email = v) } },
                    label = "Correo",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                PbTextField(
                    value = form.address,
                    onValueChange = { v -> model.update { it.copy(address = v) } },
                    label = "Dirección",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                PbTextField(
                    value = form.discount,
                    onValueChange = { v -> model.update { it.copy(discount = v.filter(Char::isDigit).take(3)) } },
                    label = "Descuento",
                    suffix = "%",
                    numeric = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                PbSwitchRow(
                    title = "Paga ITBIS",
                    subtitle = "Apágalo si el cliente está exento.",
                    checked = form.payItbis,
                    onCheckedChange = { v -> model.update { it.copy(payItbis = v) } },
                )
            } else {
                PbButton(
                    text = "Más datos (correo, dirección, descuento)",
                    onClick = { more = true },
                    variant = PbButtonVariant.Ghost,
                    leadingIcon = PbSymbols.ExpandMore,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
