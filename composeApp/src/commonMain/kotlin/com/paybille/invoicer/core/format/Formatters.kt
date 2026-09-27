package com.paybille.invoicer.core.format

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Zona del negocio si la tienda no dice otra. República Dominicana no tiene horario de verano. */
const val DEFAULT_TIME_ZONE = "America/Santo_Domingo"

/**
 * Tasa decimal → porcentaje para pantalla: `0.18` → `"18 %"`, `0.125` → `"12.5 %"`.
 * El espacio antes del signo es el mismo que el POS escribe en `ITBIS 18 %`.
 */
fun formatPercent(rate: Double): String {
    val tenths = (rate * 1000).roundToLong() // porcentaje × 10, redondeado
    val whole = tenths / 10
    val decimal = abs(tenths % 10)
    return if (decimal == 0L) "$whole %" else "$whole.$decimal %"
}

/**
 * Importe en moneda base: `1250.0` → `"$ 1,250.00"`. Es exactamente lo que imprime el POS
 * (`utils/receipt.js → formatCash(v, '$')`), espacio incluido.
 *
 * Se formatea a mano y no con el formateador de la plataforma: con `es-DO`, Android e iOS
 * devuelven `US$`/`RD$` y separadores distintos entre sí.
 */
fun formatMoney(value: Double, symbol: String = "$"): String {
    // Céntimos con redondeo half-up, como `round2` del backend.
    val cents = floor(abs(value) * 100 + 0.5 + 1e-9).toLong()
    val units = (cents / 100).toString()
    val grouped = units.reversed().chunked(3).joinToString(",").reversed()
    val decimals = (cents % 100).toString().padStart(2, '0')
    val sign = if (value < 0 && cents != 0L) "-" else ""
    return "$sign$symbol $grouped.$decimals"
}

/** Cantidad para pantalla: `3.0` → `"3"`, `2.5` → `"2.5"` (hasta dos decimales, sin redondear hacia arriba). */
fun formatQuantity(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else ((value * 100).toLong() / 100.0).toString()

private val SHORT_MONTHS = listOf("ene.", "feb.", "mar.", "abr.", "may.", "jun.", "jul.", "ago.", "sep.", "oct.", "nov.", "dic.")

/** Fecha corta para listas: `"5 may. 2026"`, en la zona del negocio. */
@OptIn(ExperimentalTime::class)
fun formatShortDate(epochMillis: Long, timeZone: String = DEFAULT_TIME_ZONE): String {
    val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zoneOf(timeZone)).date
    return "${date.day} ${SHORT_MONTHS[date.month.number - 1]} ${date.year}"
}

/** Fecha `YYYY-MM-DD` (vencimientos) → `"20 may. 2026"`; tal cual si no se entiende. */
fun formatIsoDate(iso: String): String {
    val date = runCatching { kotlinx.datetime.LocalDate.parse(iso.take(10)) }.getOrNull() ?: return iso
    return "${date.day} ${SHORT_MONTHS[date.month.number - 1]} ${date.year}"
}

/** `createdAt` de la API (`"2026-05-05T14:22:00.000Z"`) → epoch ms, o null si no se entiende. */
@OptIn(ExperimentalTime::class)
fun parseApiTimestamp(value: String?): Long? =
    value?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

/**
 * `sales.Date`: `"05/05/2026 2:07 pm"`. NO es una fecha: es texto de presentación que el POS
 * arma a mano (`completeOrder.vue → date()`), con la hora sin cero a la izquierda y los
 * minutos con él. Se replica tal cual para que el POS lo muestre igual.
 */
@OptIn(ExperimentalTime::class)
fun formatPosDate(epochMillis: Long, timeZone: String = DEFAULT_TIME_ZONE): String {
    val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zoneOf(timeZone))
    val hour12 = (t.hour % 12).let { if (it == 0) 12 else it }
    val ampm = if (t.hour >= 12) "pm" else "am"
    return "${pad2(t.day)}/${pad2(t.month.number)}/${t.year} $hour12:${pad2(t.minute)} $ampm"
}

/** `YYYY-MM-DD` en la zona del negocio, `days` días después de `epochMillis`. */
@OptIn(ExperimentalTime::class)
fun formatIsoDatePlusDays(epochMillis: Long, days: Int, timeZone: String = DEFAULT_TIME_ZONE): String {
    val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zoneOf(timeZone)).date
        .plus(days, DateTimeUnit.DAY)
    return "${date.year}-${pad2(date.month.number)}-${pad2(date.day)}"
}

private fun zoneOf(timeZone: String): TimeZone =
    runCatching { TimeZone.of(timeZone) }.getOrElse { TimeZone.of(DEFAULT_TIME_ZONE) }

private fun pad2(value: Int) = value.toString().padStart(2, '0')
