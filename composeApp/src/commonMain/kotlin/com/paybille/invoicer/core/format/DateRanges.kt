package com.paybille.invoicer.core.format

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Rango de días `YYYY-MM-DD`, los dos inclusive, en la zona del negocio. Es lo que piden
 * `dashboard/summary` y los reportes (`startDate`/`endDate`): el servidor completa las horas
 * (`00:00:00` a `23:59:59`).
 */
data class DateRange(val start: String, val end: String) {
    /** Clave para guardar el resultado de ese rango en `cached_payloads`. */
    val key: String get() = "$start:$end"
}

/**
 * Periodos del Resumen y de los reportes, los mismos del dashboard del POS (`pages/index.vue`):
 * Hoy · Ayer · 7 días (hoy y los 6 anteriores) · Mes (del día 1 a hoy).
 */
enum class Period(val label: String) {
    Today("Hoy"),
    Yesterday("Ayer"),
    Week("7 días"),
    Month("Mes"),
    ;

    fun range(today: LocalDate): DateRange = when (this) {
        Today -> DateRange(today.toString(), today.toString())
        Yesterday -> today.minus(DatePeriod(days = 1)).let { DateRange(it.toString(), it.toString()) }
        Week -> DateRange(today.minus(DatePeriod(days = 6)).toString(), today.toString())
        Month -> DateRange(LocalDate(today.year, today.month, 1).toString(), today.toString())
    }
}

/** Los últimos `days` días, hoy incluido: `lastDays(30)` = hoy y los 29 anteriores. */
fun lastDays(today: LocalDate, days: Int): DateRange =
    DateRange(today.minus(DatePeriod(days = days - 1)).toString(), today.toString())

/** `[inicio del primer día, inicio del día siguiente al último)` en epoch ms, en la zona del negocio. */
fun DateRange.epochBounds(timeZone: String): Pair<Long, Long> {
    val tz = runCatching { TimeZone.of(timeZone) }.getOrElse { TimeZone.of(DEFAULT_TIME_ZONE) }
    val from = LocalDate.parse(start).atStartOfDayIn(tz).toEpochMilliseconds()
    val to = LocalDate.parse(end).plus(DatePeriod(days = 1)).atStartOfDayIn(tz).toEpochMilliseconds()
    return from to to
}

/** Hoy en la zona del negocio. */
fun todayIn(timeZone: String, now: Long = Clock.System.now().toEpochMilliseconds()): LocalDate =
    kotlin.time.Instant.fromEpochMilliseconds(now)
        .toLocalDateTime(runCatching { TimeZone.of(timeZone) }.getOrElse { TimeZone.of(DEFAULT_TIME_ZONE) })
        .date

/** `"2026-09-05"` → `"5 sep."` (sin año: para rangos cortos en pantalla). */
fun formatDayMonth(iso: String): String {
    val full = formatIsoDate(iso)
    return if (full == iso) iso else full.substringBeforeLast(' ')
}

/** Rango para pantalla: `"5 sep. – 18 sep."`, o la fecha completa si es un solo día. */
fun DateRange.label(): String = if (start == end) formatIsoDate(start) else "${formatDayMonth(start)} – ${formatDayMonth(end)}"
