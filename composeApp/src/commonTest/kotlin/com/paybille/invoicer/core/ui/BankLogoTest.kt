package com.paybille.invoicer.core.ui

import com.paybille.invoicer.feature.banks.data.BANKS
import com.paybille.invoicer.resources.Res
import com.paybille.invoicer.resources.bank_popular
import com.paybille.invoicer.resources.bank_promerica
import com.paybille.invoicer.resources.bank_santacruz
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BankLogoTest {

    @Test
    fun todoBancoDeLaListaDelPosTieneLogo() {
        BANKS.forEach { assertNotNull(bankLogo(it), "Sin logo: $it") }
    }

    @Test
    fun proamericaUsaElArchivoPromerica() {
        assertEquals(Res.drawable.bank_promerica, bankLogo("Proamérica"))
    }

    @Test
    fun toleraLoQueSeEscribeAManoEnElPos() {
        assertEquals(Res.drawable.bank_popular, bankLogo("Banco Popular"))
        assertEquals(Res.drawable.bank_santacruz, bankLogo("santa cruz"))
        assertEquals(Res.drawable.bank_promerica, bankLogo("PROAMERICA"))
        assertNull(bankLogo("Banco Imaginario"))
        assertNull(bankLogo(null))
    }
}
