package com.paybille.invoicer.core.network

import androidx.compose.ui.graphics.ImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Descarga y decodifica imágenes por URL (la de un producto o el logo de la tienda), con una
 * caché en memoria de las últimas [MAX_ENTRIES]. Sin librería de imágenes: son pocas y
 * pequeñas, y así no se añade una dependencia por una sola función.
 *
 * Sin red devuelve `null` y la pantalla enseña su hueco: es un adorno, no un dato, así que no
 * se guarda en Room (regla offline first: los DATOS viven en Room).
 */
class RemoteImageLoader(private val client: HttpClient) {
    private val lock = Mutex()
    private val cache = LinkedHashMap<String, ImageBitmap>()

    suspend fun load(url: String): ImageBitmap? {
        lock.withLock { cache[url] }?.let { return it }
        val image = try {
            val response = client.get(url)
            if (!response.status.isSuccess()) return null
            response.readRawBytes().decodeToImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin red, URL rota o un formato que la plataforma no decodifica: sin imagen.
            return null
        }
        lock.withLock {
            cache.remove(url)
            cache[url] = image
            while (cache.size > MAX_ENTRIES) cache.remove(cache.keys.first())
        }
        return image
    }

    /**
     * Los bytes tal cual (sin decodificar ni guardar en memoria): para embeber el logo de la
     * tienda en la factura. `null` sin red o si el servidor no responde 2xx.
     */
    suspend fun bytes(url: String): ByteArray? = try {
        val response = client.get(url)
        if (response.status.isSuccess()) response.readRawBytes() else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val MAX_ENTRIES = 40
    }
}
