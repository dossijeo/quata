package com.quata.web

import androidx.compose.runtime.Composable
import com.quata.core.platform.PlatformServices
import com.quata.feature.auth.presentation.AuthBrowserLoginHostContent
import com.quata.feature.auth.presentation.AuthCatalog
import com.quata.feature.auth.presentation.AuthCatalogLocale
import com.quata.feature.auth.presentation.register.RegisterFormStrings

/** Web adapter retains browser-backed session persistence while sharing the app auth session. */
@Composable
fun WebLoginHost(
    platformServices: PlatformServices,
    runtimeConfiguration: WebRuntimeConfiguration,
    repository: WebAuthRepository,
    onLoginSuccess: () -> Unit,
) {
    val catalog = AuthCatalog.copy(AuthCatalogLocale.Spanish)
    AuthBrowserLoginHostContent(
        repository = repository,
        prefixes = AuthCatalog.countryPrefixes(AuthCatalogLocale.Spanish),
        strings = catalog.login,
        subtitle = "Quata Web",
        recoveryStrings = catalog.recovery,
        secretQuestions = catalog.secretQuestions,
        recoveryQuestionWaiting = catalog.recoveryQuestionWaiting,
        recoveryQuestionLoading = catalog.recoveryQuestionLoading,
        passwordUpdatedMessage = catalog.passwordUpdatedMessage,
        registerStrings = if (runtimeConfiguration.webRegistrationEnabled) RegisterFormStrings(
            displayName = catalog.register.displayName,
            neighborhood = catalog.register.neighborhood,
            phone = catalog.login.phone,
            password = catalog.login.password,
            secretAnswer = catalog.register.secretAnswer,
            searchPrefix = catalog.login.searchPrefix,
            creating = catalog.register.creating,
            createAccount = catalog.register.createAccount,
            back = catalog.register.back,
        ) else null,
        registerSubtitle = catalog.register.title,
        registerUnavailableMessage = if (runtimeConfiguration.webRegistrationEnabled) null else "El registro web aún no está habilitado.",
        runtimeConfigurationNotice = runtimeConfiguration.authRuntimeDiagnosticOrNull(),
    ) {
        platformServices.preferences.putString(WebSessionReadyKey, "true")
        onLoginSuccess()
    }
}

internal const val WebSessionReadyKey = "web.auth.session_ready"
