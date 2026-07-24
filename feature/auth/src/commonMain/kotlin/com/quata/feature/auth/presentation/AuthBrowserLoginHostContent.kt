package com.quata.feature.auth.presentation

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
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.presentation.login.LoginEffect
import com.quata.feature.auth.presentation.login.LoginForm
import com.quata.feature.auth.presentation.login.LoginFormStrings
import com.quata.feature.auth.presentation.login.LoginViewModel

/** Host-neutral browser login orchestration; transports and post-login work are injected. */
@Composable
fun AuthBrowserLoginHostContent(
    repository: AuthRepository,
    prefixes: List<CountryPrefix>,
    strings: LoginFormStrings,
    subtitle: String,
    recoveryUnavailableMessage: String,
    registerUnavailableMessage: String,
    onLoginSuccess: suspend () -> Unit,
) {
    val viewModel = remember(repository) { LoginViewModel(repository) }
    val state by viewModel.uiState.collectAsState()
    var unavailableMessage by remember { mutableStateOf<String?>(null) }
    DisposableEffect(viewModel) { onDispose(viewModel::close) }
    LaunchedEffect(viewModel) { viewModel.effects.collect { if (it is LoginEffect.Success) onLoginSuccess() } }
    AuthScreenLayoutContent(PaddingValues(), subtitle, portraitLogoSpacing = 22.dp) { isLandscape ->
        LoginForm(state = state, prefixes = prefixes, strings = strings, isLandscape = isLandscape, showMockNotice = false, onEvent = viewModel::onEvent,
            onForgotPassword = { unavailableMessage = recoveryUnavailableMessage },
            onGoToRegister = { unavailableMessage = registerUnavailableMessage })
        unavailableMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
