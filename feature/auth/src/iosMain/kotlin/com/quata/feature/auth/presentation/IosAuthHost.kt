package com.quata.feature.auth.presentation

import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.domain.LogoutUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS composition boundary for the shared Auth product roots.
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

/**
 * Swift-safe asynchronous boundary for the shared logout use case.
 *
 * The UIKit launcher owns the transition back to its public Feed, while this adapter keeps the
 * actual session operation in Kotlin. In particular, [AuthRepository.logout] is the single
 * operation that attempts the remote Supabase sign-out and always clears the Keychain-backed
 * session locally.
 */
class IosAuthLogoutHandler(repository: AuthRepository) {
    private val logoutUseCase = LogoutUseCase(repository)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Completion is invoked even if an unexpected transport/storage failure occurs. */
    fun logout(onCompleted: () -> Unit) {
        scope.launch {
            try {
                logoutUseCase()
            } finally {
                onCompleted()
            }
        }
    }
}

/** Stable Swift-facing factory without exposing a suspend function across the UIKit boundary. */
fun createIosAuthLogoutHandler(repository: AuthRepository): IosAuthLogoutHandler =
    IosAuthLogoutHandler(repository)

/** Stable Swift-exported UIViewController factory backed by common Auth ViewModels and Compose. */
fun QuataAuthViewController(dependencies: IosAuthHostDependencies): UIViewController = ComposeUIViewController {
    val catalog = AuthCatalog.copy(dependencies.locale)
    QuataTheme {
        AuthProductHostContent(
            repository = dependencies.repository,
            catalog = catalog,
            prefixes = AuthCatalog.countryPrefixes(dependencies.locale),
            onAuthenticated = dependencies.onLoginSuccess,
        )
    }
}
