package com.paybille.invoicer.feature.banks.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.components.PbChip
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.ui.BankLogo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbChipGroup
import com.paybille.invoicer.core.designsystem.components.PbEmptyState
import com.paybille.invoicer.core.designsystem.components.PbFabClearance
import com.paybille.invoicer.core.designsystem.components.PbFormScreen
import com.paybille.invoicer.core.designsystem.components.PbIconButton
import com.paybille.invoicer.core.designsystem.components.PbItemRow
import com.paybille.invoicer.core.designsystem.components.PbListHeader
import com.paybille.invoicer.core.designsystem.components.PbListMaxWidth
import com.paybille.invoicer.core.designsystem.components.PbNumberField
import com.paybille.invoicer.core.designsystem.components.PbRowAmount
import com.paybille.invoicer.core.designsystem.components.PbSheet
import com.paybille.invoicer.core.designsystem.components.PbStackHeader
import com.paybille.invoicer.core.designsystem.components.PbStatTile
import com.paybille.invoicer.core.designsystem.components.PbSwitchRow
import com.paybille.invoicer.core.designsystem.components.PbTag
import com.paybille.invoicer.core.designsystem.components.PbTagTone
import com.paybille.invoicer.core.designsystem.components.PbOverline
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.components.PbTextField
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.core.format.formatMoney
import com.paybille.invoicer.core.format.formatShortDate
import com.paybille.invoicer.core.format.label
import com.paybille.invoicer.core.ui.syncStatusItem
import com.paybille.invoicer.feature.banks.data.ACCOUNT_TYPES
import com.paybille.invoicer.feature.banks.data.BANKS
import com.paybille.invoicer.feature.banks.data.local.AccountMovementEntity
import com.paybille.invoicer.feature.banks.data.local.BankAccountEntity
import com.paybille.invoicer.feature.main.TabHeader
import org.koin.core.parameter.parametersOf

private fun typeLabel(type: String) = ACCOUNT_TYPES.firstOrNull { it.first == type }?.second ?: type

// ---------------------------------------------------------------- Destino

