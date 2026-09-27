package com.paybille.invoicer.feature.store.data.remote

import com.paybille.invoicer.core.network.LenientDoubleSerializer
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.decodeApi
import com.paybille.invoicer.core.platform.PickedImage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Lo que se edita de `markets` desde el teléfono. Es lo que la factura PDF del servidor pinta
 * de la tienda (`services/ventas.js`: `Image`, `Name`, `Address`, `RNC`, `Phone`, `Mail`) más
 * el impuesto con el que nace cada factura. Lo demás de `configuracion/tienda.vue` (banco,
 * garantía, redes, parámetros, impresora, zona horaria, NCF) es del mostrador y se queda en el
 * POS (guía 00).
 */
@Serializable
data class StoreConfigDto(
    val id: Int,
    @SerialName("Name") val name: String = "",
    @SerialName("Address") val address: String? = null,
    @SerialName("Phone") val phone: String? = null,
    @SerialName("Mail") val mail: String? = null,
    @SerialName("RNC") val rnc: String? = null,
    @SerialName("Image") val image: String? = null,
    /** Decimal (`0.18` = 18 %), `DECIMAL(5,2)` que llega como string. */
    @Serializable(LenientDoubleSerializer::class) val taxValue: Double? = null,
    /** `ITBIS` | `IVA` (ENUM en la tabla). */
    val taxLabel: String? = null,
    /** `included` | `with_tax` | `no_tax` (ENUM en la tabla). */
    val taxType: String? = null,
)

class StoreRemoteDataSource(private val api: PayBilleApi) {
    suspend fun market(idMarket: Int): StoreConfigDto = decodeApi(StoreConfigDto.serializer(), api.getById("markets", idMarket))

    suspend fun uploadImage(image: PickedImage): String = api.uploadImage(image.bytes, image.mimeType, image.extension)

    /**
     * `PUT generic/markets/{id}`. El genérico hace `instance.update(data)`: lo que no viaja no se
     * toca (el POS manda la ficha entera porque la tiene toda).
     */
    suspend fun updateMarket(idMarket: Int, body: JsonObject) {
        api.put("markets", idMarket, body)
    }
}
