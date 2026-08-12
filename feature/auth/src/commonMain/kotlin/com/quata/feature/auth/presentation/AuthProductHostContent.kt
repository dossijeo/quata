package com.quata.feature.auth.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quata.core.model.CountryPrefix
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.presentation.login.LoginScreenHost
import com.quata.feature.auth.presentation.recovery.ForgotPasswordScreenHost
import com.quata.feature.auth.presentation.register.RegisterScreenHost

/**
 * Minimal product navigation for the shared Auth screen roots.
 *
 * This owns only destination changes and delegates every visual surface to the common product
 * roots, keeping platform launchers free of form/layout orchestration.
 */
@Composable
fun AuthProductHostContent(
    repository: AuthRepository,
    catalog: AuthCatalogCopy,
    prefixes: List<CountryPrefix>,
    initialDestination: AuthProductDestination = AuthProductDestination.Login,
    registerLegalLinks: @Composable (() -> Unit)? = null,
    onAuthenticated: () -> Unit,
) {
    var destination by remember(initialDestination) { mutableStateOf(initialDestination) }
    LaunchedEffect(initialDestination) {
        destination = initialDestination
    }

    when (destination) {
        AuthProductDestination.Login -> LoginScreenHost(
            padding = PaddingValues(),
            repository = repository,
            catalog = catalog,
            prefixes = prefixes,
            showMockNotice = false,
            onGoToRegister = { destination = AuthProductDestination.Register },
            onForgotPassword = { destination = AuthProductDestination.Recovery },
            onLoginSuccess = onAuthenticated,
        )
        AuthProductDestination.Register -> RegisterScreenHost(
            padding = PaddingValues(),
            repository = repository,
            catalog = catalog,
            prefixes = prefixes,
            legalLinks = registerLegalLinks,
            onBack = { destination = AuthProductDestination.Login },
            onRegisterSuccess = onAuthenticated,
        )
        AuthProductDestination.Recovery -> ForgotPasswordScreenHost(
            padding = PaddingValues(),
            repository = repository,
            catalog = catalog,
            prefixes = prefixes,
            onBack = { destination = AuthProductDestination.Login },
            onPasswordUpdated = { destination = AuthProductDestination.Login },
        )
    }
}

enum class AuthProductDestination { Login, Register, Recovery }
