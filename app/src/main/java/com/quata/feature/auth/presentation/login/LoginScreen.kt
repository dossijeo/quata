package com.quata.feature.auth.presentation.login

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.quata.core.config.AppConfig
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.profile.data.countryPrefixOptions
import com.quata.feature.profile.data.authCatalog

@Composable
fun LoginScreen(
    padding: PaddingValues,
    authRepository: AuthRepository,
    onGoToRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val prefixes = remember(context) { context.countryPrefixOptions() }
    val catalog = remember(context) { context.authCatalog() }
    LoginScreenHost(
        padding = padding,
        repository = authRepository,
        catalog = catalog,
        prefixes = prefixes,
        showMockNotice = AppConfig.USE_MOCK_BACKEND,
        onGoToRegister = onGoToRegister,
        onForgotPassword = onForgotPassword,
        onLoginSuccess = onLoginSuccess,
    )
}