/** Destino Bancos: cuentas de dinero (caja y bancos) con su balance. */
@Composable
fun BanksTab(
    model: BanksScreenModel,
    onOpenAccount: (Int) -> Unit,
) {
    val state by model.state.collectAsState()
    val accounts by model.accounts.collectAsState()
    val active = accounts.filter { it.active }

    LaunchedEffect(Unit) { model.onVisible() }

    Column(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
        TabHeader(title = "Cuentas bancarias")
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = PbListMaxWidth),
                contentPadding = PaddingValues(top = PbSpace.s2, bottom = PbFabClearance),
            ) {
                syncStatusItem(state.sync, hasRows = accounts.isNotEmpty(), onRetry = model::refresh)
                if (accounts.isNotEmpty()) {
                    item(key = "total") {
                        PbStatTile(
                            label = "Balance total",
                            value = formatMoney(active.sumOf { it.balance }),
                            hint = if (active.size == 1) "1 cuenta activa" else "${active.size} cuentas activas",
                            modifier = Modifier.fillMaxWidth().padding(PbSpace.s6),
                        )
                    }
                }
                // Como el POS: si no hay caja activa, se ofrece crearla.
                if (state.sync.attempted && !state.sync.syncing && !state.sync.offline && state.sync.error == null && active.none { it.type == "Caja" }) {
                    item(key = "cash") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = PbSpace.s6, vertical = PbSpace.s3),
                            verticalArrangement = Arrangement.spacedBy(PbSpace.s3),
                        ) {
                            PbBanner(message = "No tienes una cuenta de efectivo. Los cobros en efectivo necesitan una.", tone = PbBannerTone.Offline)
                            PbButton(
                                text = "Crear Efectivo General",
                                onClick = model::createCash,
                                loading = state.creatingCash,
                                variant = PbButtonVariant.Outline,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            state.message?.let { PbBanner(message = it, tone = PbBannerTone.Error) }
                        }
                    }
                }
                items(accounts, key = { it.id }) { account -> AccountRow(account, onClick = { onOpenAccount(account.id) }) }
                if (accounts.isEmpty() && state.sync.attempted && !state.sync.syncing && !state.sync.offline && state.sync.error == null) {
                    item(key = "empty") {
                        PbEmptyState(icon = PbSymbols.AccountBalance, title = "Todavía no hay cuentas", message = "Añade tu caja y tus cuentas de banco con el icono de arriba.")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(account: BankAccountEntity, onClick: () -> Unit) {
    PbItemRow(
        title = account.name,
        subtitle = listOfNotNull(
            typeLabel(account.type),
            account.bankName,
            account.accountNumber?.let { "···${it.takeLast(4)}" },
            "Inactiva".takeUnless { account.active },
        ).joinToString(" · "),
        leading = { BankLogo(account.bankName, fallback = accountIcon(account.type)) },
        dimmed = !account.active,
        onClick = onClick,
    ) {
        PbRowAmount(amount = formatMoney(account.balance), color = if (account.balance < 0) PbTheme.colors.error else PbTheme.colors.ink)
    }
}

// ---------------------------------------------------------------- Ficha

/** Una cuenta: balance, ingresos y egresos del rango, y sus movimientos. */
data class BankAccountDetailScreen(val id: Int) : Screen {
    override val key: String = "account-$id"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<BankAccountDetailScreenModel> { parametersOf(id) }
        val state by model.state.collectAsState()
        val account by model.account.collectAsState()
        val movements by model.movements.collectAsState()
        val income = movements.filter { it.type == "Ingreso" }.sumOf { it.amount }
        val outgo = movements.filter { it.type == "Egreso" }.sumOf { it.amount }

        Box(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
            Column(Modifier.fillMaxSize()) {
                PbStackHeader(
                    title = account?.name ?: "Cuenta",
                    onBack = { navigator.pop() },
                    actions = {
                        if (account != null) {
                            PbIconButton(icon = PbSymbols.Edit, contentDescription = "Editar", onClick = { navigator.push(BankAccountEditorScreen(id)) })
                        }
                    },
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().widthIn(max = PbListMaxWidth),
                        contentPadding = PaddingValues(bottom = PbSpace.s10),
                    ) {
                        account?.let { a ->
                            item(key = "balance") {
                                Column(Modifier.padding(PbSpace.s6), verticalArrangement = Arrangement.spacedBy(PbSpace.s5)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PbSpace.s5)) {
                                        BankLogo(a.bankName, fallback = accountIcon(a.type), size = 56.dp)
                                        Column(Modifier.weight(1f)) {
                                            PbText(
                                                text = a.bankName?.takeIf { it.isNotBlank() } ?: typeLabel(a.type),
                                                style = PbTheme.typography.subtitle,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            a.accountNumber?.takeIf { it.isNotBlank() }?.let {
                                                PbText(text = it, style = PbTheme.typography.amount, color = PbTheme.colors.muted)
                                            }
                                            listOfNotNull(a.holderName, a.holderId).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                                                PbText(
                                                    text = it,
                                                    style = PbTheme.typography.caption,
                                                    color = PbTheme.colors.muted,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                    PbStatTile(
                                        label = "Balance actual",
                                        value = formatMoney(a.balance),
                                        hint = listOfNotNull(typeLabel(a.type), a.bankName, a.accountNumber, "Inactiva".takeUnless { a.active }).joinToString(" · "),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(PbSpace.s5)) {
                                        PbStatTile(label = "Entró", value = formatMoney(income), modifier = Modifier.weight(1f).fillMaxHeight())
                                        PbStatTile(label = "Salió", value = formatMoney(outgo), modifier = Modifier.weight(1f).fillMaxHeight())
                                    }
                                    PbButton(
                                        text = "Nuevo movimiento",
                                        onClick = model::openMovement,
                                        variant = PbButtonVariant.Outline,
                                        leadingIcon = PbSymbols.SwapVert,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        item(key = "range") {
                            Column(Modifier.padding(horizontal = PbSpace.s6), verticalArrangement = Arrangement.spacedBy(PbSpace.s3)) {
                                PbChipGroup(
                                    options = MOVEMENT_DAYS,
                                    selected = state.days,
                                    label = { "$it días" },
                                    onSelect = model::selectDays,
                                )
                                state.range?.let { PbText(text = it.label(), style = PbTheme.typography.caption, color = PbTheme.colors.muted) }
                            }
                        }
                        syncStatusItem(
                            state.sync,
                            hasRows = movements.isNotEmpty(),
                            onRetry = model::refresh,
                            offlineEmpty = "Sin conexión. Los movimientos de este rango se ven con internet.",
                        )
                        item(key = "moves-h") { PbListHeader("Movimientos") }
                        if (movements.isEmpty() && state.sync.attempted && !state.sync.syncing) {
                            item(key = "moves-none") {
                                PbText(
                                    text = "Sin movimientos en este rango.",
                                    style = PbTheme.typography.body,
                                    color = PbTheme.colors.muted,
                                    modifier = Modifier.padding(horizontal = PbSpace.s6),
                                )
                            }
                        }
                        items(movements, key = { it.id }) { MovementRow(it, state.timeZone) }
                    }
                }
            }

            MovementSheet(state, model)
        }
    }
}

@Composable
private fun MovementRow(move: AccountMovementEntity, timeZone: String) {
    val incoming = move.type == "Ingreso"
    PbItemRow(
        title = move.description ?: if (incoming) "Ingreso" else "Egreso",
        subtitle = listOfNotNull(
            move.createdAt?.let { formatShortDate(it, timeZone) },
            move.referenceType,
            move.reference,
        ).joinToString(" · "),
        icon = if (incoming) PbSymbols.ArrowDownward else PbSymbols.ArrowUpward,
        iconTint = if (incoming) PbTheme.colors.success else PbTheme.colors.error,
    ) {
        PbRowAmount(
            amount = (if (incoming) "+" else "−") + formatMoney(move.amount),
            caption = move.balanceAfter?.let { "Queda ${formatMoney(it)}" },
            color = if (incoming) PbTheme.colors.success else PbTheme.colors.ink,
        )
    }
}

@Composable
private fun MovementSheet(state: BankDetailUiState, model: BankAccountDetailScreenModel) {
    PbSheet(visible = state.sheetOpen, title = "Nuevo movimiento", onDismiss = model::closeMovement) {
        PbChipGroup(
            options = MovementType.entries,
            selected = state.movementType,
            label = { it.label },
            onSelect = { v -> model.updateMovement { it.copy(movementType = v) } },
        )
        PbNumberField(
            value = state.amount,
            onValueChange = { v -> model.updateMovement { it.copy(amount = v) } },
            label = "Monto",
            prefix = "$",
            modifier = Modifier.fillMaxWidth(),
        )
        PbTextField(
            value = state.description,
            onValueChange = { v -> model.updateMovement { it.copy(description = v) } },
            label = "Descripción",
            placeholder = "Depósito, retiro, pago de luz…",
            modifier = Modifier.fillMaxWidth(),
        )
        PbTextField(
            value = state.reference,
            onValueChange = { v -> model.updateMovement { it.copy(reference = v) } },
            label = "Referencia",
            modifier = Modifier.fillMaxWidth(),
        )
        state.movementError?.let { PbBanner(message = it, tone = PbBannerTone.Error) }
        PbButton(
            text = if (state.movementType == MovementType.In) "Registrar ingreso" else "Registrar egreso",
            onClick = model::saveMovement,
            loading = state.savingMovement,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------- Editor

/** Alta (`id == null`) y edición de una cuenta (`CreateCuenta.vue`). No se borra desde el teléfono. */
data class BankAccountEditorScreen(val id: Int? = null) : Screen {
    override val key: String = "account-editor-${id ?: "new"}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = koinScreenModel<BankAccountEditorScreenModel> { parametersOf(id) }
        val state by model.state.collectAsState()
        val form = state.form
        val isCash = form.type == "Caja"

        LaunchedEffect(state.done) { if (state.done) navigator.pop() }

        PbFormScreen(
            title = if (id == null) "Nueva cuenta" else "Editar cuenta",
            saveLabel = if (id == null) "Crear cuenta" else "Guardar cambios",
            dirty = state.dirty,
            saving = state.saving,
            error = state.error,
            saveEnabled = state.loaded,
            onSave = model::save,
            onBack = { navigator.pop() },
        ) {
            PbTextField(
                value = form.name,
                onValueChange = { v -> model.update { it.copy(name = v) } },
                label = "Nombre",
                placeholder = "Popular nómina, Caja chica…",
                required = true,
                error = state.nameError,
                modifier = Modifier.fillMaxWidth(),
            )
            PbOverline(text = "Tipo")
            PbChipGroup(
                options = ACCOUNT_TYPES.map { it.first },
                selected = form.type,
                label = ::typeLabel,
                onSelect = { v -> model.update { it.copy(type = v) } },
            )
            if (!isCash) {
                PbOverline(text = "Banco")
                BankPicker(
                    selected = form.bankName,
                    onSelect = { v -> model.update { it.copy(bankName = if (it.bankName == v) "" else v) } },
                )
                PbTextField(
                    value = form.accountNumber,
                    onValueChange = { v -> model.update { it.copy(accountNumber = v) } },
                    label = "Número de cuenta",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                PbOverline(text = "Titular")
                PbText(
                    text = "Para que te transfieran desde otro banco. Sale en la factura cuando eliges esta cuenta en \"Dónde pagar\".",
                    style = PbTheme.typography.caption,
                    color = PbTheme.colors.muted,
                )
                PbTextField(
                    value = form.holderName,
                    onValueChange = { v -> model.update { it.copy(holderName = v) } },
                    label = "Nombre del titular",
                    placeholder = "Como aparece en el banco",
                    modifier = Modifier.fillMaxWidth(),
                )
                PbTextField(
                    value = form.holderId,
                    onValueChange = { v -> model.update { it.copy(holderId = v.filter { c -> c.isDigit() || c == '-' }.take(MAX_HOLDER_ID)) } },
                    label = "Cédula o RNC del titular",
                    placeholder = "001-0000000-0",
                    numeric = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PbTextField(
                value = form.description,
                onValueChange = { v -> model.update { it.copy(description = v) } },
                label = "Descripción",
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            PbSwitchRow(
                title = "Activa",
                subtitle = "Las inactivas no salen para cobrar.",
                checked = form.active,
                onCheckedChange = { v -> model.update { it.copy(active = v) } },
            )
            if (id == null) {
                PbTag(text = "Empieza con balance ${formatMoney(0.0)}", tone = PbTagTone.Neutral)
            }
        }
    }
}

private fun accountIcon(type: String?) = if (type == "Caja") PbSymbols.Payments else PbSymbols.AccountBalance

/**
 * Los bancos del POS con su logo, en cuadrícula de 4 (caben a 360 dp). Tocar el elegido lo
 * quita. Un banco escrito a mano en el POS (fuera de la lista) sale al final como chip.
 */
@Composable
private fun BankPicker(selected: String, onSelect: (String) -> Unit) {
    val colors = PbTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(PbSpace.s3)) {
        BANKS.chunked(BANK_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(PbSpace.s3)) {
                row.forEach { bank ->
                    val chosen = bank == selected
                    val shape = RoundedCornerShape(PbRadius.lg)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (chosen) colors.primaryTint else colors.island, shape)
                            .border(if (chosen) PbControl.borderFocus else PbControl.border, if (chosen) colors.primary else colors.outline, shape)
                            .selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(bank) })
                            .padding(vertical = PbSpace.s3, horizontal = PbSpace.s1),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(PbSpace.s2),
                    ) {
                        BankLogo(bank, fallback = PbSymbols.AccountBalance, size = 44.dp)
                        PbText(
                            text = bank,
                            style = PbTheme.typography.caption,
                            color = if (chosen) colors.primary else colors.ink2,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // La última fila, incompleta, no estira sus casillas.
                repeat(BANK_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (selected.isNotBlank() && selected !in BANKS) {
            PbChip(text = selected, selected = true, onClick = { onSelect(selected) })
        }
    }
}

private const val BANK_COLUMNS = 4

/** Cédula con guiones (13) cabe; un RNC (9) también. `Cuentas.HolderId` es VARCHAR(20). */
private const val MAX_HOLDER_ID = 20
