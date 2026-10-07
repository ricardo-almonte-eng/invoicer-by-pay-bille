package com.paybille.invoicer.feature.document.domain

import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.core.format.formatIsoDate
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.format.formatQuantity
import com.paybille.invoicer.core.format.formatShortDate
import com.paybille.invoicer.core.format.parseApiTimestamp
import com.paybille.invoicer.feature.sales.domain.SaleStatus

/**
 * Lo que la plantilla necesita además de los datos: la tipografía de la marca (`@font-face`
 * con las fuentes embebidas), el isotipo del pie y el logo de la tienda ya descargado. Todo en
 * `data:` para que la factura se vea y se convierta en PDF sin red.
 */
data class InvoiceAssets(
    val fontCss: String = "",
    val brandMark: String? = null,
    /** Logo de la tienda en `data:`; si falta, se usa la URL (necesita red). */
    val logoDataUri: String? = null,
)

/**
 * Genera el HTML de la factura: datos de `ventas/factura/{id}/data` + configuración de la
 * tienda + plantilla. Es puro (sin red ni plataforma) para poder probarlo.
 */
object InvoiceHtml {

    /** Azul de PayBille: el acento de una tienda que no eligió otro. Papel, no tema. */
    const val DEFAULT_ACCENT = "#16426F"

    fun render(
        template: String,
        data: InvoiceData,
        config: InvoiceConfig,
        assets: InvoiceAssets = InvoiceAssets(),
        timeZone: String = DEFAULT_TIME_ZONE,
        todayIso: String? = null,
    ): String {
        val source = config.templateHtml?.takeIf { it.isNotBlank() } ?: template
        return MiniTemplate.render(source, model(data, config, assets, timeZone, todayIso))
    }

