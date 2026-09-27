package com.paybille.invoicer.feature.summary.data

import com.paybille.invoicer.core.database.Cached
import com.paybille.invoicer.core.database.PayloadCache
import com.paybille.invoicer.core.format.DateRange
import com.paybille.invoicer.core.network.LenientDoubleSerializer
import com.paybille.invoicer.core.network.LenientIntSerializer
import com.paybille.invoicer.core.network.PayBilleApi
import com.paybille.invoicer.core.network.decodeApi
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Totales de un rango (`repositories/dashboard.js → getTotals`). Todo es de ventas `Complete`. */
@Serializable
data class DashboardTotalsDto(
    @Serializable(LenientDoubleSerializer::class) val totalVendido: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalGastos: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalImpuestos: Double? = null,
    @Serializable(LenientIntSerializer::class) val cantidadTransacciones: Int? = null,
    @Serializable(LenientDoubleSerializer::class) val totalEfectivo: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalDeposito: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val totalCredito: Double? = null,
)

/** Un día de la serie. El servidor solo manda los días con ventas. */
@Serializable
data class SeriesPointDto(
    val time: String = "",
    @Serializable(LenientDoubleSerializer::class) val value: Double? = null,
)

@Serializable
data class TopProductDto(
    val name: String? = null,
    @Serializable(LenientDoubleSerializer::class) val qty: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val total: Double? = null,
)

/**
 * `POST dashboard/summary` (el dashboard del POS, `pages/index.vue`). `activeTornings` se
 * ignora: los turnos de caja no entran en esta app.
 */
@Serializable
data class DashboardSummaryDto(
    val today: DashboardTotalsDto = DashboardTotalsDto(),
    val range: DashboardTotalsDto = DashboardTotalsDto(),
    val series: List<SeriesPointDto> = emptyList(),
    val topProducts: List<TopProductDto> = emptyList(),
)

class SummaryRemoteDataSource(private val api: PayBilleApi) {

    /** Sin fechas el servidor usa hoy; aquí se mandan siempre (las calcula el teléfono en la zona del negocio). */
    suspend fun summary(idMarket: Int, range: DateRange): DashboardSummaryDto = decodeApi(
        DashboardSummaryDto.serializer(),
        api.get(
            "dashboard",
            buildJsonObject {
                put("startDate", range.start)
                put("endDate", range.end)
                // El controlador saca la tienda de `params` (`controllers/dashboard.js → getIdMarket`).
                putJsonObject("params") { put("IdMarket", idMarket) }
            },
            route = "summary",
        ),
    )
}

class SummaryRepository(
    private val remote: SummaryRemoteDataSource,
    private val cache: PayloadCache,
) {
    fun observe(idMarket: Int, range: DateRange): Flow<Cached<DashboardSummaryDto>?> =
        cache.observe(idMarket, key(range), DashboardSummaryDto.serializer())

    /** @throws com.paybille.invoicer.core.network.ApiException sin red o si el servidor falla. */
    suspend fun refresh(idMarket: Int, range: DateRange) {
        cache.put(idMarket, key(range), DashboardSummaryDto.serializer(), remote.summary(idMarket, range))
    }

    private fun key(range: DateRange) = "dashboard:${range.key}"
}

/**
 * La serie con **todos** los días del rango (los que no vendieron, en 0): sin esto, una semana
 * con ventas solo el lunes y el domingo dibujaría una línea recta entre los dos.
 */
fun fillDays(range: DateRange, series: List<SeriesPointDto>): List<Double> {
    val start = runCatching { LocalDate.parse(range.start) }.getOrNull() ?: return series.map { it.value ?: 0.0 }
    val end = runCatching { LocalDate.parse(range.end) }.getOrNull() ?: return series.map { it.value ?: 0.0 }
    val byDay = series.groupBy { it.time.take(10) }.mapValues { (_, points) -> points.sumOf { it.value ?: 0.0 } }
    val days = mutableListOf<Double>()
    var day = start
    while (day <= end && days.size < MAX_DAYS) {
        days += byDay[day.toString()] ?: 0.0
        day = day.plus(DatePeriod(days = 1))
    }
    return days
}

private const val MAX_DAYS = 400
