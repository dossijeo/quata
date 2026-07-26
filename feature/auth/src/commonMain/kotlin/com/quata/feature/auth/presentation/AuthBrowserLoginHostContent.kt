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
import com.quata.feature.auth.presentation.recovery.ForgotPasswordEffect
import com.quata.feature.auth.presentation.recovery.ForgotPasswordForm
import com.quata.feature.auth.presentation.recovery.ForgotPasswordFormStrings
import com.quata.feature.auth.presentation.recovery.ForgotPasswordViewModel
import com.quata.feature.auth.presentation.register.RegisterSecretQuestion
import com.quata.feature.auth.presentation.register.RegisterEffect
import com.quata.feature.auth.presentation.register.RegisterForm
import com.quata.feature.auth.presentation.register.RegisterFormStrings
import com.quata.feature.auth.presentation.register.RegisterViewModel

/** Host-neutral browser login orchestration; transports and post-login work are injected. */
@Composable
fun AuthBrowserLoginHostContent(
    repository: AuthRepository,
    prefixes: List<CountryPrefix>,
    strings: LoginFormStrings,
    subtitle: String,
    recoveryStrings: ForgotPasswordFormStrings,
    secretQuestions: List<RegisterSecretQuestion>,
    recoveryQuestionWaiting: String,
    recoveryQuestionLoading: String,
    passwordUpdatedMessage: String,
    registerStrings: RegisterFormStrings?,
    registerSubtitle: String?,
    registerUnavailableMessage: String?,
    runtimeConfigurationNotice: String? = null,
    onLoginSuccess: suspend () -> Unit,
) {
    val loginViewModel = remember(repository) { LoginViewModel(repository) }
    val recoveryViewModel = remember(repository) { ForgotPasswordViewModel(repository) }
    val registerViewModel = remember(repository) { RegisterViewModel(repository) }
    val loginState by loginViewModel.uiState.collectAsState()
    val recoveryState by recoveryViewModel.uiState.collectAsState()
    val registerState by registerViewModel.uiState.collectAsState()
    var destination by remember { mutableStateOf(AuthBrowserDestination.Login) }
    var notice by remember(runtimeConfigurationNotice) { mutableStateOf(runtimeConfigurationNotice) }
    DisposableEffect(loginViewModel, recoveryViewModel, registerViewModel) {
        onDispose {
            loginViewModel.close()
            recoveryViewModel.close()
            registerViewModel.close()
        }
    }
    LaunchedEffect(loginViewModel) { loginViewModel.effects.collect { if (it is LoginEffect.Success) onLoginSuccess() } }
    LaunchedEffect(registerViewModel) {
        registerViewModel.effects.collect { if (it is RegisterEffect.Success) onLoginSuccess() }
    }
    LaunchedEffect(recoveryViewModel) {
        recoveryViewModel.effects.collect {
            if (it is ForgotPasswordEffect.PasswordUpdated) {
                destination = AuthBrowserDestination.Login
                notice = passwordUpdatedMessage
            }
        }
    }
    val activeSubtitle = if (destination == AuthBrowserDestination.Register) registerSubtitle ?: subtitle else subtitle
    AuthScreenLayoutContent(PaddingValues(), activeSubtitle, portraitLogoSpacing = 22.dp) { isLandscape ->
        when (destination) {
            AuthBrowserDestination.Login -> LoginForm(
                state = loginState,
                prefixes = prefixes,
                strings = strings,
                isLandscape = isLandscape,
                showMockNotice = false,
                showRegistration = registerStrings != null || registerUnavailableMessage != null,
                onEvent = loginViewModel::onEvent,
                onForgotPassword = { destination = AuthBrowserDestination.Recovery; notice = null },
                onGoToRegister = {
                    if (registerStrings != null) {
                        destination = AuthBrowserDestination.Register
                        notice = null
                    } else {
                        notice = registerUnavailableMessage
                    }
                },
            )
            AuthBrowserDestination.Recovery -> ForgotPasswordForm(
                state = recoveryState,
                prefixes = prefixes,
                resolvedQuestion = when {
                    recoveryState.isLoadingQuestion -> recoveryQuestionLoading
                    recoveryState.secretQuestion.isBlank() -> recoveryQuestionWaiting
                    else -> secretQuestions.firstOrNull { it.value == recoveryState.secretQuestion }?.label
                        ?: recoveryState.secretQuestion
                },
                strings = recoveryStrings,
                isLandscape = isLandscape,
                onEvent = recoveryViewModel::onEvent,
                onBack = { destination = AuthBrowserDestination.Login },
            )
            AuthBrowserDestination.Register -> registerStrings?.let { stringsForRegister ->
                RegisterForm(
                    state = registerState,
                    prefixes = prefixes,
                    secretQuestions = secretQuestions,
                    strings = stringsForRegister,
                    isLandscape = isLandscape,
                    onEvent = registerViewModel::onEvent,
                    onBack = { destination = AuthBrowserDestination.Login; notice = null },
                )
            }
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private enum class AuthBrowserDestination { Login, Recovery, Register }
