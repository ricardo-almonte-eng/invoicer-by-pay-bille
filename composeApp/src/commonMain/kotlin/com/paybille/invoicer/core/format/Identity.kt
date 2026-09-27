package com.paybille.invoicer.core.format

import kotlin.random.Random

/** Solo los dígitos: así se guarda la cédula (el POS le quita los guiones antes de enviarla). */
fun digitsOnly(value: String): String = value.filter { it.isDigit() }

/**
 * Cédula dominicana para pantalla: `"00112345678"` → `"001-1234567-8"` (máscara
 * `000-0000000-0` de `CreateClient.vue`). Lo que no tiene 11 dígitos se enseña tal cual.
 */
fun formatCedula(value: String): String {
    val d = digitsOnly(value)
    return if (d.length == 11) "${d.take(3)}-${d.substring(3, 10)}-${d.last()}" else value
}

/** Código de barras de 12 dígitos al azar para un producto nuevo (`randomBarcode()` del POS). */
fun randomBarcode(random: Random = Random.Default): String = buildString { repeat(12) { append(random.nextInt(10)) } }
