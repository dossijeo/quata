package com.quata.feature.auth.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.AuthSession
import com.quata.core.model.CountryPrefix
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.domain.PasswordRecoveryQuestion
import com.quata.feature.auth.domain.RegisterAccountRequest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AuthLoginFailureRetryInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nativeLoginFailurePreservesInputAndExplicitRetryAuthenticates() {
        val repository = FailFirstLoginRepository()
        val authenticated = AtomicInteger(0)
        compose.setContent {
            QuataTheme {
                AuthProductHostContent(
                    repository = repository,
                    catalog = AuthCatalog.copy(AuthCatalogLocale.Spanish),
                    prefixes = listOf(CountryPrefix("240", "+240")),
                    onAuthenticated = { authenticated.incrementAndGet() },
                )
            }
        }

        compose.onNodeWithTag("auth.phone.input", useUnmergedTree = true)
            .performTextInput(FailFirstLoginRepository.Phone)
        compose.onNodeWithTag("auth.password", useUnmergedTree = true)
            .performTextInput(FailFirstLoginRepository.Password)
        compose.onNodeWithTag("auth.submit", useUnmergedTree = true).performClick()

        compose.waitUntil(5_000) { repository.callCount == 1 }
        compose.onNodeWithTag("auth.error", useUnmergedTree = true)
            .assertTextContains("synthetic-login-rejected")
        compose.onNodeWithTag("auth.phone.input", useUnmergedTree = true)
            .assertTextContains(FailFirstLoginRepository.Phone)
        compose.onNodeWithTag("auth.submit", useUnmergedTree = true)
            .assertIsEnabled()
            .performClick()

        compose.waitUntil(5_000) { authenticated.get() == 1 }
        val calls = repository.callsSnapshot()
        assertEquals(2, calls.size)
        assertEquals(calls[0], calls[1])
    }
}

private class FailFirstLoginRepository : AuthRepository {
    private val callsLock = Any()
    private val calls = mutableListOf<LoginAttempt>()

    @Volatile
    var callCount: Int = 0
        private set

    fun callsSnapshot(): List<LoginAttempt> = synchronized(callsLock) { calls.toList() }

    override suspend fun login(
        countryCode: String,
        phone: String,
        password: String,
    ): Result<AuthSession> {
        val (attemptNumber, requestRepeatedExactly) = synchronized(callsLock) {
            calls += LoginAttempt(countryCode, phone, password)
            callCount = calls.size
            calls.size to (calls.size < 2 || calls.last() == calls.first())
        }
        return if (attemptNumber == 1) {
            Result.failure(IllegalStateException("synthetic-login-rejected"))
        } else if (requestRepeatedExactly) {
            Result.success(fixtureSession())
        } else {
            Result.failure(IllegalStateException("synthetic-login-request-changed"))
        }
    }

    override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> = unsupported()

    override suspend fun getPasswordRecoveryQuestion(
        countryCode: String,
        phone: String,
    ): Result<PasswordRecoveryQuestion?> = unsupported()

    override suspend fun resetPassword(
        countryCode: String,
        phone: String,
        secretAnswer: String,
        newPassword: String,
    ): Result<Unit> = unsupported()

    override suspend fun deactivateAccount(password: String): Result<Unit> = unsupported()

    override suspend fun deleteAccountData(password: String): Result<Unit> = unsupported()

    override suspend fun logout() = Unit

    private fun <T> unsupported(): Result<T> =
        Result.failure(UnsupportedOperationException("fixture_operation_not_used"))

    companion object {
        const val Phone = "799000000000"
        const val Password = "Synthetic-password-7!"
    }
}

private data class LoginAttempt(
    val countryCode: String,
    val phone: String,
    val password: String,
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
