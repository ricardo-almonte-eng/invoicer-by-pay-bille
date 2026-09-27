package com.paybille.invoicer.core.format

import com.paybille.invoicer.feature.reports.data.SoldProductDto
import com.paybille.invoicer.feature.reports.data.mergeByProduct
import com.paybille.invoicer.feature.reports.presentation.ReportPeriod
import com.paybille.invoicer.feature.summary.data.SeriesPointDto
import com.paybille.invoicer.feature.summary.data.fillDays
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DateRangesTest {

    private val today = LocalDate(2026, 3, 1)

    @Test
    fun periodosDelResumenComoElPos() {
        assertEquals(DateRange("2026-03-01", "2026-03-01"), Period.Today.range(today))
        // "Ayer" cruza el mes (y febrero de 2026 tiene 28 días).
        assertEquals(DateRange("2026-02-28", "2026-02-28"), Period.Yesterday.range(today))
        // 7 días = hoy y los 6 anteriores.
        assertEquals(DateRange("2026-02-23", "2026-03-01"), Period.Week.range(today))
        assertEquals(DateRange("2026-03-01", "2026-03-01"), Period.Month.range(today))
        assertEquals(DateRange("2026-09-01", "2026-09-18"), Period.Month.range(LocalDate(2026, 9, 18)))
    }

    @Test
    fun mesAnteriorCruzaElAno() {
        assertEquals(DateRange("2025-12-01", "2025-12-31"), ReportPeriod.PreviousMonth.range(LocalDate(2026, 1, 15)))
        assertEquals(DateRange("2026-02-01", "2026-02-28"), ReportPeriod.PreviousMonth.range(today))
    }

    @Test
    fun ultimosDiasIncluyenHoy() {
        assertEquals(DateRange("2026-08-20", "2026-09-18"), lastDays(LocalDate(2026, 9, 18), 30))
    }

    @Test
    fun limitesDelRangoEnLaZonaDelNegocio() {
        // Santo Domingo es UTC-4 todo el año: el día empieza a las 04:00 UTC.
        val (from, to) = DateRange("2026-09-18", "2026-09-18").epochBounds(DEFAULT_TIME_ZONE)
        assertEquals(kotlin.time.Instant.parse("2026-09-18T04:00:00Z").toEpochMilliseconds(), from)
        assertEquals(kotlin.time.Instant.parse("2026-09-19T04:00:00Z").toEpochMilliseconds(), to)
    }

    @Test
    fun hoySeCalculaEnLaZonaDelNegocio() {
        // 02:00 UTC del 19 = 22:00 del 18 en Santo Domingo.
        val now = kotlin.time.Instant.parse("2026-09-19T02:00:00Z").toEpochMilliseconds()
        assertEquals(LocalDate(2026, 9, 18), todayIn(DEFAULT_TIME_ZONE, now))
    }

    @Test
    fun laSerieRellenaLosDiasSinVentas() {
        val series = listOf(SeriesPointDto("2026-09-14", 100.0), SeriesPointDto("2026-09-16", 50.0))
        assertEquals(listOf(100.0, 0.0, 50.0, 0.0), fillDays(DateRange("2026-09-14", "2026-09-17"), series))
    }

    @Test
    fun cedulaConMascaraYSinGuionesAlGuardar() {
        assertEquals("001-1234567-8", formatCedula("00112345678"))
        assertEquals("001-1234567-8", formatCedula("001-1234567-8"))
        assertEquals("AB123", formatCedula("AB123"))
        assertEquals("00112345678", digitsOnly("001-1234567-8"))
    }

    @Test
    fun codigoDeBarrasDeDoceDigitos() {
        val code = randomBarcode(Random(7))
        assertEquals(12, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun productosVendidosSeSumanPorProducto() {
        val merged = mergeByProduct(
            listOf(
                SoldProductDto(idProduct = 1, name = "Cola", totalAmount = 2.0, totalPrice = 100.0, profit = 40.0),
                SoldProductDto(idProduct = 2, name = "Pan", totalAmount = 5.0, totalPrice = 50.0, profit = 10.0),
                // La misma Cola, en una venta pendiente: el servidor la separa por estatus.
                SoldProductDto(idProduct = 1, name = "Cola", totalAmount = 4.0, totalPrice = 200.0, profit = 80.0),
            ),
        )
        assertEquals(listOf("Cola", "Pan"), merged.map { it.name })
        assertEquals(6.0, merged.first().totalAmount)
        assertEquals(300.0, merged.first().totalPrice)
        assertEquals(120.0, merged.first().profit)
    }
}
