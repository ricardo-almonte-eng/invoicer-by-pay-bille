package com.paybille.invoicer.feature.catalog.domain

/**
 * Enlace del catálogo público de la tienda: `https://paybille.com/catalogo/<slug>`. Lo abre la
 * web del POS (`pages/catalogo/[storeName].vue`), que pide a la API `catalog/token` con ese slug.
 */
object CatalogLink {

    /**
     * El nombre de la tienda en minúsculas y sin espacios, igual que el POS (`AppBar.vue →
     * generateCatalogLink`) y la API (`services/catalog.js → getMarketBySlug`).
     */
    fun slug(storeName: String): String = storeName.lowercase().filterNot { it.isWhitespace() }

    /**
     * La API solo acepta letras ASCII, las vocales con tilde, `ü`, `ñ`, dígitos, `-` y `_`
     * (`controllers/catalog.js`). Con otro carácter (un punto, un `&`) el catálogo no abre.
     */
    fun isValidSlug(slug: String): Boolean = slug.isNotEmpty() && slug.all { it in ALLOWED }

    /** El enlace para compartir, o `null` si el nombre de la tienda no sirve de slug. */
    fun url(webBaseUrl: String, storeName: String): String? {
        val slug = slug(storeName)
        if (!isValidSlug(slug)) return null
        return "${webBaseUrl.trimEnd('/')}/catalogo/${encodePathSegment(slug)}"
    }

    /** Los caracteres que sobran del nombre, para decirle al usuario qué cambiar. */
    fun invalidCharacters(storeName: String): Set<Char> = slug(storeName).filterNot { it in ALLOWED }.toSet()

    /** `ñ` y las tildes van en UTF-8 con `%`: WhatsApp no enlaza bien una URL con `ñ` tal cual. */
    private fun encodePathSegment(text: String): String = buildString {
        text.encodeToByteArray().forEach { byte ->
            val c = byte.toInt().toChar()
            if (byte >= 0 && (c.isAsciiLetterOrDigit() || c == '-' || c == '_')) {
                append(c)
            } else {
                append('%')
                append(HEX[(byte.toInt() shr 4) and 0x0F])
                append(HEX[byte.toInt() and 0x0F])
            }
        }
    }

    private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    private const val HEX = "0123456789ABCDEF"

    private val ALLOWED: Set<Char> = (('a'..'z') + ('0'..'9') + "-_áéíóúüñ".toList()).toSet()
}
