package com.paybille.invoicer.feature.document.data

import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.PayBilleJson
import com.paybille.invoicer.core.network.decodeApi
import com.paybille.invoicer.feature.document.domain.InvoiceConfig
import com.paybille.invoicer.feature.document.domain.InvoiceData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class InvoiceDocumentRemote(private val api: PayBilleApi) {

    /** `GET ventas/factura/{id}/data`: los datos de la factura, sin PDF. */
    suspend fun data(saleId: Int): InvoiceData = decodeApi(InvoiceData.serializer(), api.getBusiness("ventas/factura/$saleId/data"))

    /** `GET invoiceConfig/{IdMarket}`: la crea con los valores por defecto si la tienda no tiene. */
    suspend fun config(idMarket: Int): InvoiceConfig = decodeApi(InvoiceConfig.serializer(), api.getBusiness("invoiceConfig/$idMarket"))

    /** `POST invoiceConfig/{IdMarket}` (upsert). Responde la configuración guardada, sin sobre. */
    suspend fun saveConfig(idMarket: Int, config: InvoiceConfig): InvoiceConfig {
        val body: JsonObject = SaveJson.encodeToJsonElement(InvoiceConfig.serializer(), config).jsonObject
        return decodeApi(InvoiceConfig.serializer(), api.post("invoiceConfig", body, route = idMarket.toString()))
    }

    private companion object {
        /**
         * Al guardar viaja TODO, también lo que vale lo de por defecto y los `null`: con
         * [PayBilleJson] un interruptor que vuelve a `true` o una firma borrada no viajarían y el
         * servidor se quedaría con lo de antes.
         */
        val SaveJson = Json(PayBilleJson) {
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
