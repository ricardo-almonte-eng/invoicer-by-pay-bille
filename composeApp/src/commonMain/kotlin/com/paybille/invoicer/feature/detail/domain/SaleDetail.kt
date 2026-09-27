package com.paybille.invoicer.feature.detail.domain

import kotlinx.serialization.Serializable

/**
 * Detalle de una venta tal como se guarda en el teléfono (offline first): cabecera, líneas y,
 * si queda saldo, su documento en el libro de cuentas (abonos y vencimiento).
 * Todos los importes en moneda base.
 */
@Serializable
data class SaleDetail(
    val id: Int,
    val idMarket: Int,
    /** Número que ve el usuario: la secuencia, o el id si no tiene. */
    val number: String,
    val clientName: String,
    val idClient: Int? = null,
    /** `sales.Status` tal cual (vocabulario del POS). */
    val status: String,
    /** `sales.Date`: texto de presentación, NO una fecha. */
    val dateText: String? = null,
    val createdAt: Long? = null,
    val subtotal: Double,
    val tax: Double,
    val discount: Double,
    val total: Double,
    val cash: Double = 0.0,
    val transfer: Double = 0.0,
    val card: Double = 0.0,
    val change: Double = 0.0,
    val ncf: String? = null,
    val rnc: String? = null,
    val taxType: String? = null,
    val lines: List<DetailLine> = emptyList(),
    val receivable: Receivable? = null,
    val syncedAt: Long = 0,
)

@Serializable
data class DetailLine(
    val name: String,
    val quantity: Double,
    val price: Double,
    val discount: Double,
    val tax: Double,
    /** `salesProducts.Total`: bruto de la línea (cantidad × precio − descuento). */
    val total: Double,
)

/**
 * Cuenta por cobrar (`AccountDocuments`) de una venta con saldo. `Paid` y `Balance` los
 * escribe SOLO el servidor: aquí se leen, nunca se calculan (guía 08 §3).
 */
@Serializable
data class Receivable(
    val docId: Int,
    val total: Double,
    val paid: Double,
    val balance: Double,
    /** `YYYY-MM-DD` o null (sin vencimiento). */
    val dueDate: String? = null,
    /** Próximo vencimiento: la cuota más antigua sin pagar, o `dueDate`. */
    val nextDueDate: String? = null,
    /** `Pendiente` · `Parcial` · `Pagado` · `Anulado`. */
    val status: String,
    val lateFeeOutstanding: Double = 0.0,
    val payments: List<ReceivablePayment> = emptyList(),
    val installments: List<Installment> = emptyList(),
) {
    val isOpen: Boolean get() = balance > 0.004 && status != "Anulado"
    val effectiveDueDate: String? get() = nextDueDate ?: dueDate
}

@Serializable
data class ReceivablePayment(
    val id: Int,
    /** ISO (aquí SÍ es una fecha real). */
    val date: String?,
    val method: String,
    val amount: Double,
    val lateFee: Double = 0.0,
    val reference: String? = null,
    val voided: Boolean = false,
)

@Serializable
data class Installment(
    val number: Int,
    val dueDate: String,
    val total: Double,
    val balance: Double,
    val status: String,
)

/**
 * Métodos de abono. El valor es el de la API y **importa**: al abonar, el backend suma el
 * pago a la venta según el método (`services/accountDocuments.js → CAMPO_VENTA`), y solo
 * reconoce `Efectivo` y `Deposito`; cualquier otro, `Transferencia` incluida, lo suma a
 * `MoneyCredit` (la columna de TARJETA). Por eso la transferencia se manda como `Deposito`.
 */
enum class PaymentMethod(val apiValue: String, val label: String) {
    Cash("Efectivo", "Efectivo"),
    Transfer("Deposito", "Transferencia o depósito"),
    Card("Tarjeta", "Tarjeta"),
}

/** Estado del vencimiento, para pintarlo. */
sealed interface DueState {
    data object NoDate : DueState
    data class InDays(val days: Int) : DueState
    data object Today : DueState
    data class Overdue(val days: Int) : DueState
}

fun dueState(dueDateIso: String?, todayIso: String): DueState {
    if (dueDateIso == null) return DueState.NoDate
    val days = daysBetween(todayIso, dueDateIso) ?: return DueState.NoDate
    return when {
        days > 0 -> DueState.InDays(days)
        days == 0 -> DueState.Today
        else -> DueState.Overdue(-days)
    }
}

/** Cuántos días antes del vencimiento una factura pasa a "Por vencer". */
const val DUE_SOON_DAYS = 3

/** Cuánto pide atención una factura con saldo, para la franja y la etiqueta de la lista. */
enum class DueUrgency { None, Soon, Overdue }

/**
 * Vencida si ya pasó la fecha; "por vencer" si vence hoy o en [DUE_SOON_DAYS] días o menos
 * (los mismos días en que suenan los avisos: el día antes y el día). Sin fecha, nada.
 */
fun DueState.urgency(): DueUrgency = when (this) {
    is DueState.Overdue -> DueUrgency.Overdue
    DueState.Today -> DueUrgency.Soon
    is DueState.InDays -> if (days <= DUE_SOON_DAYS) DueUrgency.Soon else DueUrgency.None
    DueState.NoDate -> DueUrgency.None
}

/** Días de `fromIso` a `toIso` (fechas `YYYY-MM-DD`), o null si alguna no se entiende. */
fun daysBetween(fromIso: String, toIso: String): Int? {
    val from = runCatching { kotlinx.datetime.LocalDate.parse(fromIso.take(10)) }.getOrNull() ?: return null
    val to = runCatching { kotlinx.datetime.LocalDate.parse(toIso.take(10)) }.getOrNull() ?: return null
    return (to.toEpochDays() - from.toEpochDays()).toInt()
}
