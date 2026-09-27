package com.paybille.invoicer.feature.sales.domain

/**
 * Estatus de `sales.Status`, con el vocabulario del POS (guía 03). Lo que no se reconoce se
 * conserva en [Other] para enseñarlo tal cual, como hace `pages/cuentas/facturas.vue`.
 */
sealed interface SaleStatus {
    data object Paid : SaleStatus
    data object Pending : SaleStatus
    data object Quote : SaleStatus
    data object Cancelled : SaleStatus
    data class Other(val raw: String) : SaleStatus

    companion object {
        const val COMPLETE = "Complete"
        const val PENDING = "Pagos Pendientes"
        const val QUOTE = "Cotizacion"

        // El POS escribe las dos grafías: se filtra por ambas.
        const val CANCELLED = "Cancelada"
        const val CANCELLED_ALT = "Cancelado"

        fun from(raw: String): SaleStatus = when (raw) {
            COMPLETE -> Paid
            PENDING -> Pending
            QUOTE -> Quote
            CANCELLED, CANCELLED_ALT -> Cancelled
            else -> Other(raw)
        }
    }
}

/**
 * Pestañas del Inicio. Los estatus internos del POS (`En Proceso`, `Suspendida…`,
 * `Esperando Facturacion`) no salen en ninguna: son ventas a medio hacer en el mostrador.
 */
enum class SalesFilter(val statuses: List<String>) {
    All(
        listOf(
            SaleStatus.COMPLETE,
            SaleStatus.PENDING,
            SaleStatus.QUOTE,
            SaleStatus.CANCELLED,
            SaleStatus.CANCELLED_ALT,
        ),
    ),
    Sales(listOf(SaleStatus.COMPLETE, SaleStatus.PENDING)),
    Quotes(listOf(SaleStatus.QUOTE)),
}

/** Lo que enseña una fila de la lista: tres datos y un estatus. */
data class SaleSummary(
    val id: Int,
    /** Número de factura que ve el usuario. Cae al `id` si la venta no tiene secuencia. */
    val number: String,
    val clientName: String,
    val status: SaleStatus,
    /** Siempre en moneda base (no hay columna de moneda en la API). */
    val total: Double,
    val createdAt: Long?,
    /** `sales.Date` tal cual: solo se muestra si falta `createdAt`. */
    val displayDate: String?,
    val ncf: String?,
)
