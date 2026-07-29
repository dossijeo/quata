package com.quata.feature.auth.presentation

import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.AuthSession
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.domain.LogoutUseCase
import com.quata.feature.auth.domain.PasswordRecoveryQuestion
import com.quata.feature.auth.domain.RegisterAccountRequest
import com.quata.feature.auth.presentation.register.RegisterFormStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    val registrationEnabled: Boolean,
    val onLoginSuccess: () -> Unit,
)

/** Swift-facing factory without default Kotlin constructor arguments. */
fun createIosAuthHostDependencies(
    repository: AuthRepository,
    languageCode: String,
    registrationEnabled: Boolean,
    onLoginSuccess: () -> Unit,
): IosAuthHostDependencies = IosAuthHostDependencies(
    repository = repository,
    locale = AuthCatalogLocale.fromLanguage(languageCode),
    registrationEnabled = registrationEnabled,
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

/**
 * Hermetic XCTest-only Auth surface.
 *
 * Swift reaches this factory exclusively through the exact `-quata-ui-test-fixture auth`
 * launch argument. It exercises the production Compose Auth host and its UIKit input bridge,
 * but the repository rejects every operation locally and never reads runtime configuration,
 * Keychain, network, or credentials.
 */
fun QuataIosAuthUiTestFixtureViewController(): UIViewController = QuataAuthViewController(
    dependencies = IosAuthHostDependencies(
        repository = IosAuthUiTestFixtureRepository,
        locale = AuthCatalogLocale.English,
        registrationEnabled = false,
        onLoginSuccess = {},
    ),
)

private object IosAuthUiTestFixtureRepository : AuthRepository {
    override suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession> = fixtureFailure()
    override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> = fixtureFailure()
    override suspend fun getPasswordRecoveryQuestion(countryCode: String, phone: String): Result<PasswordRecoveryQuestion?> = fixtureFailure()
    override suspend fun resetPassword(countryCode: String, phone: String, secretAnswer: String, newPassword: String) = fixtureFailure<Unit>()
    override suspend fun deactivateAccount(password: String) = fixtureFailure<Unit>()
    override suspend fun deleteAccountData(password: String) = fixtureFailure<Unit>()
    override suspend fun logout() = Unit
}

private fun <T> fixtureFailure(): Result<T> = Result.failure(IllegalStateException("ios_auth_ui_test_fixture"))

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
            registerStrings = if (dependencies.registrationEnabled) RegisterFormStrings(
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
            registerSubtitle = catalog.register.title.takeIf { dependencies.registrationEnabled },
            registerUnavailableMessage = null,
            phoneInputAccessibilityOverlay = { value, onValueChange, onFocusChanged, modifier ->
                IosNativeAuthAccessibilityInput(
                    value = value,
                    onValueChange = onValueChange,
                    onFocusChanged = onFocusChanged,
                    identifier = "auth.phone.input",
                    label = catalog.login.phone,
                    password = false,
                    modifier = modifier,
                )
            },
            passwordInputAccessibilityOverlay = { value, onValueChange, onFocusChanged, modifier ->
                IosNativeAuthAccessibilityInput(
                    value = value,
                    onValueChange = onValueChange,
                    onFocusChanged = onFocusChanged,
                    identifier = "auth.password.input",
                    label = catalog.login.password,
                    password = true,
                    modifier = modifier,
                )
            },
            onLoginSuccess = { dependencies.onLoginSuccess() },
        )
    }
}
