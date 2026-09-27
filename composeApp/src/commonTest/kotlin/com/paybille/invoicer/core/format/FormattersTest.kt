package com.paybille.invoicer.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormattersTest {
    @Test
    fun porcentajeEnteroSinDecimales() {
        assertEquals("18 %", formatPercent(0.18))
        assertEquals("0 %", formatPercent(0.0))
        assertEquals("16 %", formatPercent(0.16))
    }

    @Test
    fun porcentajeConUnDecimal() {
        assertEquals("12.5 %", formatPercent(0.125))
    }

    @Test
    fun dineroComoElPapelDelPos() {
        assertEquals("$ 1,250.00", formatMoney(1250.0))
        assertEquals("$ 0.00", formatMoney(0.0))
        assertEquals("$ 1.00", formatMoney(1.0))
        assertEquals("$ 1,234,567.89", formatMoney(1234567.89))
        assertEquals("-$ 600.50", formatMoney(-600.5))
    }

    @Test
    fun dineroRedondeaMitadHaciaArribaComoElBackend() {
        assertEquals("$ 0.13", formatMoney(0.125))
        assertEquals("$ 2.68", formatMoney(2.675)) // en binario es 2.67499…: el épsilon lo corrige
    }

    @Test
    fun fechaCortaEnLaZonaDelNegocio() {
        // 2026-05-06 02:30 UTC = 5 de mayo, 22:30 en Santo Domingo (UTC-4).
        val instant = parseApiTimestamp("2026-05-06T02:30:00.000Z")!!
        assertEquals("5 may. 2026", formatShortDate(instant))
        assertEquals("6 may. 2026", formatShortDate(instant, "UTC"))
    }

    @Test
    fun fechaIlegibleEsNull() {
        assertNull(parseApiTimestamp("05/05/2026 10:30 am"))
        assertNull(parseApiTimestamp(null))
    }
}
