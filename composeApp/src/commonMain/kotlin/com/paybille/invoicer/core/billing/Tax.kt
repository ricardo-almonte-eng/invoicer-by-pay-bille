package com.paybille.invoicer.core.billing

/** `taxType` de `sales` y `markets`, con los valores exactos de la API. */
enum class TaxType(val apiValue: String, val label: String) {
    WithTax("with_tax", "Se suma al precio"),
    Included("included", "Incluido en el precio"),
    NoTax("no_tax", "Sin impuesto"),
    ;

    companion object {
        fun fromApi(value: String?): TaxType = entries.firstOrNull { it.apiValue == value } ?: WithTax
    }
}

data class LineTotals(val subtotal: Double, val tax: Double, val total: Double)

data class DocumentTotals(
    val subtotal: Double,
    val tax: Double,
    val discount: Double,
    val total: Double,
)

/**
 * Totales de UNA línea, en moneda base. **Único sitio de la app con aritmética de impuesto.**
 *
 * Son las fórmulas del backend (`services/accountDocuments.js → computeLine`) y del libro de
 * cuentas del POS (`stores/data/accountDocs.js → lineTotals`):
 *
 * | taxType   | subtotal        | impuesto            | total                |
 * |-----------|-----------------|---------------------|----------------------|
 * | with_tax  | bruto           | bruto × tasa        | bruto + impuesto     |
 * | included  | bruto/(1+tasa)  | bruto − subtotal    | bruto                |
 * | no_tax    | bruto           | 0                   | bruto                |
 *
 * con `bruto = cantidad × precio − descuento`, redondeado **por línea**.
 *
 * ⚠️ El carrito del POS (`stores/components/cart.js → recalcTotals`) calcula distinto:
 * en `included` usa `bruto × tasa` y en `with_tax` aplica el descuento después del
 * impuesto. Aquí se sigue al backend, que es quien guarda los documentos.
 */
fun lineTotals(quantity: Double, unitPrice: Double, discount: Double, taxType: TaxType, rate: Double): LineTotals {
    val effectiveRate = if (taxType == TaxType.NoTax) 0.0 else rate.coerceAtLeast(0.0)
    val gross = round2(quantity * unitPrice - discount)
    return when (taxType) {
        TaxType.NoTax -> LineTotals(subtotal = gross, tax = 0.0, total = gross)
        TaxType.Included -> {
            val sub = round2(gross / (1 + effectiveRate))
            LineTotals(subtotal = sub, tax = round2(gross - sub), total = gross)
        }
        TaxType.WithTax -> {
            val tax = round2(gross * effectiveRate)
            LineTotals(subtotal = gross, tax = tax, total = round2(gross + tax))
        }
    }
}

/** Suma de líneas ya redondeadas, como `draftTotals` del POS. */
fun documentTotals(lines: List<LineInput>, taxType: TaxType, rate: Double): DocumentTotals {
    var subtotal = 0.0
    var tax = 0.0
    var discount = 0.0
    var total = 0.0
    for (line in lines) {
        val t = lineTotals(line.quantity, line.unitPrice, line.discount, taxType, rate)
        subtotal = round2(subtotal + t.subtotal)
        tax = round2(tax + t.tax)
        discount = round2(discount + line.discount)
        total = round2(total + t.total)
    }
    return DocumentTotals(subtotal = subtotal, tax = tax, discount = discount, total = total)
}

data class LineInput(val quantity: Double, val unitPrice: Double, val discount: Double)

/** Rango aceptado para la tasa editable: 0 % a 100 % (guía 08 §1). */
fun isValidTaxRate(rate: Double): Boolean = rate in 0.0..1.0
