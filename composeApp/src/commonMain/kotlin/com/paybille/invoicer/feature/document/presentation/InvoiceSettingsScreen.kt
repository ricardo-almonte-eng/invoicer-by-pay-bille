package com.paybille.invoicer.feature.document.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbFormScreen
import com.paybille.invoicer.core.designsystem.components.PbIcon
import com.paybille.invoicer.core.designsystem.components.PbOverline
import com.paybille.invoicer.core.designsystem.components.PbSection
import com.paybille.invoicer.core.designsystem.components.PbSpinner
import com.paybille.invoicer.core.designsystem.components.PbSwitchRow
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.components.PbTextField
import com.paybille.invoicer.core.designsystem.theme.InvoiceAccentOptions
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.platform.HtmlView
import com.paybille.invoicer.feature.detail.presentation.InvoiceViewerScreen
import com.paybille.invoicer.feature.document.domain.InvoiceHtml

/**
 * Diseño de la factura de la tienda: qué secciones salen, el título, el color, los textos y la
 * firma. La vista previa de arriba es una factura de ejemplo con lo que se está eligiendo.
 * Afecta también a las facturas ya emitidas: la app las genera al abrirlas.
 */
data object InvoiceSettingsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<InvoiceSettingsScreenModel>()
        val state by model.state.collectAsState()
        val config = state.config

        LaunchedEffect(state.done) { if (state.done) navigator.pop() }

        PbFormScreen(
            title = "Diseño de factura",
            saveLabel = "Guardar diseño",
            dirty = state.dirty,
            saving = state.saving,
            error = state.error,
            saveEnabled = state.loaded && !state.offline,
            onSave = model::save,
            onBack = { navigator.pop() },
        ) {
            if (state.offline) {
                PbBanner(
                    message = "Sin conexión. Ves lo guardado en el teléfono; para cambiar el diseño hace falta internet.",
                    tone = PbBannerTone.Offline,
                )
                PbButton(
                    text = "Reintentar",
                    onClick = model::load,
                    variant = PbButtonVariant.Outline,
                    leadingIcon = PbSymbols.Sync,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Preview(html = state.previewHtml, onOpen = { html -> navigator.push(InvoiceViewerScreen(0, html, "Vista previa")) })

            PbSection(title = "Encabezado", icon = PbSymbols.Palette) {
                PbTextField(
                    value = config.title.orEmpty(),
                    onValueChange = { v -> model.update { it.copy(title = v.take(40)) } },
                    label = "Título",
                    placeholder = "Factura",
                    enabled = state.loaded,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(PbSpace.s3)) {
                    PbOverline(text = "Color")
                    AccentPicker(
                        selected = config.accentColor ?: InvoiceHtml.DEFAULT_ACCENT,
                        onSelect = { hex -> model.update { it.copy(accentColor = hex.takeIf { h -> h != InvoiceHtml.DEFAULT_ACCENT }) } },
                    )
                }
                PbSwitchRow(
                    title = "Logo",
                    subtitle = "El de Configurar tienda.",
                    checked = config.showLogo != false,
                    onCheckedChange = { v -> model.update { it.copy(showLogo = v) } },
                )
                PbSwitchRow(
                    title = "Datos del negocio",
                    subtitle = "RNC, dirección, teléfono y correo.",
                    checked = config.showStoreInfo != false,
                    onCheckedChange = { v -> model.update { it.copy(showStoreInfo = v) } },
                )
            }

            PbSection(title = "Cliente", icon = PbSymbols.Person) {
                PbSwitchRow(
                    title = "Datos del cliente",
                    checked = config.showClient != false,
                    onCheckedChange = { v -> model.update { it.copy(showClient = v) } },
                )
                if (config.showClient != false) {
                    PbSwitchRow(
                        title = "Contacto del cliente",
                        subtitle = "Cédula o RNC, dirección, teléfono y correo de su ficha.",
                        checked = config.showClientContact != false,
                        onCheckedChange = { v -> model.update { it.copy(showClientContact = v) } },
                    )
                }
            }

            PbSection(title = "Detalle", icon = PbSymbols.ReceiptLong) {
                PbSwitchRow(
                    title = "Fecha de vencimiento",
                    checked = config.showDueDate != false,
                    onCheckedChange = { v -> model.update { it.copy(showDueDate = v) } },
                )
                PbSwitchRow(
                    title = "NCF",
                    subtitle = "Solo en las facturas que lo llevan.",
                    checked = config.showNcf != false,
                    onCheckedChange = { v -> model.update { it.copy(showNcf = v) } },
                )
                PbSwitchRow(
                    title = "Impuesto",
                    subtitle = "La línea de ITBIS en los totales.",
                    checked = config.showTax != false,
                    onCheckedChange = { v -> model.update { it.copy(showTax = v) } },
                )
            }

            PbSection(title = "Cobro", icon = PbSymbols.Payments) {
                PbSwitchRow(
                    title = "Saldo pendiente",
                    subtitle = "Lo pagado y lo que falta por pagar.",
                    checked = config.showBalance != false,
                    onCheckedChange = { v -> model.update { it.copy(showBalance = v) } },
                )
                PbSwitchRow(
                    title = "Pagos recibidos",
                    subtitle = "Cada abono con su fecha y forma de pago.",
                    checked = config.showPayments != false,
                    onCheckedChange = { v -> model.update { it.copy(showPayments = v) } },
                )
                PbSwitchRow(
                    title = "Dónde pagar",
                    subtitle = "Las cuentas que eliges al crear la factura.",
                    checked = config.showPaymentAccounts != false,
                    onCheckedChange = { v -> model.update { it.copy(showPaymentAccounts = v) } },
                )
                PbSwitchRow(
                    title = "Instrucciones de pago",
                    checked = config.showPaymentNote != false,
                    onCheckedChange = { v -> model.update { it.copy(showPaymentNote = v) } },
                )
                if (config.showPaymentNote != false) {
                    PbTextField(
                        value = config.defaultPaymentNote.orEmpty(),
                        onValueChange = { v -> model.update { it.copy(defaultPaymentNote = v.take(MAX_TEXT)) } },
                        label = "Instrucciones por defecto",
                        placeholder = "Envía el comprobante por WhatsApp al…",
                        singleLine = false,
                        enabled = state.loaded,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PbText(
                        text = "Salen cuando la factura no trae las suyas. Solo en lo que se debe o se cotiza.",
                        style = PbTheme.typography.caption,
                        color = PbTheme.colors.muted,
                    )
                }
            }

            PbSection(title = "Firma y cierre", icon = PbSymbols.Edit) {
                PbSwitchRow(
                    title = "Condiciones",
                    subtitle = "Un texto antes de la firma.",
                    checked = config.showTerms == true,
                    onCheckedChange = { v -> model.update { it.copy(showTerms = v) } },
                )
                if (config.showTerms == true) {
                    PbTextField(
                        value = config.terms.orEmpty(),
                        onValueChange = { v -> model.update { it.copy(terms = v.take(MAX_TEXT)) } },
                        label = "Condiciones",
                        singleLine = false,
                        enabled = state.loaded,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PbSwitchRow(
                    title = "Firma",
                    checked = config.showSignature == true,
                    onCheckedChange = { v -> model.update { it.copy(showSignature = v) } },
                )
                if (config.showSignature == true) {
                    SignaturePad(strokes = state.strokes, onStrokesChange = model::setStrokes, enabled = state.loaded)
                    if (state.strokes.isNotEmpty()) {
                        PbButton(
                            text = "Borrar firma",
                            onClick = model::clearSignature,
                            variant = PbButtonVariant.Outline,
                            leadingIcon = PbSymbols.Delete,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    PbTextField(
                        value = config.signatureName.orEmpty(),
                        onValueChange = { v -> model.update { it.copy(signatureName = v.take(120)) } },
                        label = "Nombre bajo la firma",
                        placeholder = "El de la tienda",
                        enabled = state.loaded,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PbTextField(
                    value = config.footerMessage.orEmpty(),
                    onValueChange = { v -> model.update { it.copy(footerMessage = v.take(MAX_TEXT)) } },
                    label = "Mensaje final",
                    placeholder = "Gracias por su compra.",
                    singleLine = false,
                    enabled = state.loaded,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PbText(
                text = "El diseño se aplica a todas tus facturas, también a las ya emitidas: el teléfono las genera al abrirlas.",
                style = PbTheme.typography.caption,
                color = PbTheme.colors.muted,
            )
        }
    }
}

private const val MAX_TEXT = 600

@Composable
private fun Preview(html: String?, onOpen: (String) -> Unit) {
    val colors = PbTheme.colors
    val shape = RoundedCornerShape(PbRadius.md)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(PREVIEW_RATIO)
                .clip(shape)
                .background(colors.surfaceSubtle)
                .border(PbControl.border, colors.outlineStrong, shape),
        ) {
            if (html == null) {
                PbSpinner(Modifier.align(Alignment.Center))
            } else {
                HtmlView(html = html, modifier = Modifier.fillMaxSize(), interactive = false)
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(role = Role.Button, onClickLabel = "Ver la vista previa completa") { onOpen(html) }
                        .semantics { contentDescription = "Vista previa de la factura. Toca para verla completa." },
                )
            }
        }
    }
}

/** Ancho / alto de la vista previa: un poco más baja que un A4 para no ocupar toda la pantalla. */
private const val PREVIEW_RATIO = 0.8f

@Composable
private fun AccentPicker(selected: String, onSelect: (String) -> Unit) {
    val colors = PbTheme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PbSpace.s4),
        verticalArrangement = Arrangement.spacedBy(PbSpace.s4),
    ) {
        InvoiceAccentOptions.forEach { (hex, color) ->
            val isSelected = hex.equals(selected, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(PbControl.h)
                    .clip(CircleShape)
                    .border(if (isSelected) 2.dp else PbControl.border, if (isSelected) colors.primary else colors.outline, CircleShape)
                    .clickable(role = Role.RadioButton) { onSelect(hex) }
                    .semantics {
                        contentDescription = "Color $hex"
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(PbControl.h - 12.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    if (isSelected) PbIcon(icon = PbSymbols.Check, contentDescription = null, tint = colors.logoPlate, size = 20.dp)
                }
            }
        }
    }
}
