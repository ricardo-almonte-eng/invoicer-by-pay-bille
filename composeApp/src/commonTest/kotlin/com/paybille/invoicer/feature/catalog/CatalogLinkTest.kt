package com.paybille.invoicer.feature.catalog

import com.paybille.invoicer.feature.catalog.domain.CatalogLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogLinkTest {

    @Test
    fun elSlugEsElNombreEnMinusculasYSinEspaciosComoElPos() {
        assertEquals("tiendadeana", CatalogLink.slug("Tienda de  Ana"))
        assertEquals("https://paybille.com/catalogo/tiendadeana", CatalogLink.url("https://paybille.com/", "Tienda de Ana"))
    }

    @Test
    fun laEnieYLasTildesVanCodificadas() {
        assertEquals(
            "https://paybille.com/catalogo/cafe%C3%B1o-2",
            CatalogLink.url("https://paybille.com", "Cafeño-2"),
        )
    }

    @Test
    fun unNombreConPuntosNoTieneEnlace() {
        assertNull(CatalogLink.url("https://paybille.com", "Colmado J.R."))
        assertEquals(setOf('.'), CatalogLink.invalidCharacters("Colmado J.R."))
    }
}
