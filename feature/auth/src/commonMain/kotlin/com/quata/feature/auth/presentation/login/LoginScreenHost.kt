package com.quata.feature.auth.presentation.login

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.quata.core.model.CountryPrefix
import com.quata.feature.auth.domain.LoginRepository
import com.quata.feature.auth.presentation.AuthCatalogCopy
import com.quata.feature.auth.presentation.AuthScreenLayoutContent

/**
 * Product login screen shared verbatim by every platform launcher.
 *
 * Launchers inject only the repository, localized catalogue and navigation effects. They do not
 * replace any visual control, so Android and Wasm render the same Compose hierarchy.
 */
@Composable
fun LoginScreenHost(
    padding: PaddingValues,
    repository: LoginRepository,
    catalog: AuthCatalogCopy,
    prefixes: List<CountryPrefix>,
    showMockNotice: Boolean,
    onGoToRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onLoginSuccess: () -> Unit,
    onLoginFailure: (String) -> Unit = {},
) {
    val viewModel = remember(repository) { LoginViewModel(repository) }
    val state by viewModel.uiState.collectAsState()

    DisposableEffect(viewModel) {
        onDispose(viewModel::close)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LoginEffect.Success -> onLoginSuccess()
                is LoginEffect.Failure -> onLoginFailure(effect.message)
            }
        }
    }

    AuthScreenLayoutContent(
        padding = padding,
        subtitle = catalog.loginSubtitle,
        portraitLogoSpacing = 22.dp,
    ) { isLandscape ->
        LoginForm(
            state = state,
            prefixes = prefixes,
            strings = catalog.login,
            isLandscape = isLandscape,
            showMockNotice = showMockNotice,
            onEvent = viewModel::onEvent,
            onForgotPassword = onForgotPassword,
            onGoToRegister = onGoToRegister,
        )
    }
}
