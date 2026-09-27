@file:OptIn(ExperimentalSerializationApi::class) // encodeNull()

package com.paybille.invoicer.core.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/*
 * La API devuelve los DECIMAL de MySQL como string ("0.18", "25.00") y a veces los
 * booleanos como 0/1. Estos serializadores normalizan en el BORDE, una sola vez, para
 * que ninguna pantalla vuelva a hacer `Number()` a mano. Un `"abc"` se lee como null:
 * es preferible un dato ausente a una excepción que tire la sesión entera.
 */

private fun Decoder.primitiveOrNull(): JsonPrimitive? {
    val element = (this as? JsonDecoder)?.decodeJsonElement() ?: return null
    return (element as? JsonPrimitive)?.takeUnless { it is JsonNull }
}

object LenientDoubleSerializer : KSerializer<Double?> {
    override val descriptor = PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double? {
        val p = decoder.primitiveOrNull() ?: return null
        return p.doubleOrNull ?: p.content.trim().toDoubleOrNull()
    }

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

object LenientIntSerializer : KSerializer<Int?> {
    override val descriptor = PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int? {
        val p = decoder.primitiveOrNull() ?: return null
        return p.intOrNull ?: p.content.trim().toDoubleOrNull()?.toInt()
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}

/** `Torning` es STRING en la tabla pero hay filas viejas donde viaja como número. */
object LenientStringSerializer : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? = decoder.primitiveOrNull()?.content

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

object LenientBooleanSerializer : KSerializer<Boolean?> {
    override val descriptor = PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean? {
        val p = decoder.primitiveOrNull() ?: return null
        return p.booleanOrNull ?: when (p.content.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean?) {
        if (value == null) encoder.encodeNull() else encoder.encodeBoolean(value)
    }
}
