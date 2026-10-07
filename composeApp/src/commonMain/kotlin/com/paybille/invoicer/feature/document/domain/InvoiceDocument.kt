package com.paybille.invoicer.feature.document.domain

import com.paybille.invoicer.core.network.LenientBooleanSerializer
import com.paybille.invoicer.core.network.LenientIntSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lo que devuelve `GET ventas/factura/{id}/data`: todo lo que la app necesita para GENERAR la
 * factura ella misma (plantilla HTML + [InvoiceConfig]). Sustituye al PDF que armaba el
 * servidor con Puppeteer. Se guarda tal cual en `cached_payloads`, así que la factura se ve
 * también sin red. Importes en moneda base y como NÚMERO (el endpoint ya hace `Number()`; si
 * algún día llegara un DECIMAL como string, `PayBilleJson` es `isLenient` y lo acepta).
 */
@Serializable
data class InvoiceData(
    val sale: InvoiceSale,
    val items: List<InvoiceItem> = emptyList(),
    val client: InvoiceClient = InvoiceClient(),
    val market: InvoiceMarket,
    val receivable: InvoiceReceivable? = null,
    val paymentAccounts: List<InvoicePayTo> = emptyList(),
    val paymentNote: String? = null,
    val config: InvoiceConfig? = null,
)

@Serializable
data class InvoiceSale(
    val id: Int,
    val number: String,
    /** `sales.Status` tal cual. */
    val status: String,
    /** `sales.Date`: texto de presentación, NO una fecha (regla 3). */
    val date: String? = null,
    val createdAt: String? = null,
    val ncf: String? = null,
    val taxType: String? = null,
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val discount: Double = 0.0,
    val total: Double = 0.0,
    val cash: Double = 0.0,
    val transfer: Double = 0.0,
    val card: Double = 0.0,
    val change: Double = 0.0,
    val seller: String? = null,
    val note: String? = null,
)

@Serializable
data class InvoiceItem(
    val name: String,
    val barcode: String? = null,
    val quantity: Double = 0.0,
    val price: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    /** Bruto de la línea: cantidad × precio − descuento. */
    val total: Double = 0.0,
)

@Serializable
data class InvoiceClient(
    val name: String = "Consumidor final",
    val identify: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
)

@Serializable
data class InvoiceMarket(
    @Serializable(LenientIntSerializer::class) val id: Int? = null,
    val name: String = "",
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val rnc: String? = null,
    val logo: String? = null,
    val taxLabel: String? = null,
)

@Serializable
data class InvoiceReceivable(
    val total: Double = 0.0,
    val paid: Double = 0.0,
    val balance: Double = 0.0,
    /** `YYYY-MM-DD`. */
    val dueDate: String? = null,
    val status: String? = null,
    val payments: List<InvoicePayment> = emptyList(),
)

@Serializable
data class InvoicePayment(
    /** ISO: aquí SÍ es una fecha. */
    val date: String? = null,
    val method: String = "",
    val amount: Double = 0.0,
    val reference: String? = null,
)

@Serializable
data class InvoicePayTo(
    val bank: String = "",
    val type: String = "",
    val number: String = "",
    val holderName: String = "",
    val holderId: String = "",
)

/**
 * Configuración de la factura, una por tienda (`invoiceconfig`, sql/F5). Decide qué secciones
 * salen y con qué textos. `templateHtml` es la plantilla entera guardada como texto; vacía =
 * la que trae la app (`files/invoice_template.html`).
 */
@Serializable
data class InvoiceConfig(
    @SerialName("Title") val title: String? = "Factura",
    @SerialName("AccentColor") val accentColor: String? = null,
    @SerialName("ShowLogo") @Serializable(LenientBooleanSerializer::class) val showLogo: Boolean? = true,
    @SerialName("ShowStoreInfo") @Serializable(LenientBooleanSerializer::class) val showStoreInfo: Boolean? = true,
    @SerialName("ShowClient") @Serializable(LenientBooleanSerializer::class) val showClient: Boolean? = true,
    @SerialName("ShowClientContact") @Serializable(LenientBooleanSerializer::class) val showClientContact: Boolean? = true,
    @SerialName("ShowDueDate") @Serializable(LenientBooleanSerializer::class) val showDueDate: Boolean? = true,
    @SerialName("ShowNcf") @Serializable(LenientBooleanSerializer::class) val showNcf: Boolean? = true,
    @SerialName("ShowTax") @Serializable(LenientBooleanSerializer::class) val showTax: Boolean? = true,
    @SerialName("ShowPayments") @Serializable(LenientBooleanSerializer::class) val showPayments: Boolean? = true,
    @SerialName("ShowBalance") @Serializable(LenientBooleanSerializer::class) val showBalance: Boolean? = true,
    @SerialName("ShowPaymentAccounts") @Serializable(LenientBooleanSerializer::class) val showPaymentAccounts: Boolean? = true,
    @SerialName("ShowPaymentNote") @Serializable(LenientBooleanSerializer::class) val showPaymentNote: Boolean? = true,
    @SerialName("DefaultPaymentNote") val defaultPaymentNote: String? = null,
    @SerialName("ShowTerms") @Serializable(LenientBooleanSerializer::class) val showTerms: Boolean? = false,
    @SerialName("Terms") val terms: String? = null,
    @SerialName("ShowSignature") @Serializable(LenientBooleanSerializer::class) val showSignature: Boolean? = false,
    @SerialName("SignatureName") val signatureName: String? = null,
    /** Firma dibujada en la app, como SVG. */
    @SerialName("Signature") val signature: String? = null,
    @SerialName("FooterMessage") val footerMessage: String? = null,
    @SerialName("TemplateHtml") val templateHtml: String? = null,
)
