package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbSymbol
import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** En tablet las listas no se estiran de lado a lado. */
val PbListMaxWidth = PbControl.maxFormWidth * 1.6f

/**
 * Una fila de lista como isla propia (regla del diseño: islas sobre el lienzo, separadas
 * 10 dp y con borde de 1 dp; nunca sombra). Antes las filas iban pegadas con una línea entre
 * ellas y costaba distinguir dónde acababa una (pedido del usuario, 2026-09-27).
 *
 * `accent`: franja de 4 dp a la izquierda para lo que pide atención (vencida, por vencer). Va
 * además de un texto o etiqueta: el color solo nunca es el único aviso.
 */
@Composable
fun PbListCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    background: Color = PbTheme.colors.island,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = PbSpace.s5, vertical = PbSpace.s4),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(PbSpace.s3),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PbTheme.colors
    val shape = RoundedCornerShape(PbRadius.lg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PbSpace.s6, vertical = PbListCardGap / 2)
            .clip(shape)
            .background(background, shape)
            .border(PbControl.border, colors.outline, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(
                if (accent != null) {
                    Modifier.drawBehind { drawRect(accent, size = Size(PbListCardAccent.toPx(), size.height)) }
                } else {
                    Modifier
                },
            )
            .then(modifier)
            .padding(contentPadding)
            .then(if (accent != null) Modifier.padding(start = PbListCardAccent) else Modifier),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** Separación entre tarjetas de una lista (la mitad arriba y la mitad abajo de cada una). */
val PbListCardGap = PbSpace.s4
private val PbListCardAccent = 4.dp

/**
 * Fila de lista: icono (o `leading`), título y subtítulo, y lo que haga falta a la derecha
 * (importe, etiqueta), dentro de su [PbListCard]. Sin `onClick` no se puede tocar.
 */
@Composable
fun PbItemRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: PbSymbol? = null,
    iconTint: Color = PbTheme.colors.primary,
    /** Sustituye a `icon` (p. ej. el logo del banco). */
    leading: (@Composable () -> Unit)? = null,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = PbTheme.colors
    PbListCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PbSpace.s5),
        ) {
            when {
                leading != null -> leading()
                icon != null -> PbIcon(icon = icon, contentDescription = null, tint = if (dimmed) colors.muted else iconTint)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PbText(
                    text = title,
                    style = PbTheme.typography.bodyStrong,
                    color = if (dimmed) colors.muted else colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    PbText(
                        text = subtitle,
                        style = PbTheme.typography.caption,
                        color = colors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing()
        }
    }
}

/** Importe a la derecha de una fila, con una línea pequeña opcional debajo. */
@Composable
fun PbRowAmount(amount: String, caption: String? = null, color: Color = PbTheme.colors.ink) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PbText(text = amount, style = PbTheme.typography.amount, color = color, maxLines = 1)
        if (caption != null) PbText(text = caption, style = PbTheme.typography.caption, color = PbTheme.colors.muted, maxLines = 1)
    }
}

/** Aviso (sin conexión, error) con "Reintentar" debajo. */
@Composable
fun PbStatusBanner(message: String, tone: PbBannerTone, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(PbSpace.s6),
        verticalArrangement = Arrangement.spacedBy(PbSpace.s3),
    ) {
        PbBanner(message = message, tone = tone)
        if (onRetry != null) {
            PbButton(
                text = "Reintentar",
                onClick = onRetry,
                variant = PbButtonVariant.Outline,
                leadingIcon = PbSymbols.Sync,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Indicador de carga centrado en una fila. */
@Composable
fun PbSpinnerRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(PbSpace.s6), horizontalArrangement = Arrangement.Center) { PbSpinner() }
}

/** Rótulo de un grupo de filas ("TE DEBE", "MOVIMIENTOS"). */
@Composable
fun PbListHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = PbSpace.s6, end = PbSpace.s6, top = PbSpace.s7, bottom = PbSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PbOverline(text = text, modifier = Modifier.weight(1f))
        trailing()
    }
}

/**
 * Pide la página siguiente cuando faltan `preload` filas para el final. La clave lleva el total
 * y `hasMore`: si una página no llena la pantalla, hay que volver a pedir aunque "estar cerca
 * del final" no haya cambiado.
 */
@Composable
fun LoadMoreEffect(listState: LazyListState, hasMore: Boolean, preload: Int = 5, onLoadMore: () -> Unit) {
    LaunchedEffect(listState, hasMore) {
        if (!hasMore) return@LaunchedEffect
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            (total > 0 && last >= total - preload) to total
        }
            .distinctUntilChanged()
            .filter { (nearEnd, _) -> nearEnd }
            .collect { onLoadMore() }
    }
}

/** Caja de búsqueda de las listas (sin pedir el foco al abrir: en un destino se viene a mirar). */
@Composable
fun PbSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    PbTextField(
        value = query,
        onValueChange = onQueryChange,
        label = "Buscar",
        placeholder = placeholder,
        leadingIcon = PbSymbols.Search,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PbSpace.s6, end = PbSpace.s6, bottom = PbSpace.s5),
        fieldModifier = fieldModifier,
    )
}

/**
 * Cabecera de las pantallas que se apilan (ficha, editor): ← título · acciones, en la isla con
 * línea de 1 dp debajo. `below` va dentro de la isla (buscador, chips).
 */
@Composable
fun PbStackHeader(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        Modifier
            .background(PbTheme.colors.island)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        PbTopBar(
            title = title,
            navigation = { PbIconButton(icon = PbSymbols.ArrowBack, contentDescription = "Volver", onClick = onBack) },
            actions = actions,
        )
        below()
        Box(Modifier.fillMaxWidth().height(PbControl.border).background(PbTheme.colors.outline))
    }
}
