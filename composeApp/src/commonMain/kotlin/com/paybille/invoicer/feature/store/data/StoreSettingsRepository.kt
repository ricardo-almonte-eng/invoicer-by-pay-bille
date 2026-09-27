package com.paybille.invoicer.feature.store.data

import com.paybille.invoicer.core.platform.PickedImage
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.store.data.remote.StoreConfigDto
import com.paybille.invoicer.feature.store.data.remote.StoreRemoteDataSource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class StoreConfigForm(
    val name: String,
    val address: String,
    val phone: String,
    val mail: String,
    val rnc: String,
    /** URL ya subida, o `null` sin logo. */
    val image: String?,
    val taxRate: Double,
    val taxLabel: String,
    val taxType: String,
)

/**
 * Configuración de la tienda. Leer y guardar necesitan red (como en el POS, que además recarga
 * la página): al guardar se refresca el perfil para que la sesión en Room (nombre, logo,
 * impuesto por defecto) quede al día.
 */
class StoreSettingsRepository(
    private val remote: StoreRemoteDataSource,
    private val sessions: SessionRepository,
) {
    suspend fun load(idMarket: Int): StoreConfigDto = remote.market(idMarket)

    suspend fun uploadLogo(image: PickedImage): String = remote.uploadImage(image)

    /** Solo estos campos; el resto de la ficha de la tienda no se toca. */
    suspend fun save(idMarket: Int, form: StoreConfigForm) {
        remote.updateMarket(idMarket, form.toMarketBody())
        // Mejor esfuerzo: si falla, lo guardado en el servidor ya es correcto y el perfil se
        // pone al día la próxima vez que se abra.
        sessions.refreshProfile()
    }
}

/** Solo estos campos viajan: el `PUT` genérico no toca los que no vienen. */
internal fun StoreConfigForm.toMarketBody() = buildJsonObject {
    put("Name", name.trim())
    put("Address", address.trim())
    put("Phone", phone.trim())
    put("Mail", mail.trim())
    put("RNC", rnc.trim())
    put("Image", image)
    put("taxValue", taxRate)
    put("taxLabel", taxLabel)
    put("taxType", taxType)
}
