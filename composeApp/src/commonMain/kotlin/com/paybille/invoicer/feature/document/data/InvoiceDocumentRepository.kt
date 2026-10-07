package com.paybille.invoicer.feature.document.data

import com.paybille.invoicer.core.database.Cached
import com.paybille.invoicer.core.database.PayloadCache
import com.paybille.invoicer.core.network.RemoteImageLoader
import com.paybille.invoicer.feature.document.domain.InvoiceAssets
import com.paybille.invoicer.feature.document.domain.InvoiceConfig
import com.paybille.invoicer.feature.document.domain.InvoiceData
import com.paybille.invoicer.feature.document.domain.InvoiceHtml
import com.paybille.invoicer.resources.Res
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Logo de la tienda ya descargado, para que la factura lo lleve también sin red. */
@Serializable
data class CachedLogo(val url: String, val dataUri: String)

/**
 * La factura que genera la app (offline first): los datos de `ventas/factura/{id}/data` y la
 * configuración de la tienda se guardan en `cached_payloads`; la pantalla los lee de ahí y el
 * HTML se arma en el teléfono en milisegundos. La red solo refresca.
 */
class InvoiceDocumentRepository(
    private val remote: InvoiceDocumentRemote,
    private val cache: PayloadCache,
    private val images: RemoteImageLoader,
) {
    private val assetsLock = Mutex()
    private var template: String? = null
    private var staticAssets: InvoiceAssets? = null

    fun observe(idMarket: Int, saleId: Int): Flow<Cached<InvoiceData>?> =
        cache.observe(idMarket, dataKey(saleId), InvoiceData.serializer())

    fun observeConfig(idMarket: Int): Flow<InvoiceConfig?> =
        cache.observe(idMarket, CONFIG_KEY, InvoiceConfig.serializer()).map { it?.value }

    /** Trae los datos de la factura y los guarda. La configuración viene con ellos. */
    suspend fun refresh(idMarket: Int, saleId: Int) {
        val data = remote.data(saleId)
        cache.put(idMarket, dataKey(saleId), InvoiceData.serializer(), data.copy(config = null))
        data.config?.let { cache.put(idMarket, CONFIG_KEY, InvoiceConfig.serializer(), it) }
        data.market.logo?.let { cacheLogo(idMarket, it) }
    }

    suspend fun refreshConfig(idMarket: Int): InvoiceConfig {
        val config = remote.config(idMarket)
        cache.put(idMarket, CONFIG_KEY, InvoiceConfig.serializer(), config)
        return config
    }

    suspend fun saveConfig(idMarket: Int, config: InvoiceConfig): InvoiceConfig {
        val saved = remote.saveConfig(idMarket, config)
        cache.put(idMarket, CONFIG_KEY, InvoiceConfig.serializer(), saved)
        return saved
    }

    /** El HTML final. `config` null = la guardada (o la de por defecto). */
    suspend fun html(
        idMarket: Int,
        data: InvoiceData,
        config: InvoiceConfig?,
        timeZone: String,
        todayIso: String?,
    ): String {
        val effective = config ?: observeConfig(idMarket).first() ?: InvoiceConfig()
        val logo = data.market.logo?.let { url ->
            cache.observe(idMarket, LOGO_KEY, CachedLogo.serializer()).first()?.value?.takeIf { it.url == url }?.dataUri
        }
        val assets = staticAssets().copy(logoDataUri = logo)
        return InvoiceHtml.render(template(), data, effective, assets, timeZone, todayIso)
    }

    private suspend fun cacheLogo(idMarket: Int, url: String) {
        if (!url.startsWith("http")) return
        val cached = cache.observe(idMarket, LOGO_KEY, CachedLogo.serializer()).first()?.value
        if (cached?.url == url) return
        // Hay logos guardados como http://: Android no deja salir a http, así que primero https.
        val bytes = (if (url.startsWith("http://")) images.bytes("https://" + url.removePrefix("http://")) else null)
            ?: images.bytes(url) ?: return
        val mime = imageMime(bytes) ?: return
        cache.put(idMarket, LOGO_KEY, CachedLogo.serializer(), CachedLogo(url, dataUri(mime, bytes)))
    }

    private suspend fun template(): String = assetsLock.withLock {
        template ?: Res.readBytes(TEMPLATE_PATH).decodeToString().also { template = it }
    }

    /**
     * La tipografía de PayBille embebida (`@font-face` en `data:`) y el isotipo del pie: la
     * factura se ve con la marca también en el PDF y sin red. Se leen una vez.
     */
    private suspend fun staticAssets(): InvoiceAssets = assetsLock.withLock {
        staticAssets ?: run {
            val css = StringBuilder()
            for ((file, weight) in FONTS) {
                val font = dataUri("font/ttf", Res.readBytes("font/$file"))
                css.append("@font-face { font-family: 'PB Sans'; font-weight: $weight; font-style: normal; src: url('$font') format('truetype'); }\n")
            }
            val mark = runCatching { dataUri("image/png", Res.readBytes("drawable/paybille_isotipo.png")) }.getOrNull()
            InvoiceAssets(fontCss = css.toString(), brandMark = mark).also { staticAssets = it }
        }
    }

    companion object {
        const val CONFIG_KEY = "invoice-config"
        const val LOGO_KEY = "invoice-logo"
        const val TEMPLATE_PATH = "files/invoice_template.html"

        fun dataKey(saleId: Int) = "invoice-doc:$saleId"

        private val FONTS = listOf(
            "google_sans_flex_regular.ttf" to 400,
            "google_sans_flex_medium.ttf" to 500,
            "google_sans_flex_semibold.ttf" to 600,
            "google_sans_flex_bold.ttf" to 700,
        )

        @OptIn(ExperimentalEncodingApi::class)
        fun dataUri(mime: String, bytes: ByteArray) = "data:$mime;base64,${Base64.Default.encode(bytes)}"

        /** Por la firma de los bytes: la URL del logo no siempre trae extensión. */
        fun imageMime(bytes: ByteArray): String? {
            fun at(i: Int) = bytes.getOrNull(i)?.toInt()?.and(0xFF)
            return when {
                at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "image/png"
                at(0) == 0xFF && at(1) == 0xD8 -> "image/jpeg"
                at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 -> "image/gif"
                at(0) == 0x52 && at(1) == 0x49 && at(8) == 0x57 && at(9) == 0x45 -> "image/webp"
                else -> null
            }
        }
    }
}
