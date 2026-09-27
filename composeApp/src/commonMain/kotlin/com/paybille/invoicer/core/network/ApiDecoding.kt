package com.paybille.invoicer.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** `data` del sobre → DTO. Si no encaja, [ApiException] `Unexpected` (nunca una excepción suelta). */
fun <T> decodeApi(serializer: KSerializer<T>, data: JsonElement): T = try {
    PayBilleJson.decodeFromJsonElement(serializer, data)
} catch (e: SerializationException) {
    throw ApiException("El servidor respondió algo inesperado.", ApiException.Kind.Unexpected, cause = e)
} catch (e: IllegalArgumentException) {
    throw ApiException("El servidor respondió algo inesperado.", ApiException.Kind.Unexpected, cause = e)
}

/**
 * Lista del `data`. Los reportes del POS responden `{ message: "No se encontraron datos" }`
 * en vez de `[]` cuando no hay nada (`controllers/reports.js`): eso es una lista vacía.
 * `{ rows }` sin sobre (un POST sin `isGet`) también se acepta.
 */
fun <T> decodeApiList(serializer: KSerializer<T>, data: JsonElement): List<T> = when (data) {
    is JsonArray -> decodeApi(ListSerializer(serializer), data)
    is JsonObject -> (data["rows"] as? JsonArray)?.let { decodeApi(ListSerializer(serializer), it) } ?: emptyList()
    else -> emptyList()
}
