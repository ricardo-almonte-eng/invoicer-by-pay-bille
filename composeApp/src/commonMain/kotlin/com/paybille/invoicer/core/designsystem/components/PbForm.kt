package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme

/**
 * Pantalla de formulario (alta y edición de cliente, producto, cuenta): cabecera con ←, cuerpo
 * con scroll y el botón de guardar **fijo abajo**, que sube con el teclado. Con cambios sin
 * guardar, *atrás* pregunta antes de salir (guía 07, regla 3).
 *
 * `offline`: guardar necesita red; se dice en vez de dejar tocar un botón que va a fallar.
 *
 * `overlay`: las hojas del formulario (elegir categoría, marca…). Van encima de todo, bajo la
 * de "¿Descartar los cambios?".
 */
@Composable
fun PbFormScreen(
    title: String,
    saveLabel: String,
    dirty: Boolean,
    saving: Boolean,
    error: String?,
    onSave: () -> Unit,
    onBack: () -> Unit,
    saveEnabled: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val tryBack = { if (dirty && !saving) confirmExit = true else if (!saving) onBack() }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !confirmExit && (dirty || saving),
        onBackCompleted = { tryBack() },
    )

    Box(Modifier.fillMaxSize().background(PbTheme.colors.canvas)) {
        Column(Modifier.fillMaxSize()) {
            PbStackHeader(title = title, onBack = tryBack)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = PbControl.maxFormWidth)
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(PbSpace.s6),
                    verticalArrangement = Arrangement.spacedBy(PbSpace.s5),
                    content = content,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(PbTheme.colors.island)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(PbControl.border).background(PbTheme.colors.outline))
                Column(
                    Modifier.widthIn(max = PbControl.maxFormWidth).fillMaxWidth().padding(PbSpace.s6),
                    verticalArrangement = Arrangement.spacedBy(PbSpace.s3),
                ) {
                    error?.let { PbBanner(message = it, tone = PbBannerTone.Error) }
                    PbButton(
                        text = saveLabel,
                        onClick = onSave,
                        loading = saving,
                        enabled = saveEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        overlay()

        PbSheet(visible = confirmExit, title = "¿Descartar los cambios?", onDismiss = { confirmExit = false }) {
            PbText(
                text = "Lo que escribiste no se ha guardado.",
                style = PbTheme.typography.body,
                color = PbTheme.colors.ink2,
            )
            PbButton(text = "Seguir editando", onClick = { confirmExit = false }, modifier = Modifier.fillMaxWidth())
            PbButton(
                text = "Descartar",
                onClick = {
                    confirmExit = false
                    onBack()
                },
                variant = PbButtonVariant.Danger,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
