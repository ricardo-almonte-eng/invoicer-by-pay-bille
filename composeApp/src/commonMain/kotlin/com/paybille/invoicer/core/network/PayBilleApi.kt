package com.paybille.invoicer.core.network

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Réplica de `useFetchStore` del POS, con los mismos nombres de método para que el código
 * del POS se traduzca leyéndolo. **Es el único sitio que sabe que existe un servidor.**
 *
 * Diferencia deliberada con el POS: allí `post()` atrapa el error y devuelve un string.
 * Aquí todo método devuelve el `data` del sobre o lanza [ApiException]. Siempre.
 *
 * Toda respuesta de la API pasa por `formatResponseMiddleware`, que la envuelve en
 * `{ data, meta }`. Los métodos devuelven ese `data` ya desenvuelto.
 */
class PayBilleApi(
    private val client: HttpClient,
    private val config: ApiConfig,
) {
    val apiKey: String get() = config.apiKey

    /** `POST {BASE}/{model}/{route}`. */
    suspend fun post(model: String, body: JsonObject, route: String? = null): JsonElement =
        call {
            client.post(businessUrl(model, route)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    /** `POST {BASE}/{model}/{route}?page&pageSize` con `isGet: true`, como `get()` del POS. */
    suspend fun get(
        model: String,
        body: JsonObject,
        route: String? = null,
        page: Int = 1,
        pageSize: Int = 10,
    ): JsonElement = call {
        client.post(businessUrl(model, route)) {
            parameter("page", page)
            parameter("pageSize", pageSize)
            contentType(ContentType.Application.Json)
            setBody(body.withIsGet())
        }
    }

    /**
     * `POST {GENERIC}/{get|like}/{model}` filtrando por `params`.
     *
     * `token` solo se pasa cuando todavía no hay sesión guardada (la cascada del login);
     * si no, el token lo pone el cliente HTTP.
     */
    suspend fun getGeneric(
        model: String,
        params: JsonObject,
        like: Boolean = false,
        page: Int = 1,
        pageSize: Int = 10,
        token: String? = null,
    ): JsonElement = genericRequest(model, params, like, page, pageSize, token).data

    /**
     * Como [getGeneric], pero con la paginación. Filtros útiles de `params` (la API los pasa
     * tal cual a Sequelize, `repositories/generic.js`):
     * - `"Gasto": null` → `Gasto IS NULL`.
     * - un arreglo → `IN (…)`.
     * - sufijos `__gte` / `__lte` / `__between` → rangos.
     *
     * El orden es siempre `id DESC`: no se puede elegir.
     */
    suspend fun getGenericPage(
        model: String,
        params: JsonObject,
        page: Int,
        pageSize: Int,
        like: Boolean = false,
        extra: JsonObject = JsonObject(emptyMap()),
    ): ApiPage {
        return genericRequest(model, params, like, page, pageSize, token = null, extra = extra).toPage(page)
    }

    private fun Envelope.toPage(page: Int) = ApiPage(
        items = data as? JsonArray ?: JsonArray(emptyList()),
        page = page,
        hasNextPage = (meta?.get("hasNextPage") as? JsonPrimitive)?.booleanOrNull ?: false,
        totalCount = (meta?.get("totalCount") as? JsonPrimitive)?.intOrNull,
    )

    private suspend fun genericRequest(
        model: String,
        params: JsonObject,
        like: Boolean,
        page: Int,
        pageSize: Int,
        token: String?,
        extra: JsonObject = JsonObject(emptyMap()),
    ): Envelope = request {
        client.post("${config.genericUrl}/${if (like) "like" else "get"}/$model") {
            authorization(token)
            parameter("page", page)
            parameter("pageSize", pageSize)
            contentType(ContentType.Application.Json)
            setBody(JsonObject(extra + ("params" to params)).withIsGet())
        }
    }

    /**
     * Como [get], pero devuelve la página completa (`productinventory/sales` y demás
     * endpoints de negocio que responden `{ rows, count }`).
     */
    suspend fun getPage(model: String, body: JsonObject, route: String, page: Int, pageSize: Int): ApiPage {
        val envelope = request {
            client.post(businessUrl(model, route)) {
                parameter("page", page)
                parameter("pageSize", pageSize)
                contentType(ContentType.Application.Json)
                setBody(body.withIsGet())
            }
        }
        return envelope.toPage(page)
    }

    /** `PUT {GENERIC}/{model}/{id}`. */
    suspend fun put(model: String, id: Int, body: JsonObject): JsonElement = call {
        client.put("${config.genericUrl}/$model/$id") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /** `POST {GENERIC}/{model}` → el registro creado, con su `id`. */
    suspend fun postGeneric(model: String, body: JsonObject): JsonElement = call {
        client.post("${config.genericUrl}/$model") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /** `GET {BASE}/{path}` (endpoints de negocio por GET, p. ej. `accountdocs/{id}`). */
    suspend fun getBusiness(path: String): JsonElement = call { client.get("${config.baseUrl}/$path") }

    /**
     * `POST {BASE}/image/upload` (multipart, campo `image`) → la URL pública de la imagen, como
     * `postImage()` del POS. El servidor la guarda en disco (multer, 5 MB como mucho, solo
     * jpeg/png/gif/webp/avif) y responde `{ message, url }` SIN sobre: `data` solo lo añade a
     * los GET y a los POST con `isGet`. La URL se guarda luego en `products.Image` /
     * `markets.Image`, que son `STRING`.
     *
     * @param extension sin punto (`jpg`, `png`): multer toma la extensión del nombre del archivo.
     */
    suspend fun uploadImage(bytes: ByteArray, mimeType: String, extension: String): String {
        val data = call {
            client.submitFormWithBinaryData(
                url = "${config.baseUrl}/image/upload",
                formData = formData {
                    append(
                        key = "image",
                        value = bytes,
                        headers = Headers.build {
                            append(HttpHeaders.ContentType, mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=\"imagen.$extension\"")
                        },
                    )
                },
            )
        }
        return ((data as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw ApiException("El servidor no devolvió la dirección de la imagen.", ApiException.Kind.Unexpected)
    }

    /** `GET {GENERIC}/{model}/{id}`. `token`: ver [getGeneric]. */
    suspend fun getById(model: String, id: Int, token: String? = null): JsonElement =
        call { client.get("${config.genericUrl}/$model/$id") { authorization(token) } }

    // Sin "Bearer ": la API espera el token crudo.
    private fun HttpRequestBuilder.authorization(token: String?) {
        if (!token.isNullOrBlank()) headers[HttpHeaders.Authorization] = token
    }

    private fun businessUrl(model: String, route: String?): String =
        if (route.isNullOrBlank()) "${config.baseUrl}/$model" else "${config.baseUrl}/$model/$route"

    private fun JsonObject.withIsGet() = JsonObject(this + ("isGet" to JsonPrimitive(true)))

    private suspend fun call(send: suspend () -> HttpResponse): JsonElement = request(send).data

    /** El sobre `{ data, meta }` de `formatResponseMiddleware`. */
    private data class Envelope(val data: JsonElement, val meta: JsonObject?)

    private suspend fun request(sendRequest: suspend () -> HttpResponse): Envelope {
        val response = send(sendRequest)
        return parseEnvelope(response)
    }

    /** Envía y traduce los fallos de red a [ApiException]. */
    private suspend fun send(sendRequest: suspend () -> HttpResponse): HttpResponse =
        try {
            sendRequest()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            throw timeout(e)
        } catch (e: ConnectTimeoutException) {
            throw timeout(e)
        } catch (e: SocketTimeoutException) {
            throw timeout(e)
        } catch (e: Exception) {
            // IOException, UnresolvedAddressException, NSURLError… cada motor lanza la
            // suya. Cualquier fallo antes de tener respuesta es "sin conexión".
            throw ApiException("No se pudo conectar al servidor.", ApiException.Kind.Network, cause = e)
        }

    private suspend fun parseEnvelope(response: HttpResponse): Envelope {
        val text = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Se perdió la conexión con el servidor.", ApiException.Kind.Network, cause = e)
        }

        val root: JsonElement = if (text.isBlank()) {
            JsonNull
        } else {
            try {
                PayBilleJson.parseToJsonElement(text)
            } catch (e: SerializationException) {
                // Algunos endpoints responden texto plano con `res.send("…")`, que no pasa por
                // el sobre (p. ej. los errores de `nfc/*`). Se entrega como string y quien
                // llama decide si es un error.
                if (response.status.isSuccess()) {
                    JsonPrimitive(text)
                } else {
                    throw ApiException(
                        text.take(MAX_PLAIN_ERROR).ifBlank { "El servidor respondió algo inesperado." },
                        ApiException.Kind.Server,
                        response.status.value,
                        e,
                    )
                }
            }
        }
        val data = (root as? JsonObject)?.get("data") ?: root
        val meta = (root as? JsonObject)?.get("meta") as? JsonObject

        if (!response.status.isSuccess()) {
            throw ApiException(
                message = messageOf(data) ?: messageOf(root) ?: "Error del servidor (${response.status.value}).",
                kind = ApiException.Kind.Server,
                status = response.status.value,
            )
        }
        return Envelope(data, meta)
    }

    private companion object {
        const val MAX_PLAIN_ERROR = 200
    }

    private fun timeout(cause: Throwable) =
        ApiException("Tiempo de espera agotado. Revisa tu conexión.", ApiException.Kind.Timeout, cause = cause)

    private fun messageOf(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull?.takeIf { element.isString && it.isNotBlank() }
        is JsonObject -> listOf("error", "message")
            .firstNotNullOfOrNull { key -> (element[key] as? JsonPrimitive)?.contentOrNull }
        else -> null
    }
}

/** Una página de la API genérica. `items` sin decodificar: cada fuente lo lleva a su DTO. */
data class ApiPage(
    val items: JsonArray,
    val page: Int,
    val hasNextPage: Boolean,
    val totalCount: Int?,
)
