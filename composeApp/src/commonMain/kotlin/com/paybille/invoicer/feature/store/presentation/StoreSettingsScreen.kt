package com.paybille.invoicer.feature.store.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.paybille.invoicer.core.billing.TaxType
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbChipGroup
import com.paybille.invoicer.core.designsystem.components.PbFormScreen
import com.paybille.invoicer.core.designsystem.components.PbOverline
import com.paybille.invoicer.core.designsystem.components.PbSection
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.components.PbTextField
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.ui.ImageField

/**
 * Configuración de la tienda, versión reducida de `configuracion/tienda.vue`: lo que sale en la
 * factura que recibe el cliente y el impuesto con el que nace cada factura. Banco, garantía,
 * redes, parámetros, impresora, zona horaria y NCF se configuran en el POS.
 */
data object StoreSettingsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<StoreSettingsScreenModel>()
        val state by model.state.collectAsState()
        val form = state.form

        LaunchedEffect(state.done) { if (state.done) navigator.pop() }

        PbFormScreen(
            title = "Configuración de la tienda",
            saveLabel = "Guardar cambios",
            dirty = state.dirty,
            saving = state.saving,
            error = state.error,
            saveEnabled = state.loaded,
            onSave = model::save,
            onBack = { navigator.pop() },
        ) {
            if (state.offline) {
                PbBanner(
                    message = "Sin conexión. Ves lo guardado en el teléfono; para cambiar la tienda hace falta internet.",
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

            PbSection(title = "En tus facturas", icon = PbSymbols.ReceiptLong) {
                PbText(
                    text = "Así aparece tu negocio en el PDF que recibe el cliente. Las facturas ya " +
                        "emitidas no cambian.",
                    style = PbTheme.typography.caption,
                    color = PbTheme.colors.muted,
                )
                ImageField(
                    label = "Logo",
                    url = form.image,
                    picked = state.newLogo,
                    onPicked = model::pickLogo,
                    onRemove = model::removeLogo,
                    onError = model::logoFailed,
                    // Un logo no se recorta.
                    contentScale = ContentScale.Fit,
                    enabled = state.loaded && !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                PbTextField(
                    value = form.name,
                    onValueChange = { v -> model.update { it.copy(name = v) } },
                    label = "Nombre del negocio",
                    required = true,
                    error = state.nameError,
                    enabled = state.loaded,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                PbTextField(
                    value = form.rnc,
                    onValueChange = { v -> model.update { it.copy(rnc = v) } },
                    label = "RNC o cédula",
                    enabled = state.loaded,
                    numeric = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                PbTextField(
                    value = form.phone,
                    onValueChange = { v -> model.update { it.copy(phone = v) } },
                    label = "Teléfono",
                    leadingIcon = PbSymbols.Call,
                    enabled = state.loaded,
                    numeric = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                PbTextField(
                    value = form.mail,
                    onValueChange = { v -> model.update { it.copy(mail = v.trim()) } },
                    label = "Correo",
                    leadingIcon = PbSymbols.Mail,
                    enabled = state.loaded,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                PbTextField(
                    value = form.address,
                    onValueChange = { v -> model.update { it.copy(address = v) } },
                    label = "Dirección",
                    required = true,
                    leadingIcon = PbSymbols.PinDrop,
                    error = state.addressError,
                    enabled = state.loaded,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PbSection(title = "Impuesto", icon = PbSymbols.Percent) {
                PbText(
                    text = "Con esto nace cada factura nueva; en la factura se puede cambiar.",
                    style = PbTheme.typography.caption,
                    color = PbTheme.colors.muted,
                )
                Column(verticalArrangement = Arrangement.spacedBy(PbSpace.s2)) {
                    PbOverline(text = "Cómo se cobra")
                    PbChipGroup(
                        options = TaxType.entries,
                        selected = form.taxType,
                        label = { it.label },
                        onSelect = { type -> model.update { it.copy(taxType = type) } },
                    )
                }
                if (form.taxType != TaxType.NoTax) {
                    Column(verticalArrangement = Arrangement.spacedBy(PbSpace.s2)) {
                        PbOverline(text = "Nombre")
                        PbChipGroup(
                            options = TAX_LABELS,
                            selected = form.taxLabel,
                            label = { it },
                            onSelect = { label -> model.update { it.copy(taxLabel = label) } },
                        )
                    }
                    PbTextField(
                        value = form.taxPercent,
                        onValueChange = { v -> model.update { it.copy(taxPercent = v.filter { c -> c.isDigit() || c == '.' || c == ',' }) } },
                        label = "Tasa",
                        suffix = "%",
                        numeric = true,
                        error = state.taxError,
                        enabled = state.loaded,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            PbText(
                text = "Banco, garantía, NCF, impresora y los demás ajustes del mostrador se configuran en el POS.",
                style = PbTheme.typography.caption,
                color = PbTheme.colors.muted,
            )
        }
    }
}
