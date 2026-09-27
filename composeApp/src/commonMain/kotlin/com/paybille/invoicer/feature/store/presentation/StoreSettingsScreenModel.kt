package com.paybille.invoicer.feature.store.presentation

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.paybille.invoicer.core.billing.TaxType
import com.paybille.invoicer.core.format.formatQuantity
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.platform.PickedImage
import com.paybille.invoicer.feature.auth.data.SessionRepository
import com.paybille.invoicer.feature.auth.data.signedIn
import com.paybille.invoicer.feature.auth.domain.DEFAULT_TAX_RATE
import com.paybille.invoicer.feature.store.data.StoreConfigForm
import com.paybille.invoicer.feature.store.data.StoreSettingsRepository
import com.paybille.invoicer.feature.store.data.remote.StoreConfigDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.round

/** `ITBIS` e `IVA`: los dos valores del ENUM `markets.taxLabel`. */
val TAX_LABELS = listOf("ITBIS", "IVA")

data class StoreFormState(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val mail: String = "",
    val rnc: String = "",
    val image: String? = null,
    /** Porcentaje como se escribe ("18"); a la API va en decimal (0.18). */
    val taxPercent: String = "",
    val taxLabel: String = "ITBIS",
    val taxType: TaxType = TaxType.WithTax,
)

data class StoreSettingsUiState(
    val loaded: Boolean = false,
    /** Sin red al abrir: se enseña lo guardado en el teléfono y no se deja guardar. */
    val offline: Boolean = false,
    val form: StoreFormState = StoreFormState(),
    val original: StoreFormState = StoreFormState(),
    val newLogo: PickedImage? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val nameError: String? = null,
    val addressError: String? = null,
    val taxError: String? = null,
    val done: Boolean = false,
) {
    val dirty: Boolean get() = form != original || newLogo != null
}

class StoreSettingsScreenModel(
    private val sessions: SessionRepository,
    private val repository: StoreSettingsRepository,
) : StateScreenModel<StoreSettingsUiState>(StoreSettingsUiState()) {

    private var uploaded: Pair<PickedImage, String>? = null

    init {
        load()
    }

    fun load() {
        screenModelScope.launch {
            val session = sessions.signedIn().first()
            // Primero lo guardado (se ve al instante); luego la ficha del servidor, que trae el
            // correo y lo que haya cambiado en el POS.
            val local = session.store?.let { store ->
                StoreFormState(
                    name = store.name,
                    address = store.address.orEmpty(),
                    phone = store.phone.orEmpty(),
                    rnc = store.rnc.orEmpty(),
                    image = store.image?.takeIf { it.isNotBlank() },
                    taxPercent = percentText(store.taxValue ?: DEFAULT_TAX_RATE),
                    taxLabel = store.taxLabel,
                    taxType = TaxType.fromApi(store.taxType),
                )
            } ?: StoreFormState()
            mutableState.update { it.copy(form = local, original = local) }
            try {
                val remote = repository.load(session.idMarket).toForm()
                mutableState.update { it.copy(loaded = true, offline = false, form = remote, original = remote) }
            } catch (e: ApiException) {
                mutableState.update {
                    it.copy(
                        loaded = false,
                        offline = e.isConnectivity,
                        error = if (e.isConnectivity) null else e.message ?: "No se pudo cargar la tienda.",
                    )
                }
            }
        }
    }

    fun update(change: (StoreFormState) -> StoreFormState) =
        mutableState.update { it.copy(form = change(it.form), error = null, nameError = null, addressError = null, taxError = null) }

    fun pickLogo(image: PickedImage) = mutableState.update { it.copy(newLogo = image, error = null) }

    fun removeLogo() = mutableState.update { it.copy(newLogo = null, form = it.form.copy(image = null)) }

    fun logoFailed(message: String) = mutableState.update { it.copy(error = message) }

    fun save() {
        val current = state.value
        if (current.saving || !current.loaded) return
        val f = current.form
        val percent = f.taxPercent.trim().replace(',', '.').toDoubleOrNull()
        when {
            f.name.isBlank() -> return mutableState.update { it.copy(nameError = "Escribe el nombre de la tienda.") }
            // `markets.Address` no admite nulo: el POS tampoco deja guardar sin ella.
            f.address.isBlank() -> return mutableState.update { it.copy(addressError = "Escribe la dirección: sale en la factura.") }
            f.taxType != TaxType.NoTax && (percent == null || percent < 0 || percent > MAX_PERCENT) ->
                return mutableState.update { it.copy(taxError = "Escribe un porcentaje entre 0 y $MAX_PERCENT.") }
        }
        // Con "Sin impuesto" la tasa se guarda igual: si vuelve a cobrar impuesto, sigue ahí.
        val rate = (percent ?: 0.0) / 100
        val newLogo = current.newLogo
        mutableState.update { it.copy(saving = true, error = null) }
        screenModelScope.launch {
            try {
                val session = sessions.signedIn().first()
                val logo = when {
                    newLogo == null -> f.image
                    uploaded?.first === newLogo -> uploaded?.second
                    else -> repository.uploadLogo(newLogo).also { uploaded = newLogo to it }
                }
                repository.save(
                    session.idMarket,
                    StoreConfigForm(
                        name = f.name,
                        address = f.address,
                        phone = f.phone,
                        mail = f.mail,
                        rnc = f.rnc,
                        image = logo,
                        taxRate = rate,
                        taxLabel = f.taxLabel,
                        taxType = f.taxType.apiValue,
                    ),
                )
                mutableState.update { it.copy(saving = false, done = true) }
            } catch (e: ApiException) {
                val message = if (e.isConnectivity) "Sin conexión. Guardar la tienda necesita internet." else e.message ?: "No se pudo guardar la tienda."
                mutableState.update { it.copy(saving = false, error = message) }
            }
        }
    }

    private fun StoreConfigDto.toForm() = StoreFormState(
        name = name.trim(),
        address = address.orEmpty(),
        phone = phone.orEmpty(),
        mail = mail.orEmpty(),
        rnc = rnc.orEmpty(),
        image = image?.takeIf { it.isNotBlank() },
        taxPercent = percentText(taxValue ?: DEFAULT_TAX_RATE),
        taxLabel = taxLabel?.takeIf { it in TAX_LABELS } ?: "ITBIS",
        taxType = TaxType.fromApi(taxType),
    )

    private companion object {
        const val MAX_PERCENT = 100
    }
}

/** 0.18 → "18"; 0.075 → "7.5". */
private fun percentText(rate: Double): String = formatQuantity(round(rate * 10_000) / 100)