    fun model(
        data: InvoiceData,
        config: InvoiceConfig,
        assets: InvoiceAssets,
        timeZone: String,
        todayIso: String? = null,
    ): Map<String, Any?> {
        val sale = data.sale
        val status = sale.status
        val isQuote = status == SaleStatus.QUOTE
        val isVoid = status == SaleStatus.CANCELLED || status == SaleStatus.CANCELLED_ALT
        val receivable = data.receivable
        val balance = receivable?.balance ?: if (status == SaleStatus.PENDING) sale.total else 0.0
        val owes = !isQuote && !isVoid && balance > 0.004
        val dueIso = receivable?.dueDate?.takeIf { owes }
        val overdue = dueIso != null && todayIso != null && dueIso.take(10) < todayIso.take(10)

        val accent = config.accentColor?.trim()?.takeIf { HEX.matches(it) } ?: DEFAULT_ACCENT
        val kindLabel = if (isQuote) "Cotización" else "Factura"
        val title = if (isQuote) "Cotización" else config.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Factura"

        val issued = parseApiTimestamp(sale.createdAt)?.let { formatShortDate(it, timeZone) } ?: sale.date.orEmpty()
        val taxType = sale.taxType
        val taxLabel = data.market.taxLabel?.takeIf { it.isNotBlank() } ?: "ITBIS"
        val showTax = config.showTax != false && taxType != "no_tax" && sale.tax > 0.004

        val payments = if (config.showPayments != false && !isQuote) receivable?.payments.orEmpty() else emptyList()
        val paid = receivable?.paid ?: (sale.total - balance).coerceAtLeast(0.0)

        val payToAccounts = if (config.showPaymentAccounts != false && (owes || isQuote)) data.paymentAccounts else emptyList()
        val payNote = if (config.showPaymentNote != false && (owes || isQuote)) {
            data.paymentNote?.takeIf { it.isNotBlank() } ?: config.defaultPaymentNote?.takeIf { it.isNotBlank() }
        } else null

        val signatureSvg = if (config.showSignature == true) SignatureSvg.sanitize(config.signature) else null

        return mapOf(
            "fontCss" to assets.fontCss,
            "brandMark" to assets.brandMark,
            "accent" to accent,
            "accentSoft" to softOf(accent),
            "showStoreInfo" to (config.showStoreInfo != false),
            "showClient" to (config.showClient != false),
            "showClientContact" to (config.showClientContact != false),
            "doc" to mapOf(
                "title" to title,
                "kindLabel" to kindLabel,
                "number" to sale.number,
                "date" to issued,
                "dueDate" to dueIso?.takeIf { config.showDueDate != false }?.let(::formatIsoDate),
                "ncf" to sale.ncf?.takeIf { config.showNcf != false && it.isNotBlank() },
                "statusLabel" to when {
                    isQuote -> "Cotización"
                    isVoid -> "Anulada"
                    !owes -> "Pagada"
                    overdue -> "Vencida"
                    paid > 0.004 -> "Pago parcial"
                    else -> "Pendiente"
                },
                "statusTone" to when {
                    isQuote -> "quote"
                    isVoid -> "void"
                    !owes -> "paid"
                    overdue -> "overdue"
                    else -> "pending"
                },
            ),
            "store" to mapOf(
                "name" to data.market.name,
                "rnc" to data.market.rnc,
                "address" to data.market.address,
                "phone" to data.market.phone,
                "email" to data.market.email,
                "logo" to if (config.showLogo != false) assets.logoDataUri ?: data.market.logo?.takeIf { it.startsWith("http") } else null,
            ),
            "client" to mapOf(
                "name" to data.client.name,
                "identify" to data.client.identify,
                "address" to data.client.address,
                "phone" to data.client.phone,
                "email" to data.client.email,
            ),
            "items" to data.items.map { item ->
                mapOf(
                    "name" to item.name,
                    "detail" to item.discount.takeIf { it > 0.004 }?.let { "Descuento ${formatMoney(it)}" },
                    "quantity" to formatQuantity(item.quantity),
                    "price" to formatMoney(item.price),
                    "total" to formatMoney(item.total),
                )
            },
            "totals" to mapOf(
                "subtotal" to formatMoney(sale.subtotal),
                "discount" to sale.discount.takeIf { it > 0.004 }?.let { formatMoney(it) },
                "tax" to if (showTax) formatMoney(sale.tax) else null,
                "taxLabel" to if (taxType == "included") "$taxLabel (incluido)" else taxLabel,
                "total" to formatMoney(sale.total),
                "paid" to if (!isQuote && paid > 0.004 && config.showBalance != false) formatMoney(paid) else null,
            ),
            "balance" to if (owes && config.showBalance != false) {
                mapOf("amount" to formatMoney(balance), "due" to dueIso?.let(::formatIsoDate))
            } else null,
            "paidStamp" to (!isQuote && !isVoid && !owes && sale.total > 0.004),
            "payments" to mapOf(
                "length" to payments.size,
                "rows" to payments.map { p ->
                    mapOf(
                        "date" to (parseApiTimestamp(p.date)?.let { formatShortDate(it, timeZone) } ?: p.date?.take(10)?.let(::formatIsoDate).orEmpty()),
                        "method" to methodLabel(p.method),
                        "reference" to p.reference,
                        "amount" to formatMoney(p.amount),
                    )
                },
            ),
            "payTo" to if (payToAccounts.isNotEmpty() || payNote != null) {
                mapOf(
                    "hasAccounts" to payToAccounts.isNotEmpty(),
                    "accounts" to payToAccounts.map {
                        mapOf(
                            "bank" to it.bank,
                            "type" to it.type,
                            "number" to it.number,
                            "holderName" to it.holderName.takeIf { h -> h.isNotBlank() },
                            "holderId" to it.holderId.takeIf { h -> h.isNotBlank() },
                        )
                    },
                    "note" to payNote,
                )
            } else null,
            "saleNote" to sale.note?.takeIf { it.isNotBlank() },
            "terms" to config.terms?.takeIf { config.showTerms == true && it.isNotBlank() },
            "signature" to signatureSvg?.let {
                mapOf(
                    "svg" to it,
                    "name" to (config.signatureName?.takeIf { n -> n.isNotBlank() } ?: data.market.name),
                    "date" to issued,
                )
            },
            "footer" to config.footerMessage?.takeIf { it.isNotBlank() },
        )
    }

    /** Nombre del método de pago para el cliente. `Deposito` es como el POS guarda la transferencia. */
    fun methodLabel(apiValue: String): String = when (apiValue) {
        "Deposito", "Transferencia" -> "Transferencia"
        "" -> "Pago"
        else -> apiValue
    }

    /** El acento al 10 % sobre blanco, para el fondo del saldo. */
    fun softOf(hex: String): String {
        val clean = hex.removePrefix("#")
        if (clean.length != 6) return "#EEF2F9"
        val channels = (0 until 3).map { clean.substring(it * 2, it * 2 + 2).toInt(16) }
        return "#" + channels.joinToString("") { c -> (255 - (255 - c) * 10 / 100).toString(16).padStart(2, '0') }.uppercase()
    }

    private val HEX = Regex("#[0-9a-fA-F]{6}")
}
