package com.quata.feature.auth.presentation

import com.quata.core.model.AuthSession
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.domain.PasswordRecoveryQuestion
import com.quata.feature.auth.domain.RegisterAccountRequest
import platform.UIKit.UIViewController

/**
 * Deterministic iOS UI-test composition for the shared Auth surface.
 *
 * This is intentionally not a production dependency factory. Every operation returns a local
 * failure, so mounting the real Compose form cannot perform an external action or create session
 * state. It exists only behind the launch argument gate owned by the UIKit application delegate.
 */
fun QuataAuthLaunchFixtureViewController(): UIViewController = fixtureViewController(
    initialDestination = AuthProductDestination.Login,
)

/** Swift-safe fixture factory for deterministic launch coverage of Auth destinations. */
fun QuataAuthLaunchFixtureViewControllerForDestination(destination: String): UIViewController = fixtureViewController(
    initialDestination = when (destination.lowercase()) {
        "register" -> AuthProductDestination.Register
        "recovery" -> AuthProductDestination.Recovery
        else -> AuthProductDestination.Login
    },
)

fun QuataAuthLaunchLegalEvidenceViewControllerForDestination(
    destination: String,
    languageCode: String?,
    onOpened: (String) -> Unit,
): UIViewController = fixtureViewController(
    initialDestination = when (destination.lowercase()) {
        "register" -> AuthProductDestination.Register
        "recovery" -> AuthProductDestination.Recovery
        else -> AuthProductDestination.Login
    },
    locale = AuthCatalogLocale.fromLanguage(languageCode),
    documentOpener = RecordingIosAuthLaunchFixtureDocumentOpener(onOpened),
)

/**
 * Deterministic native-host fixture for the Auth failure/retry surface.
 *
 * The first mutation fails and the second succeeds only when it repeats the exact first request.
 * Callbacks expose operation counters to XCTest without exporting credentials or product state.
 */
fun QuataAuthFailureRetryFixtureViewControllerForDestination(
    destination: String,
    languageCode: String?,
    onAttempt: (String) -> Unit,
    onSuccess: (String) -> Unit,
): UIViewController {
    val initialDestination = destination.toAuthProductDestination()
    return QuataAuthViewController(
        dependencies = IosAuthHostDependencies(
            repository = IosAuthFailureRetryFixtureRepository(onAttempt, onSuccess),
            locale = AuthCatalogLocale.fromLanguage(languageCode),
            initialDestination = initialDestination,
            documentOpener = IosAuthLaunchFixtureDocumentOpener,
            onLoginSuccess = {
                val operation = when (initialDestination) {
                    AuthProductDestination.Register -> "register"
                    else -> "login"
                }
                onSuccess("$operation-success")
            },
        ),
    )
}

private fun String.toAuthProductDestination(): AuthProductDestination = when (lowercase()) {
    "register" -> AuthProductDestination.Register
    "recovery" -> AuthProductDestination.Recovery
    else -> AuthProductDestination.Login
}

private fun fixtureViewController(
    initialDestination: AuthProductDestination,
    locale: AuthCatalogLocale = AuthCatalogLocale.English,
    documentOpener: DocumentOpenService = IosAuthLaunchFixtureDocumentOpener,
): UIViewController = QuataAuthViewController(
    dependencies = IosAuthHostDependencies(
        repository = IosAuthLaunchFixtureRepository(),
        locale = locale,
        initialDestination = initialDestination,
        documentOpener = documentOpener,
        onLoginSuccess = {},
    ),
)

private object IosAuthLaunchFixtureDocumentOpener : DocumentOpenService {
    override suspend fun open(file: PlatformFile): PlatformResult<Unit> = PlatformResult.Success(Unit)
}

private class RecordingIosAuthLaunchFixtureDocumentOpener(
    private val onOpened: (String) -> Unit,
) : DocumentOpenService {
    override suspend fun open(file: PlatformFile): PlatformResult<Unit> {
        onOpened(file.displayName.orEmpty())
        return PlatformResult.Success(Unit)
    }
}

