package com.quata.feature.auth.presentation.register

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.profile.data.authCatalogForLanguage
import com.quata.feature.profile.data.countryPrefixOptionsForLanguage

/** Android launcher for the shared registration hierarchy. */
@Composable
fun RegisterScreen(
    padding: PaddingValues,
    authRepository: AuthRepository,
    onBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
) {
    val language = LocalConfiguration.current.locales[0].language
    val prefixes = remember(language) { countryPrefixOptionsForLanguage(language) }
    val catalog = remember(language) { authCatalogForLanguage(language) }
    RegisterScreenHost(
        padding = padding,
        repository = authRepository,
        catalog = catalog,
        prefixes = prefixes,
        onBack = onBack,
        onRegisterSuccess = onRegisterSuccess,
    )
}
