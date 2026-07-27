package com.quata.feature.auth.presentation

import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.auth.domain.AuthRepository
import platform.UIKit.UIViewController

/**
 * iOS composition boundary for the shared Auth login form.
 *
 * The launcher supplies its authenticated repository and post-login handling. Shared Auth
 * strings, prefixes and secret-question labels come from [AuthCatalog], so the iOS host does
 * not copy Android resources or invent a platform-only catalogue.
 */
class IosAuthHostDependencies(
    val repository: AuthRepository,
    val locale: AuthCatalogLocale,
    val onLoginSuccess: () -> Unit,
)

/** Swift-facing factory without default Kotlin constructor arguments. */
fun createIosAuthHostDependencies(
    repository: AuthRepository,
    languageCode: String,
    onLoginSuccess: () -> Unit,
): IosAuthHostDependencies = IosAuthHostDependencies(
    repository = repository,
    locale = AuthCatalogLocale.fromLanguage(languageCode),
    onLoginSuccess = onLoginSuccess,
)

/** Stable Swift-exported UIViewController factory backed by common Auth ViewModels and Compose. */
fun QuataAuthViewController(dependencies: IosAuthHostDependencies): UIViewController = ComposeUIViewController {
    val catalog = AuthCatalog.copy(dependencies.locale)
    QuataTheme {
        AuthBrowserLoginHostContent(
            repository = dependencies.repository,
            prefixes = AuthCatalog.countryPrefixes(dependencies.locale),
            strings = catalog.login,
            subtitle = catalog.loginSubtitle,
            recoveryStrings = catalog.recovery,
            secretQuestions = catalog.secretQuestions,
            recoveryQuestionWaiting = catalog.recoveryQuestionWaiting,
            recoveryQuestionLoading = catalog.recoveryQuestionLoading,
            passwordUpdatedMessage = catalog.passwordUpdatedMessage,
            registerStrings = null,
            registerSubtitle = null,
            registerUnavailableMessage = null,
            onLoginSuccess = { dependencies.onLoginSuccess() },
        )
    }
}