private class IosAuthLaunchFixtureRepository : AuthRepository {
    private fun <T> unavailable(): Result<T> = Result.failure(IllegalStateException("fixture_auth_unavailable"))

    override suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession> = unavailable()

    override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> = unavailable()

    override suspend fun getPasswordRecoveryQuestion(countryCode: String, phone: String): Result<PasswordRecoveryQuestion?> = unavailable()

    override suspend fun resetPassword(
        countryCode: String,
        phone: String,
        secretAnswer: String,
        newPassword: String,
    ): Result<Unit> = unavailable()

    override suspend fun deactivateAccount(password: String): Result<Unit> = unavailable()

    override suspend fun deleteAccountData(password: String): Result<Unit> = unavailable()

    override suspend fun logout() = Unit
}

private class IosAuthFailureRetryFixtureRepository(
    private val onAttempt: (String) -> Unit,
    private val onSuccess: (String) -> Unit,
) : AuthRepository {
    private var firstLogin: LoginFixtureRequest? = null
    private var firstRegister: RegisterAccountRequest? = null
    private var firstRecovery: RecoveryFixtureRequest? = null

    override suspend fun login(
        countryCode: String,
        phone: String,
        password: String,
    ): Result<AuthSession> {
        val request = LoginFixtureRequest(countryCode, phone, password)
        val previous = firstLogin
        val attempt = if (previous == null) 1 else 2
        onAttempt("login-attempt-$attempt")
        if (previous == null) {
            firstLogin = request
            return rejected("synthetic-login-rejected")
        }
        if (request != previous) return rejected("synthetic-login-request-changed")
        return Result.success(fixtureSession())
    }

    override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> {
        val previous = firstRegister
        val attempt = if (previous == null) 1 else 2
        onAttempt("register-attempt-$attempt")
        if (previous == null) {
            firstRegister = request
            return rejected("synthetic-registration-rejected")
        }
        if (request != previous) return rejected("synthetic-registration-request-changed")
        return Result.success(fixtureSession())
    }

    override suspend fun getPasswordRecoveryQuestion(
        countryCode: String,
        phone: String,
    ): Result<PasswordRecoveryQuestion?> = Result.success(
        PasswordRecoveryQuestion(secretQuestion = "Nombre de tu primer barrio"),
    )

    override suspend fun resetPassword(
        countryCode: String,
        phone: String,
        secretAnswer: String,
        newPassword: String,
    ): Result<Unit> {
        val request = RecoveryFixtureRequest(countryCode, phone, secretAnswer, newPassword)
        val previous = firstRecovery
        val attempt = if (previous == null) 1 else 2
        onAttempt("recovery-attempt-$attempt")
        if (previous == null) {
            firstRecovery = request
            return rejected("synthetic-recovery-rejected")
        }
        if (request != previous) return rejected("synthetic-recovery-request-changed")
        onSuccess("recovery-success")
        return Result.success(Unit)
    }

    override suspend fun deactivateAccount(password: String): Result<Unit> = rejected("fixture_not_supported")

    override suspend fun deleteAccountData(password: String): Result<Unit> = rejected("fixture_not_supported")

    override suspend fun logout() = Unit

    private fun <T> rejected(message: String): Result<T> = Result.failure(IllegalStateException(message))
}

private data class LoginFixtureRequest(
    val countryCode: String,
    val phone: String,
    val password: String,
)

private data class RecoveryFixtureRequest(
    val countryCode: String,
    val phone: String,
    val secretAnswer: String,
    val newPassword: String,
)

private fun fixtureSession() = AuthSession(
    token = "synthetic-access-token",
    userId = "synthetic-profile-id",
    authUserId = "synthetic-auth-user-id",
    accessToken = "synthetic-access-token",
    refreshToken = "synthetic-refresh-token",
    expiresAt = 4_102_444_800,
    email = "synthetic@example.test",
    displayName = "Synthetic User",
)
