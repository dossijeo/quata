package com.quata.feature.auth.presentation.register

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.profile.data.authCatalog
import com.quata.feature.profile.data.countryPrefixOptions

/** Android launcher for the shared registration hierarchy. */
@Composable
fun RegisterScreen(
    padding: PaddingValues,
    authRepository: AuthRepository,
    onBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val prefixes = remember(context) { context.countryPrefixOptions() }
    val catalog = remember(context) { context.authCatalog() }
    RegisterScreenHost(
        padding = padding,
        repository = authRepository,
        catalog = catalog,
        prefixes = prefixes,
        onBack = onBack,
        onRegisterSuccess = onRegisterSuccess,
    )
}
