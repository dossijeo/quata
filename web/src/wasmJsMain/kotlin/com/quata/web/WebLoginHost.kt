package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.quata.core.model.CountryPrefix
import com.quata.core.platform.PlatformServices
import com.quata.feature.auth.presentation.AuthScreenLayoutContent
import com.quata.feature.auth.presentation.login.LoginEffect
import com.quata.feature.auth.presentation.login.LoginForm
import com.quata.feature.auth.presentation.login.LoginFormStrings
import com.quata.feature.auth.presentation.login.LoginViewModel
import kotlinx.coroutines.flow.collect

/** Browser launcher for the shared login form; auth transport and persistence stay injectable. */
@Composable
fun WebLoginHost(
    platformServices: PlatformServices,
    configuration: WebRuntimeConfiguration,
    onLoginSuccess: (WebPushSessionCoordinator) -> Unit,
) {
    val repository = remember(configuration, platformServices.preferences) {
        WebAuthRepository(configuration, platformServices.preferences)
    }
    val viewModel = remember(repository) { LoginViewModel(repository) }
    val pushCoordinator = remember(repository, configuration) {
        WebPushSessionCoordinator(configuration, repository)
    }
    val state by viewModel.uiState.collectAsState()
    var unavailableActionMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(viewModel) {
        onDispose(viewModel::close)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is LoginEffect.Success) {
                platformServices.preferences.putString(WebSessionReadyKey, "true")
                onLoginSuccess(pushCoordinator)
            }
        }
    }

    AuthScreenLayoutContent(
        padding = PaddingValues(),
        subtitle = "Quata Web",
        portraitLogoSpacing = 22.dp,
    ) { isLandscape ->
        LoginForm(
            state = state,
            prefixes = WebCountryPrefixes,
            strings = WebLoginStrings,
            isLandscape = isLandscape,
            showMockNotice = false,
            onEvent = viewModel::onEvent,
            onForgotPassword = {
                unavailableActionMessage = "La recuperaci\u00f3n de contrase\u00f1a a\u00fan no est\u00e1 disponible en Quata Web."
            },
            onGoToRegister = {
                unavailableActionMessage = "El registro a\u00fan no est\u00e1 disponible en Quata Web."
            },
        )
        unavailableActionMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal const val WebSessionReadyKey = "web.auth.session_ready"

private val WebCountryPrefixes = listOf(
    CountryPrefix(code = "240", label = "+240 - Guinea Ecuatorial"),
    CountryPrefix(code = "1", label = "+1 - United States / Canada"),
    CountryPrefix(code = "34", label = "+34 - Espa\u00f1a"),
)

private val WebLoginStrings = LoginFormStrings(
    phone = "Tel\u00e9fono",
    password = "Contrase\u00f1a",
    signingIn = "Iniciando sesi\u00f3n\u2026",
    signIn = "Iniciar sesi\u00f3n",
    forgotPassword = "He olvidado mi contrase\u00f1a",
    createAccount = "Crear cuenta",
    searchPrefix = "Buscar prefijo",
    mockNotice = "",
)
