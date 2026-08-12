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
