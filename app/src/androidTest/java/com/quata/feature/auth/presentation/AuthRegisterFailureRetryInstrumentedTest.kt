package com.quata.feature.auth.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class AuthRegisterFailureRetryInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nativeRegisterFailurePreservesAllFieldsAndExplicitRetryAuthenticates() {
        val repository = FailFirstRegisterRepository()
        val authenticated = AtomicInteger(0)
        compose.setContent {
            QuataTheme {
                AuthProductHostContent(
                    repository = repository,
                    catalog = AuthCatalog.copy(AuthCatalogLocale.Spanish),
                    prefixes = listOf(CountryPrefix("240", "+240")),
                    initialDestination = AuthProductDestination.Register,
                    onAuthenticated = { authenticated.incrementAndGet() },
                )
            }
        }

        enter(FailFirstRegisterRepository.DisplayName, "auth.register.display-name")
        enter(FailFirstRegisterRepository.Neighborhood, "auth.register.neighborhood")
        compose.onNodeWithTag("auth.register.country-prefix", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag("auth.register.country-prefix.option.240", useUnmergedTree = true)
            .performClick()
        enter(FailFirstRegisterRepository.Phone, "auth.register.phone.input")
        enter(FailFirstRegisterRepository.Password, "auth.register.password")
        compose.onNodeWithTag("auth.register.secret-question", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag("auth.register.secret-question.option.madre", useUnmergedTree = true)
            .performClick()
        enter(FailFirstRegisterRepository.SecretAnswer, "auth.register.secret-answer")

        compose.onNodeWithTag("auth.register.submit", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.waitUntil(5_000) { repository.callCount == 1 }

        compose.onNodeWithTag("auth.register.error", useUnmergedTree = true)
            .assertTextContains("synthetic-registration-rejected")
        compose.onNodeWithTag("auth.register.display-name", useUnmergedTree = true)
            .assertTextContains(FailFirstRegisterRepository.DisplayName)
        compose.onNodeWithTag("auth.register.neighborhood", useUnmergedTree = true)
            .assertTextContains(FailFirstRegisterRepository.Neighborhood)
        compose.onNodeWithTag("auth.register.phone.input", useUnmergedTree = true)
            .assertTextContains(FailFirstRegisterRepository.Phone)
        compose.onNodeWithTag("auth.register.secret-answer", useUnmergedTree = true)
            .assertTextContains(FailFirstRegisterRepository.SecretAnswer)

        compose.onNodeWithTag("auth.register.submit", useUnmergedTree = true)
            .assertIsEnabled()
            .performClick()
        compose.waitUntil(5_000) { authenticated.get() == 1 }

        val calls = repository.callsSnapshot()
        assertEquals(2, calls.size)
        assertEquals(calls[0], calls[1])
        assertEquals(FailFirstRegisterRepository.Expected, calls[1])
        assertEquals(1, authenticated.get())
    }

    private fun enter(value: String, tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performScrollTo()
            .performTextInput(value)
    }
}

private class FailFirstRegisterRepository : AuthRepository {
    private val callsLock = Any()
    private val calls = mutableListOf<RegisterAccountRequest>()

    @Volatile
    var callCount: Int = 0
        private set

    fun callsSnapshot(): List<RegisterAccountRequest> = synchronized(callsLock) { calls.toList() }

    override suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession> = unsupported()

    override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> {
        val (attemptNumber, requestRepeatedExactly) = synchronized(callsLock) {
            calls += request
            callCount = calls.size
            calls.size to (calls.size < 2 || calls.last() == calls.first())
        }
        return if (attemptNumber == 1) {
            Result.failure(IllegalStateException("synthetic-registration-rejected"))
        } else if (requestRepeatedExactly) {
            Result.success(registerFixtureSession())
        } else {
            Result.failure(IllegalStateException("synthetic-registration-request-changed"))
        }
    }

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
        const val DisplayName = "Synthetic User"
        const val Neighborhood = "Synthetic District"
        const val Phone = "799000000000"
        const val Password = "Synthetic-password-7!"
        const val SecretAnswer = "Synthetic answer"
        val Expected = RegisterAccountRequest(
            displayName = DisplayName,
            neighborhood = Neighborhood,
            countryCode = "240",
            phone = Phone,
            password = Password,
            secretQuestion = "madre",
            secretAnswer = SecretAnswer,
        )
    }
}

private fun registerFixtureSession() = AuthSession(
    token = "synthetic-access-token",
    userId = "synthetic-profile-id",
    authUserId = "synthetic-auth-user-id",
    accessToken = "synthetic-access-token",
    refreshToken = "synthetic-refresh-token",
    expiresAt = 4_102_444_800,
    email = "synthetic@example.test",
    displayName = "Synthetic User",
)
