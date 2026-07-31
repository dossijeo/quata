package com.quata.feature.auth.presentation

import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.ui.components.QuataAuthRequiredDialogContent
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
    val initialDestination: AuthProductDestination,
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
    initialDestination = AuthProductDestination.Login,
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
            initialDestination = dependencies.initialDestination,
            onAuthenticated = dependencies.onLoginSuccess,
        )
    }
}

/** The registration choice is still the shared Auth product host; UIKit only selects its entry. */
fun QuataRegistrationViewController(dependencies: IosAuthHostDependencies): UIViewController = ComposeUIViewController {
    val catalog = AuthCatalog.copy(dependencies.locale)
    QuataTheme {
        AuthProductHostContent(
            repository = dependencies.repository,
            catalog = catalog,
            prefixes = AuthCatalog.countryPrefixes(dependencies.locale),
            initialDestination = AuthProductDestination.Register,
            onAuthenticated = dependencies.onLoginSuccess,
        )
    }
}

/**
 * Thin iOS entry point for the Android-equivalent capability prompt.  This deliberately hosts
 * the common Material dialog rather than recreating its copy or buttons in UIKit.
 */
fun QuataAuthRequiredDialogViewController(
    languageCode: String,
    onDismiss: () -> Unit,
    onCreateAccount: () -> Unit,
    onLogin: () -> Unit,
): UIViewController = ComposeUIViewController {
    val spanish = languageCode.lowercase().startsWith("es")
    QuataTheme {
        QuataAuthRequiredDialogContent(
            title = if (spanish) "Únete a QÜATA para participar" else "Join QÜATA to participate",
            intro = if (spanish) "Puedes explorar publicaciones libremente, pero para:" else "You can explore posts freely, but to:",
            requirements = if (spanish) listOf(
                "✓ Enviar mensajes", "✓ Comentar publicaciones", "✓ Crear contenido",
                "✓ Seguir comunidades", "✓ Configurar contactos SOS",
            ) else listOf(
                "✓ Send messages", "✓ Comment on posts", "✓ Create content",
                "✓ Follow communities", "✓ Configure SOS contacts",
            ),
            outro = if (spanish) "necesitas una cuenta." else "you need an account.",
            createAccountLabel = if (spanish) "Crear cuenta" else "Create account",
            loginLabel = if (spanish) "Ya tengo cuenta" else "I have an account",
            onDismiss = onDismiss,
            onCreateAccount = onCreateAccount,
            onLogin = onLogin,
        )
    }
}
