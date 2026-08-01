package com.quata.feature.auth.presentation.recovery

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.profile.data.authCatalogForLanguage
import com.quata.feature.profile.data.countryPrefixOptionsForLanguage

/** Android feedback wrapper around the shared recovery host. */
@Composable
fun ForgotPasswordScreen(
    padding: PaddingValues,
    authRepository: AuthRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    val prefixes = remember(language) { countryPrefixOptionsForLanguage(language) }
    val catalog = remember(language) { authCatalogForLanguage(language) }
    ForgotPasswordScreenHost(
        padding = padding,
        repository = authRepository,
        catalog = catalog,
        prefixes = prefixes,
        onBack = onBack,
        onPasswordUpdated = {
            Toast.makeText(context, catalog.passwordUpdatedMessage, Toast.LENGTH_SHORT).show()
            onBack()
        },
    )
}
