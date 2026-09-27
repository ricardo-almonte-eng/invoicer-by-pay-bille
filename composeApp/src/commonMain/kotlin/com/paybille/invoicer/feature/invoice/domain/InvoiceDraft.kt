package com.paybille.invoicer.feature.invoice.domain

import com.paybille.invoicer.core.billing.Currency
import com.paybille.invoicer.core.billing.DocumentTotals
import com.paybille.invoicer.core.billing.LineInput
import com.paybille.invoicer.core.billing.TaxType
import com.paybille.invoicer.core.billing.documentTotals
import com.paybille.invoicer.core.billing.lineTotals
import com.paybille.invoicer.core.billing.round2
import com.paybille.invoicer.feature.sales.domain.SaleStatus
import kotlinx.serialization.Serializable

/** Qué documento se está creando. El título se decide desde el primer frame (guía 07). */
@Serializable
enum class DocumentKind(val title: String, val createLabel: String, val saveLabel: String) {
    Invoice("Nueva factura", "Crear factura", "Cobrar"),
    Quote("Nueva cotización", "Crear cotización", "Guardar cotización"),
}

/** Tipos de NCF que se usan en una factura. B03/B04 son de notas de débito/crédito. */
@Serializable
enum class NcfType(val code: String, val label: String) {
    B02("B02", "Consumidor final"),
    B01("B01", "Crédito fiscal"),
}

@Serializable
data class DraftClient(
    val id: Int,
    val name: String,
    val phone: String? = null,
    val identify: String? = null,
)

/**
 * Una línea del borrador. **Precio y descuento en moneda base**, como `warehouse.Price1`.
 * `stock`, `infinite` y `unique` son una foto de la existencia al agregarla: sirven para
 * avisar, no para bloquear (decisión 2026-09-06: se vende aunque no haya).
 */
@Serializable
data class DraftLine(
    val key: String,
    val idWarehouse: Int?,
    val idProduct: Int?,
    val barcode: String?,
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val discount: Double = 0.0,
    val stock: Double? = null,
    val infinite: Boolean = false,
    val unique: Boolean = false,
) {
    val tracksStock: Boolean get() = idWarehouse != null && !infinite && !unique

    /** Se pide más de lo que hay. Solo aviso. */
    val exceedsStock: Boolean get() = tracksStock && stock != null && quantity > stock

    fun gross(): Double = round2(quantity * unitPrice - discount)

    fun totals(taxType: TaxType, rate: Double) = lineTotals(quantity, unitPrice, discount, taxType, rate)
}

/** Cobro recibido, en moneda base. */
@Serializable
data class DraftPayment(
    val cash: Double = 0.0,
    val transfer: Double = 0.0,
    val card: Double = 0.0,
) {
    val total: Double get() = round2(cash + transfer + card)
}

@Serializable
data class DraftAccount(
    val id: Int,
    val name: String,
    /** Solo para enseñar el logo del banco. Con valor por defecto: los borradores ya guardados lo leen igual. */
    val bankName: String? = null,
    val type: String? = null,
    /** Para reconocerla en "Dónde pagar". */
    val accountNumber: String? = null,
)

/**
 * "Dónde pagar": cuentas en las que el cliente puede transferir y unas instrucciones libres.
 * Salen en el PDF de la factura (`Sales.PaymentAccounts` / `PaymentNote`, API `F4`). La
 * siguiente factura empieza con lo último que se eligió.
 */
@Serializable
data class PayTo(
    val accounts: List<DraftAccount> = emptyList(),
    val note: String = "",
) {
    val isEmpty: Boolean get() = accounts.isEmpty() && note.isBlank()

    /** `Sales.PaymentAccounts`: ids separados por coma, en el orden elegido. */
    val accountIds: String? get() = accounts.joinToString(",") { it.id.toString() }.ifEmpty { null }

    fun toggle(account: DraftAccount): PayTo =
        if (accounts.any { it.id == account.id }) copy(accounts = accounts.filterNot { it.id == account.id })
        else copy(accounts = accounts + account)

    companion object {
        /** `Sales.PaymentNote` es TEXT, pero una nota de pago larga no se lee en el PDF. */
        const val MAX_NOTE = 400
    }
}

/**
 * El borrador vive en el teléfono (Room) y se guarda en cada cambio: si la app muere a media
 * factura, al volver está todo. Los totales NO se guardan: se derivan. La tasa de impuesto
 * y la de cambio SÍ, porque son entradas del cálculo (guía 04).
 */
@Serializable
data class InvoiceDraft(
    val kind: DocumentKind,
    val client: DraftClient? = null,
    val lines: List<DraftLine> = emptyList(),
    val taxType: TaxType = TaxType.WithTax,
    /** Tasa EFECTIVA de este documento (0.18 = 18 %). Nace de la tienda y se puede cambiar. */
    val taxRate: Double,
    val currency: Currency = Currency.DOP,
    /** Unidades de moneda base por 1 de [currency]. DOP → 1. */
    val exchangeRate: Double = 1.0,
    val payment: DraftPayment = DraftPayment(),
    val account: DraftAccount? = null,
    val withNcf: Boolean = false,
    val ncfType: NcfType = NcfType.B02,
    val rnc: String = "",
    /** Días para el vencimiento de lo que queda debiendo. `null` = sin fecha. */
    val dueInDays: Int? = null,
    val payTo: PayTo = PayTo(),
    val updatedAt: Long = 0,
) {
    val isQuote: Boolean get() = kind == DocumentKind.Quote

    val totals: DocumentTotals
        get() = documentTotals(lines.map { LineInput(it.quantity, it.unitPrice, it.discount) }, taxType, taxRate)

    /** Lo cobrado cuenta solo en facturas: una cotización no mueve dinero. */
    val paid: Double get() = if (isQuote) 0.0 else payment.total

    /** > 0: falta; < 0: sobra (devuelta). */
    val balance: Double get() = round2(totals.total - paid)

    val missing: Double get() = balance.coerceAtLeast(0.0)

    /**
     * Devuelta. Solo sale del efectivo: con transferencia o tarjeta no se devuelve dinero.
     * Nunca negativa (el cierre de caja del POS la suma, `services/ventas.js`).
     */
    val change: Double get() = if (isQuote) 0.0 else round2((paid - totals.total).coerceIn(0.0, payment.cash))

    /** Estatus con el que nace en `sales` (vocabulario del POS). */
    val status: String
        get() = when {
            isQuote -> SaleStatus.QUOTE
            missing > 0.0 -> SaleStatus.PENDING
            else -> SaleStatus.COMPLETE
        }

    val isOnCredit: Boolean get() = status == SaleStatus.PENDING

    /**
     * "Dónde pagar" solo tiene sentido en lo que se va a pagar después: una cotización o una
     * factura que queda debiendo. Una pagada no lo manda (el PDF tampoco lo pintaría).
     */
    val asksForPayment: Boolean get() = isQuote || isOnCredit

    /** Cobro de más que no se puede devolver (transferencia o tarjeta por encima del total). */
    val overpaidWithoutCash: Boolean
        get() = !isQuote && paid - totals.total - change > 0.004

    val canSave: Boolean
        get() = lines.isNotEmpty() && totals.total > 0.0 && exchangeRate > 0.0 && !overpaidWithoutCash
}

/** Un documento guardado en el teléfono que todavía no llegó al servidor. */
data class PendingDocument(
    val localId: String,
    val kind: DocumentKind,
    val clientName: String,
    val total: Double,
    val createdAt: Long,
    val failed: Boolean,
    val sending: Boolean,
    val error: String?,
    /** Estatus con el que nacerá en `sales`. */
    val status: String,
)
