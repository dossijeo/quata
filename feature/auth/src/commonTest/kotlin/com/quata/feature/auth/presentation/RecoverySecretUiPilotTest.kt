package com.quata.feature.auth.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import com.quata.core.common.AppDispatchers
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.AuthSession
import com.quata.core.model.CountryPrefix
import com.quata.feature.auth.domain.*
import com.quata.feature.auth.presentation.recovery.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import kotlin.test.assertEquals

/** Synthetic form + real ViewModel. No network, credentials or platform transport. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class RecoverySecretUiPilotTest {
    @Test
    fun inputsValidationPendingAndFailureUseTheRealForm() = runComposeUiTest {
        val scheduler = TestCoroutineScheduler()
        val repository = LocalRecoveryRepository()
        val viewModel = ForgotPasswordViewModel(repository, AppDispatchers(default = StandardTestDispatcher(scheduler)))
        val catalog = AuthCatalog.copy(AuthCatalogLocale.Spanish)
        var backCalls = 0
        try {
            setContent {
                val state by viewModel.uiState.collectAsState()
                QuataTheme {
                    Column {
                        ForgotPasswordForm(
                            state = state,
                            prefixes = listOf(CountryPrefix("240", "+240")),
                            resolvedQuestion = catalog.secretQuestions.firstOrNull { it.value == state.secretQuestion }?.label.orEmpty(),
                            strings = catalog.recovery,
                            isLandscape = false,
                            onEvent = viewModel::onEvent,
                            onBack = { backCalls++ },
                        )
                    }
                }
            }
            onNodeWithTag("auth.recovery.submit").performClick()
            scheduler.runCurrent()
            onNodeWithTag("auth.recovery.error").assertTextEquals("Introduce un teléfono registrado")
            assertEquals(null, repository.resetInputs)
            onNodeWithTag("auth.recovery.phone").performTextInput("799000000000")
            scheduler.advanceUntilIdle()
            onNodeWithTag("auth.recovery.question").assertTextEquals("¿Cómo se llama tu madre?")
            onNodeWithTag("auth.recovery.secret-answer").performTextInput("pilot-only-answer")
            onNodeWithTag("auth.recovery.new-password").performTextInput("Pilot-only-password-7!")
            onNodeWithTag("auth.recovery.submit").performClick()
            scheduler.runCurrent()
            onNodeWithTag("auth.recovery.submit").assertIsNotEnabled()
            assertEquals(listOf("240", "799000000000", "pilot-only-answer", "Pilot-only-password-7!"), repository.resetInputs)
            repository.resetResult.complete(Result.failure(IllegalStateException("pilot-local-rejection")))
            scheduler.runCurrent()
            onNodeWithTag("auth.recovery.error").assertTextEquals("pilot-local-rejection")
            onNodeWithTag("auth.recovery.submit").assertIsEnabled()
            onNodeWithTag("auth.recovery.back").performClick()
            assertEquals(1, backCalls)
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun commonNavigationOpensRecoveryAndReturnsToLogin() = runComposeUiTest {
        setContent {
            QuataTheme {
                AuthProductHostContent(
                    repository = LocalRecoveryRepository(),
                    catalog = AuthCatalog.copy(AuthCatalogLocale.Spanish),
                    prefixes = listOf(CountryPrefix("240", "+240")),
                    onAuthenticated = { error("pilot_must_not_authenticate") },
                )
            }
        }
        onNodeWithTag("auth.forgot-password").performClick()
        onNodeWithTag("auth.recovery.root").assertExists()
        onNodeWithTag("auth.recovery.back").performClick()
        onNodeWithTag("auth.submit").assertExists()
        onNodeWithTag("auth.recovery.root").assertDoesNotExist()
    }

    private class LocalRecoveryRepository : AuthRepository {
        val resetResult = CompletableDeferred<Result<Unit>>()
        var resetInputs: List<String>? = null
        override suspend fun getPasswordRecoveryQuestion(countryCode: String, phone: String) =
            Result.success(PasswordRecoveryQuestion(secretQuestion = "madre"))
        override suspend fun resetPassword(countryCode: String, phone: String, secretAnswer: String, newPassword: String): Result<Unit> {
            resetInputs = listOf(countryCode, phone, secretAnswer, newPassword)
            return resetResult.await()
        }
        override suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession> = error("unused_local_boundary")
        override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> = error("unused_local_boundary")
        override suspend fun deactivateAccount(password: String): Result<Unit> = error("unused_local_boundary")
        override suspend fun deleteAccountData(password: String): Result<Unit> = error("unused_local_boundary")
        override suspend fun logout() = Unit
    }
}
