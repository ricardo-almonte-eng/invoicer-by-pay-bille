package com.paybille.invoicer.feature.invoice.data

import com.paybille.invoicer.core.billing.round2
import com.paybille.invoicer.core.format.DEFAULT_TIME_ZONE
import com.paybille.invoicer.core.format.formatIsoDatePlusDays
import com.paybille.invoicer.core.format.formatPosDate
import com.paybille.invoicer.core.network.ApiException
import com.paybille.invoicer.core.network.PayBilleJson
import com.paybille.invoicer.feature.auth.domain.Session
import com.paybille.invoicer.feature.invoice.data.local.InvoiceDao
import com.paybille.invoicer.feature.invoice.data.local.OutboxEntity
import com.paybille.invoicer.feature.invoice.data.local.OutboxState
import com.paybille.invoicer.feature.invoice.data.remote.InvoiceRemoteDataSource
import com.paybille.invoicer.feature.invoice.domain.InvoiceDraft
import com.paybille.invoicer.feature.sales.data.SalesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Qué pasos del envío ya se hicieron. Se guarda tras CADA paso: si la red o la app se caen
 * a mitad, el reintento continúa donde quedó.
 */
@Serializable
data class SendProgress(
    val sequence: String? = null,
    val ncfChecked: Boolean = false,
    val ncf: String? = null,
    /** Se intentó crear la cabecera: si no hay `saleId`, pudo llegar sin que llegara la respuesta. */
    val saleAttempted: Boolean = false,
    val saleId: Int? = null,
    /** Id de cada línea creada, en el orden del borrador. */
    val lineIds: List<Int> = emptyList(),
    /** Cuántas líneas ya descontaron inventario. */
    val inventoryDone: Int = 0,
    val accountDocDone: Boolean = false,
    val movementDone: Boolean = false,
)

/** Resultado de un intento de vaciar la cola. */
data class SendReport(val sent: Int, val offline: Boolean, val failed: Int)

/**
 * Vacía la cola de envíos. Replica `completeOrder.vue → saveSold()` y
 * `CreateCotizacion.vue → saveCotizacion()` del POS, paso a paso:
 *
 * 1. Secuencia (solo facturas).
 * 2. NCF: verificar rango y consumir uno (solo si se pidió). **Lo más tarde posible**: un NCF
 *    consumido no se devuelve.
 * 3. Cabecera en `sales`.
 * 4. Líneas en `salesProducts`.
 * 5. Por línea (solo facturas): descontar `warehouse`, rastro en `reportInventory`, garantía.
 * 6. Si queda saldo: documento espejo en `accountdocs` (idempotente).
 * 7. Si hay cuenta: movimiento de ingreso en `cuentas`.
 * 8. PDF en el servidor.
 *
 * Los pasos 1–5 son obligatorios: si fallan, el documento se queda en la cola. Del 6 en
 * adelante son "mejor esfuerzo", como en el POS: la venta ya existe y el backend tiene
 * su propia red de seguridad para el espejo.
 */
