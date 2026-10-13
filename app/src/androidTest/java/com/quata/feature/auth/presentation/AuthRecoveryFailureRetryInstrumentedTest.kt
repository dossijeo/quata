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
import com.quata.feature.auth.presentation.recovery.ForgotPasswordTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthRecoveryFailureRetryInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nativeRecoveryFailurePreservesInputsAndExplicitRetryReturnsToLogin() {
        val repository = FailFirstRecoveryRepository()
        compose.setContent {
            QuataTheme {
                AuthProductHostContent(
                    repository = repository,
                    catalog = AuthCatalog.copy(AuthCatalogLocale.Spanish),
                    prefixes = listOf(CountryPrefix("240", "+240")),
                    initialDestination = AuthProductDestination.Recovery,
                    onAuthenticated = {},
                )
            }
        }

        compose.onNodeWithTag(ForgotPasswordTestTags.Phone, useUnmergedTree = true)
            .performTextInput(FailFirstRecoveryRepository.Phone)
        compose.waitUntil(5_000) { repository.questionReads == 1 }
        compose.onNodeWithTag(ForgotPasswordTestTags.Question, useUnmergedTree = true)
            .assertTextContains("primer barrio", substring = true)
        compose.onNodeWithTag(ForgotPasswordTestTags.SecretAnswer, useUnmergedTree = true)
            .performTextInput(FailFirstRecoveryRepository.SecretAnswer)
        compose.onNodeWithTag(ForgotPasswordTestTags.NewPassword, useUnmergedTree = true)
            .performTextInput(FailFirstRecoveryRepository.NewPassword)

        compose.onNodeWithTag(ForgotPasswordTestTags.Submit, useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.waitUntil(5_000) { repository.callCount == 1 }

        compose.onNodeWithTag(ForgotPasswordTestTags.Error, useUnmergedTree = true)
            .assertTextContains("synthetic-recovery-rejected")
        compose.onNodeWithTag(ForgotPasswordTestTags.Phone, useUnmergedTree = true)
            .assertTextContains(FailFirstRecoveryRepository.Phone)
        compose.onNodeWithTag(ForgotPasswordTestTags.SecretAnswer, useUnmergedTree = true)
            .assertTextContains(FailFirstRecoveryRepository.SecretAnswer)

        compose.onNodeWithTag(ForgotPasswordTestTags.Submit, useUnmergedTree = true)
            .assertIsEnabled()
            .performClick()
        compose.waitUntil(5_000) { repository.callCount == 2 }
        compose.onNodeWithTag("auth.submit", useUnmergedTree = true)
            .assertIsEnabled()

        val calls = repository.callsSnapshot()
        assertEquals(2, calls.size)
        assertEquals(calls[0], calls[1])
        assertEquals(FailFirstRecoveryRepository.Expected, calls[1])
    }
}

private class FailFirstRecoveryRepository : AuthRepository {
    private val callsLock = Any()
    private val calls = mutableListOf<RecoveryFixtureRequest>()

    @Volatile
    var questionReads: Int = 0
        private set

    @Volatile
    var callCount: Int = 0
        private set

    fun callsSnapshot(): List<RecoveryFixtureRequest> = synchronized(callsLock) { calls.toList() }

    override suspend fun getPasswordRecoveryQuestion(
        countryCode: String,
        phone: String,
    ): Result<PasswordRecoveryQuestion?> {
        questionReads += 1
        return Result.success(PasswordRecoveryQuestion(secretQuestion = "Nombre de tu primer barrio"))
    }

    override suspend fun resetPassword(
        countryCode: String,
        phone: String,
        secretAnswer: String,
        newPassword: String,
    ): Result<Unit> {
        val request = RecoveryFixtureRequest(countryCode, phone, secretAnswer, newPassword)
        val (attemptNumber, requestRepeatedExactly) = synchronized(callsLock) {
            calls += request
            callCount = calls.size
            calls.size to (calls.size < 2 || calls.last() == calls.first())
        }
        return if (attemptNumber == 1) {
            Result.failure(IllegalStateException("synthetic-recovery-rejected"))
        } else if (requestRepeatedExactly) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("synthetic-recovery-request-changed"))
        }
    }

    override suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession> = unsupported()

    override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> = unsupported()

    override suspend fun deactivateAccount(password: String): Result<Unit> = unsupported()

    override suspend fun deleteAccountData(password: String): Result<Unit> = unsupported()

    override suspend fun logout() = Unit

    private fun <T> unsupported(): Result<T> =
        Result.failure(UnsupportedOperationException("fixture_operation_not_used"))

    companion object {
        const val Phone = "799000000000"
        const val SecretAnswer = "Synthetic answer"
        const val NewPassword = "Synthetic-password-8!"
        val Expected = RecoveryFixtureRequest(
            countryCode = "240",
            phone = Phone,
            secretAnswer = SecretAnswer,
            newPassword = NewPassword,
        )
    }
}

private data class RecoveryFixtureRequest(
    val countryCode: String,
    val phone: String,
    val secretAnswer: String,
    val newPassword: String,
)
