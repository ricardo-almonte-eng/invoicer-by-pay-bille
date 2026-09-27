package com.paybille.invoicer.core.billing

import kotlin.math.abs
import kotlin.math.floor

/**
 * Redondeo a céntimos idéntico al del backend (`services/accountDocuments.js:49`):
 * `Math.round((v + Number.EPSILON) * 100) / 100`. `Math.round` lleva el .5 hacia +∞;
 * `kotlin.math.round` lo lleva al par y descuadraría céntimos contra el POS.
 */
fun round2(value: Double): Double {
    if (value.isNaN() || value.isInfinite()) return 0.0
    return floor((value + EPSILON) * 100 + 0.5) / 100
}

private const val EPSILON = 2.220446049250313e-16 // Number.EPSILON

/** `true` si dos importes son iguales al céntimo. */
fun sameCents(a: Double, b: Double): Boolean = abs(round2(a) - round2(b)) < 0.005

/**
 * Moneda en la que se EMITE un documento. **A la API siempre van importes en moneda base**
 * (no hay columna de moneda en ninguna tabla): esta solo cambia lo que se ve y lo que se
 * imprime (guía 08 §11).
 */
enum class Currency(val code: String, val symbol: String, val label: String) {
    // `$` solo para la base: es lo que imprime el papel del POS.
    DOP("DOP", "$", "Pesos"),

    // Las extranjeras con su código: dos `$` distintos en la misma app confunden.
    USD("USD", "USD", "Dólares"),
    EUR("EUR", "EUR", "Euros"),
    ;

    val isBase: Boolean get() = this == DOP
}

/*
 * Las DOS únicas conversiones de la app. `rate` = unidades de moneda BASE por 1 de la moneda
 * del documento ("el dólar está a 60"). La dirección invertida no falla: produce un número
 * creíble y equivocado. Nadie más multiplica ni divide por la tasa.
 */

/** Base → moneda del documento: `1500 / 60 = 25`. */
fun toInvoiceCurrency(base: Double, rate: Double): Double =
    if (rate <= 0.0) base else round2(base / rate)

/** Moneda del documento → base: `25 × 60 = 1500`. */
fun toBaseCurrency(value: Double, rate: Double): Double =
    if (rate <= 0.0) value else round2(value * rate)