class InvoiceSender(
    private val dao: InvoiceDao,
    private val remote: InvoiceRemoteDataSource,
    private val sales: SalesRepository,
    /** Sesión abierta, o `null` si no hay (entonces no se envía nada). */
    private val currentSession: suspend () -> Session?,
    /** Tras enviar: traer los datos de la factura, poner al día los vencimientos… Nunca hace fallar el envío. */
    private val afterSent: suspend (saleId: Int, onCredit: Boolean) -> Unit = { _, _ -> },
) {
    private val running = Mutex()

    private val sentIds = MutableStateFlow<Map<String, Int>>(emptyMap())

    /**
     * Id del servidor de cada documento enviado en esta ejecución, por su id local. Así la
     * pantalla que se abrió al guardar sabe qué factura cargar cuando llega.
     */
    val sent: StateFlow<Map<String, Int>> = sentIds.asStateFlow()

    // Vive lo que la app: un envío no se corta porque el usuario cierre el editor.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Lanza [sendPending] en segundo plano y vuelve enseguida. */
    fun trigger(includeFailed: Boolean = false) {
        scope.launch { sendPending(includeFailed) }
    }

    /**
     * Intenta enviar todo lo pendiente. Si ya hay un envío en curso, no hace nada: el que
     * está corriendo recogerá lo nuevo en su siguiente vuelta.
     */
    suspend fun sendPending(includeFailed: Boolean = false): SendReport {
        if (!running.tryLock()) return SendReport(0, offline = false, failed = 0)
        try {
            dao.resetInterrupted()
            val session = currentSession() ?: return SendReport(0, offline = false, failed = 0)
            var sent = 0
            var failed = 0
            // Se relee la cola en cada vuelta: puede entrar algo nuevo mientras se envía.
            while (true) {
                val next = dao.outbox().firstOrNull {
                    it.idMarket == session.idMarket &&
                        (it.state == OutboxState.PENDING || (includeFailed && it.state == OutboxState.FAILED))
                } ?: break
                when (sendOne(next, session)) {
                    Outcome.Sent -> sent++
                    Outcome.Failed -> failed++
                    Outcome.Offline -> return SendReport(sent, offline = true, failed = failed)
                }
                if (includeFailed && failed > 0) break
            }
            return SendReport(sent, offline = false, failed = failed)
        } finally {
            running.unlock()
        }
    }

    private enum class Outcome { Sent, Failed, Offline }

    private suspend fun sendOne(item: OutboxEntity, session: Session): Outcome {
        val draft = InvoiceRepository.decodeDraft(item.draftJson)
        var row = item.copy(state = OutboxState.SENDING, attempts = item.attempts + 1)
        dao.upsertOutbox(row)
        var progress = PayBilleJson.decodeFromString(SendProgress.serializer(), row.progress)

        suspend fun checkpoint(next: SendProgress) {
            progress = next
            row = row.copy(progress = PayBilleJson.encodeToString(SendProgress.serializer(), next))
            dao.upsertOutbox(row)
        }

        return try {
            val saleId = sendRequired(draft, row, session, progress) { checkpoint(it) }
            sendBestEffort(draft, row, session, progress) { checkpoint(it) }
            sales.saveSent(saleId, session.idMarket, draft, progress.sequence, progress.ncf, row.createdAt, row.clientName)
            dao.deleteOutbox(row.localId)
            sentIds.update { it + (row.localId to saleId) }
            runCatching { afterSent(saleId, draft.isOnCredit) }
            Outcome.Sent
        } catch (e: CancellationException) {
            dao.upsertOutbox(row.copy(state = OutboxState.PENDING))
            throw e
        } catch (e: ApiException) {
            if (e.isConnectivity) {
                dao.upsertOutbox(row.copy(state = OutboxState.PENDING, lastError = null))
                Outcome.Offline
            } else {
                dao.upsertOutbox(row.copy(state = OutboxState.FAILED, lastError = e.message))
                Outcome.Failed
            }
        }
    }

    /** Pasos 1–5. Devuelve el id de la venta. */
    private suspend fun sendRequired(
        draft: InvoiceDraft,
        row: OutboxEntity,
        session: Session,
        start: SendProgress,
        checkpoint: suspend (SendProgress) -> Unit,
    ): Int {
        var p = start
        val idMarket = session.idMarket
        // Hubo un intento anterior que llegó (o pudo llegar) a crear la cabecera.
        val resumed = start.saleAttempted

        // 1. Secuencia. Las cotizaciones del POS no llevan.
        if (!draft.isQuote && p.sequence == null) {
            p = p.copy(sequence = remote.nextSequence(idMarket))
            checkpoint(p)
        }

        // 2. NCF.
        if (!draft.isQuote && draft.withNcf && p.ncf == null) {
            if (!p.ncfChecked) {
                remote.verifyNcf(idMarket, draft.ncfType.code)
                p = p.copy(ncfChecked = true)
                checkpoint(p)
            }
            p = p.copy(ncf = remote.nextNcf(idMarket, draft.ncfType.code))
            checkpoint(p)
        }

        // 3. Cabecera. Si un intento anterior pudo haberla creado, se busca por su secuencia
        //    antes de crear otra.
        if (p.saleId == null) {
            val sequence = p.sequence
            val existing = if (p.saleAttempted && sequence != null) {
                remote.findSaleIdBySequence(idMarket, sequence)
            } else {
                null
            }
            if (existing == null) {
                p = p.copy(saleAttempted = true)
                checkpoint(p)
            }
            val saleId = existing ?: remote.createSale(saleBody(draft, row, session, p))
            p = p.copy(saleId = saleId)
            checkpoint(p)
        }
        val saleId = p.saleId!!

        // 4. Líneas. Al reanudar, manda el servidor: se crean en orden, así que las N que ya
        //    existen son las N primeras (incluida una que llegó sin que llegara su respuesta).
        if (resumed && p.inventoryDone == 0) {
            val existingLines = remote.saleLines(saleId).map { it.id }.take(draft.lines.size)
            if (existingLines != p.lineIds) {
                p = p.copy(lineIds = existingLines)
                checkpoint(p)
            }
        }
        while (p.lineIds.size < draft.lines.size) {
            val index = p.lineIds.size
            val id = remote.createSaleLine(lineBody(draft, index, saleId, session))
            p = p.copy(lineIds = p.lineIds + id)
            checkpoint(p)
        }

        // 5. Inventario. Solo facturas: una cotización no toca existencias.
        if (!draft.isQuote) {
            while (p.inventoryDone < draft.lines.size) {
                val index = p.inventoryDone
                discountInventory(draft, index, saleId, p.lineIds[index], row, session)
                p = p.copy(inventoryDone = index + 1)
                checkpoint(p)
            }
        }
        return saleId
    }

    /** Pasos 6–8: si fallan, la venta ya existe y se da por enviada. */
    private suspend fun sendBestEffort(
        draft: InvoiceDraft,
        row: OutboxEntity,
        session: Session,
        start: SendProgress,
        checkpoint: suspend (SendProgress) -> Unit,
    ) {
        var p = start
        val saleId = p.saleId ?: return
        val timeZone = session.store?.timeZone ?: DEFAULT_TIME_ZONE

        if (draft.isOnCredit && !p.accountDocDone) {
            val due = draft.dueInDays?.let { formatIsoDatePlusDays(row.createdAt, it, timeZone) }
            if (bestEffort { remote.accountDocFromSale(saleId, due) }) {
                p = p.copy(accountDocDone = true)
                checkpoint(p)
            }
        }

        val account = draft.account
        // Lo que de verdad entró: lo cobrado menos la devuelta. (El POS suma la devuelta y
        // cuenta de más.) En una venta a crédito, el adelanto entra aquí; el backend lo marca
        // como abono de apertura sin crear otro movimiento.
        val received = round2(draft.paid - draft.change)
        if (!draft.isQuote && account != null && received > 0.0 && !p.movementDone) {
            val body = buildJsonObject {
                put("IdCuenta", account.id)
                put("Type", "Ingreso")
                put("Amount", received)
                put(
                    "Description",
                    if (draft.client != null) "Venta de productos - ${row.clientName}" else "Venta de productos",
                )
                put("Reference", p.sequence)
                put("ReferenceType", "Venta")
                put("ReferenceId", saleId)
                put("IdMarket", session.idMarket)
                put("IdUser", session.userId)
                put("Username", session.displayName)
            }
            if (bestEffort { remote.createAccountMovement(body) }) {
                p = p.copy(movementDone = true)
                checkpoint(p)
            }
        }

        // El PDF lo descarga `afterSent` (GET ventas/factura/{id}, que además lo genera).
    }

    private suspend fun discountInventory(
        draft: InvoiceDraft,
        index: Int,
        saleId: Int,
        lineId: Int,
        row: OutboxEntity,
        session: Session,
    ) {
        val line = draft.lines[index]
        val idWarehouse = line.idWarehouse ?: return
        val warehouse = remote.warehouse(idWarehouse)
        val unique = warehouse.unique == true
        val infinite = warehouse.infinityAmount == true
        val before = warehouse.amount ?: 0.0
        // Se permite quedar en negativo (decisión 2026-09-06): un -3 es la señal de que faltan
        // tres unidades por registrar.
        val after = if (infinite) before else round2(before - line.quantity)

        remote.updateWarehouse(
            idWarehouse,
            buildJsonObject {
                put("Amount", after)
                if (unique) {
                    put("sold", true)
                    put("IdSale", saleId)
                    put("NameClient", row.clientName)
                }
            },
        )

        val user = session.displayName
        remote.createInventoryReport(
            buildJsonObject {
                put("IdSale", saleId)
                put("IdProduct", line.idProduct)
                put("IdWarehouse", idWarehouse)
                put("Name", line.name)
                put("IdMarket", session.idMarket)
                put("User", user)
                put("Comentary", "${formatQuantity(line.quantity)} Vendidos por $user a ${row.clientName}")
                put("Barcode", line.barcode)
                // Sin "antes y después" en lo que no tiene cantidad.
                if (unique || infinite) {
                    put("Before", null as Double?)
                    put("After", null as Double?)
                } else {
                    put("Before", before)
                    put("After", after)
                }
                put("IdUser", session.userId)
                put("IdPerson", session.idPerson)
            },
        )
        if (unique) {
            remote.createInventoryReport(
                buildJsonObject {
                    put("IdSale", saleId)
                    put("IdProduct", line.idProduct)
                    put("Name", line.name)
                    put("IdMarket", session.idMarket)
                    put("User", user)
                    put("Comentary", "Producto vendido por $user a ${row.clientName}")
                    put("Barcode", line.barcode)
                },
            )
        }

        val guarantee = warehouse.idGuarantee
        if (guarantee != null) {
            val timeZone = session.store?.timeZone ?: DEFAULT_TIME_ZONE
            val iso = formatIsoDatePlusDays(row.createdAt, warehouse.guaranteeDuration ?: 0, timeZone)
            // El POS guarda la expiración como DD-MM-YYYY (`completeOrder.vue → getFutureDate`).
            val (y, m, d) = iso.split("-")
            remote.createGuarantee(
                buildJsonObject {
                    put("IdGuarantee", guarantee)
                    put("IdProductSale", lineId)
                    put("IdProduct", line.idProduct)
                    put("IdWarehouse", idWarehouse)
                    put("IdSale", saleId)
                    put("Name", line.name)
                    put("Barcode", warehouse.barcode ?: line.barcode)
                    put("ExpirationDate", "$d-$m-$y")
                    put("Active", true)
                    put("IdMarket", session.idMarket)
                },
            )
        }
    }

    private fun saleBody(draft: InvoiceDraft, row: OutboxEntity, session: Session, p: SendProgress): JsonObject {
        val totals = draft.totals
        val payment = draft.payment
        val timeZone = session.store?.timeZone ?: DEFAULT_TIME_ZONE
        return buildJsonObject {
            put("IdClient", draft.client?.id)
            put("Client", row.clientName)
            put("ClientDiscount", totals.discount)
            put("IdMarket", session.idMarket)
            // Fecha del momento en que se guardó, no del envío: puede salir horas después.
            put("Date", formatPosDate(row.createdAt, timeZone))
            put("SubTotal", totals.subtotal)
            put("Tax", totals.tax)
            put("Total", totals.total)
            if (draft.isQuote) {
                put("Money", 0.0)
                put("Change", 0.0)
            } else {
                // `Money` es EFECTIVO, no "total cobrado" (guía 08 §6).
                put("Money", payment.cash)
                put("MoneyDeposit", payment.transfer)
                put("MoneyCredit", payment.card)
                put("Change", draft.change)
                put("trade", 0.0)
                put("financing", 0.0)
                put("IdCuenta", draft.account?.id)
                put("Secuency", p.sequence)
                put("NCF", p.ncf)
                put("RNC", draft.rnc.trim().takeIf { draft.withNcf && it.isNotEmpty() })
            }
            // Dónde pagar (API `F4`): lo pinta el PDF. Solo en lo que se paga después.
            if (draft.asksForPayment && !draft.payTo.isEmpty) {
                put("PaymentAccounts", draft.payTo.accountIds)
                put("PaymentNote", draft.payTo.note.trim().takeIf { it.isNotEmpty() })
            }
            put("Status", draft.status)
            put("taxType", draft.taxType.apiValue)
            put("IdPerson", session.idPerson)
            put("Username", session.displayName)
            put("Torning", session.torning?.toIntOrNull())
        }
    }

    private fun lineBody(draft: InvoiceDraft, index: Int, saleId: Int, session: Session): JsonObject {
        val line = draft.lines[index]
        val totals = line.totals(draft.taxType, draft.taxRate)
        return buildJsonObject {
            put("IdSale", saleId)
            put("IdMarket", session.idMarket)
            put("Barcode", line.barcode)
            put("Name", line.name)
            put("IdProduct", line.idProduct)
            put("idWarehouse", line.idWarehouse) // camelCase: es la columna
            put("Amount", line.quantity)
            put("Price", line.unitPrice)
            put("Tax", totals.tax)
            put("Discount", line.discount)
            // Como el POS: `Total` de la línea es el bruto (cantidad × precio − descuento).
            put("Total", line.gross())
            put("isRetail", false)
            put("isTrade", false)
            if (!draft.isQuote) put("sold", true)
            put("Torning", session.torning?.toIntOrNull())
        }
    }

    private suspend fun bestEffort(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        false
    }

    private fun formatQuantity(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
