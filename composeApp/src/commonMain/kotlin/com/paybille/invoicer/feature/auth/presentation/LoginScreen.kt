package com.paybille.invoicer.feature.auth.presentation

import com.paybille.invoicer.core.designsystem.theme.PbSymbols
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.paybille.invoicer.core.designsystem.components.PbBanner
import com.paybille.invoicer.core.designsystem.components.PbBannerTone
import com.paybille.invoicer.core.designsystem.components.PbBrand
import com.paybille.invoicer.core.designsystem.components.PbButton
import com.paybille.invoicer.core.designsystem.components.PbButtonVariant
import com.paybille.invoicer.core.designsystem.components.PbCard
import com.paybille.invoicer.core.designsystem.components.PbListRow
import com.paybille.invoicer.core.designsystem.components.PbOverline
import com.paybille.invoicer.core.designsystem.components.PbScreen
import com.paybille.invoicer.core.designsystem.components.PbSpinner
import com.paybille.invoicer.core.designsystem.components.PbText
import com.paybille.invoicer.core.designsystem.components.PbTextField
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.feature.auth.domain.StoreOption

data object LoginScreen : Screen {
    @Composable
    override fun Content() {
        val model = koinScreenModel<LoginScreenModel>()
        val state by model.state.collectAsState()
        LoginContent(
            state = state,
            onUsernameChange = model::onUsernameChange,
            onPasswordChange = model::onPasswordChange,
            onSubmit = model::submit,
            onChooseStore = model::chooseStore,
            onBackToCredentials = model::backToCredentials,
        )
    }
}

/**
 * Login como el del diseño (versión teclado): logo arriba, una sola tarjeta con título,
 * rótulos en mayúsculas y un único botón lleno a todo lo ancho. La elección de tienda usa la
 * misma tarjeta, así que pasar de un paso a otro no mueve nada más de la pantalla.
 */
@Composable
private fun LoginContent(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onChooseStore: (StoreOption) -> Unit,
    onBackToCredentials: () -> Unit,
) {
    PbScreen(centered = true) {
        PbBrand(modifier = Modifier.fillMaxWidth().padding(bottom = PbSpace.s4))

        PbCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(PbSpace.s8),
            verticalArrangement = Arrangement.spacedBy(PbSpace.s6),
        ) {
            val stores = state.stores
            if (stores == null) {
                CredentialsStep(state, onUsernameChange, onPasswordChange, onSubmit)
            } else {
                StoreStep(state, stores, onChooseStore, onBackToCredentials)
            }
        }

        PbText(
            text = "Todos los derechos reservados · PayBille",
            style = PbTheme.typography.caption,
            color = PbTheme.colors.muted2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CredentialsStep(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focus = LocalFocusManager.current

    Header(title = "Iniciar sesión", subtitle = "Usa tu cuenta de PayBille.")

    PbTextField(
        value = state.username,
        onValueChange = onUsernameChange,
        label = "Usuario",
        error = state.usernameError,
        enabled = !state.submitting,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
        modifier = Modifier.fillMaxWidth(),
    )
    PbTextField(
        value = state.password,
        onValueChange = onPasswordChange,
        label = "Contraseña",
        error = state.passwordError,
        enabled = !state.submitting,
        isPassword = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            focus.clearFocus()
            onSubmit()
        }),
        modifier = Modifier.fillMaxWidth(),
    )

    state.message?.let { MessageBanner(it) }

    PbButton(
        text = "Iniciar sesión",
        onClick = {
            focus.clearFocus()
            onSubmit()
        },
        loading = state.submitting,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StoreStep(
    state: LoginUiState,
    stores: List<StoreOption>,
    onChooseStore: (StoreOption) -> Unit,
    onBackToCredentials: () -> Unit,
) {
    // Atrás vuelve a las credenciales, no cierra la app.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !state.submitting,
        onBackCompleted = onBackToCredentials,
    )

    Header(
        title = "Elige la tienda",
        subtitle = "Tu usuario tiene acceso a varias tiendas. ¿En cuál vas a facturar?",
    )

    Column(verticalArrangement = Arrangement.spacedBy(PbSpace.s3)) {
        PbOverline(text = "Tus tiendas")
        stores.forEach { store ->
            PbListRow(
                title = store.name,
                subtitle = store.address,
                leadingIcon = PbSymbols.Storefront,
                enabled = !state.submitting,
                onClick = { onChooseStore(store) },
            )
        }
    }

    if (state.submitting) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PbSpace.s4, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PbSpinner()
            PbText(text = "Entrando…", style = PbTheme.typography.bodyStrong, color = PbTheme.colors.ink2)
        }
    }

    state.message?.let { MessageBanner(it) }

    PbButton(
        text = "Usar otra cuenta",
        onClick = onBackToCredentials,
        variant = PbButtonVariant.Ghost,
        enabled = !state.submitting,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Cabecera de la tarjeta: título del diseño (17/800) y una línea que dice qué se pide. */
@Composable
private fun Header(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(PbSpace.s1)) {
        PbText(
            text = title,
            style = PbTheme.typography.heading,
            modifier = Modifier.semantics { heading() },
        )
        PbText(text = subtitle, style = PbTheme.typography.body, color = PbTheme.colors.muted)
    }
}

@Composable
private fun MessageBanner(message: LoginMessage) {
    PbBanner(
        message = message.text,
        tone = if (message.offline) PbBannerTone.Offline else PbBannerTone.Error,
    )
}
