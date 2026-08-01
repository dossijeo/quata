package com.quata.feature.auth.presentation.login

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.quata.core.config.AppConfig
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.profile.data.authCatalogForLanguage
import com.quata.feature.profile.data.countryPrefixOptionsForLanguage

@Composable
fun LoginScreen(
    padding: PaddingValues,
    authRepository: AuthRepository,
    onGoToRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val language = LocalConfiguration.current.locales[0].language
    val prefixes = remember(language) { countryPrefixOptionsForLanguage(language) }
    val catalog = remember(language) { authCatalogForLanguage(language) }
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
