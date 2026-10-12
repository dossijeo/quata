package com.quata.feature.auth.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.common.AppDispatchers
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.CountryPrefix
import com.quata.feature.auth.domain.PasswordRecoveryQuestion
import com.quata.feature.auth.domain.PasswordRecoveryRepository
import com.quata.feature.auth.presentation.recovery.ForgotPasswordEffect
import com.quata.feature.auth.presentation.recovery.ForgotPasswordForm
import com.quata.feature.auth.presentation.recovery.ForgotPasswordTestTags
import com.quata.feature.auth.presentation.recovery.ForgotPasswordUiEvent
import com.quata.feature.auth.presentation.recovery.ForgotPasswordViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import kotlin.test.assertEquals

/** Synthetic transport around the real shared recovery form and ViewModel. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class RecoveryFailureRetryUiTest {
    @Test
    fun failedResetKeepsInputsAndOneExplicitRetryCompletes() = runComposeUiTest {
        val scheduler = TestCoroutineScheduler()
        val repository = SequencedRecoveryRepository()
        val viewModel = ForgotPasswordViewModel(
            repository = repository,
            dispatchers = AppDispatchers(default = StandardTestDispatcher(scheduler)),
        )
        val catalog = AuthCatalog.copy(AuthCatalogLocale.Spanish)
        val expected = RecoveryAttempt(
            countryCode = "240",
            phone = "799000000000",
            secretAnswer = "Synthetic answer",
            newPassword = "Synthetic-password-8!",
        )
        var successes = 0
        try {
            setContent {
                val state by viewModel.uiState.collectAsState()
                LaunchedEffect(viewModel) {
                    viewModel.effects.collect {
                        if (it is ForgotPasswordEffect.PasswordUpdated) successes += 1
                    }
                }
                QuataTheme {
                    Column {
                        ForgotPasswordForm(
                            state = state,
                            prefixes = listOf(CountryPrefix("240", "+240")),
                            resolvedQuestion = catalog.secretQuestions
                                .firstOrNull { it.value == state.secretQuestion }
                                ?.label
                                .orEmpty(),
                            strings = catalog.recovery,
                            isLandscape = false,
                            onEvent = viewModel::onEvent,
                            onBack = {},
                        )
                    }
                }
            }

            onNodeWithTag(ForgotPasswordTestTags.Phone).performTextInput(expected.phone)
            scheduler.advanceUntilIdle()
            onNodeWithTag(ForgotPasswordTestTags.Question)
                .assertTextEquals("¿Cómo se llama tu madre?")
            onNodeWithTag(ForgotPasswordTestTags.SecretAnswer)
                .performTextInput(expected.secretAnswer)
            onNodeWithTag(ForgotPasswordTestTags.NewPassword)
                .performTextInput(expected.newPassword)

            onNodeWithTag(ForgotPasswordTestTags.Submit).performClick()
            viewModel.onEvent(ForgotPasswordUiEvent.Submit)
            scheduler.runCurrent()

            onNodeWithTag(ForgotPasswordTestTags.Submit).assertIsNotEnabled()
            assertEquals(listOf(expected), repository.resetCalls)

            repository.resetResults.removeFirst().complete(
                Result.failure(IllegalStateException("synthetic-recovery-rejected")),
            )
            scheduler.runCurrent()

            onNodeWithTag(ForgotPasswordTestTags.Error)
                .assertTextEquals("synthetic-recovery-rejected")
            onNodeWithTag(ForgotPasswordTestTags.Submit).assertIsEnabled()
            assertEquals(expected.phone, viewModel.uiState.value.phone)
            assertEquals(expected.secretAnswer, viewModel.uiState.value.secretAnswer)
            assertEquals(expected.newPassword, viewModel.uiState.value.newPassword)
            assertEquals(0, successes)

            onNodeWithTag(ForgotPasswordTestTags.Submit).performClick()
            scheduler.runCurrent()
            assertEquals(listOf(expected, expected), repository.resetCalls)

            repository.resetResults.removeFirst().complete(Result.success(Unit))
            scheduler.runCurrent()

            assertEquals(1, successes)
            assertEquals(2, repository.resetCalls.size)
        } finally {
            viewModel.close()
        }
    }

    private class SequencedRecoveryRepository : PasswordRecoveryRepository {
        val resetCalls = mutableListOf<RecoveryAttempt>()
        val resetResults = ArrayDeque<CompletableDeferred<Result<Unit>>>().apply {
            add(CompletableDeferred())
            add(CompletableDeferred())
        }

        override suspend fun getPasswordRecoveryQuestion(
            countryCode: String,
            phone: String,
        ) = Result.success(PasswordRecoveryQuestion(secretQuestion = "madre"))

        override suspend fun resetPassword(
            countryCode: String,
            phone: String,
            secretAnswer: String,
            newPassword: String,
        ): Result<Unit> {
            resetCalls += RecoveryAttempt(countryCode, phone, secretAnswer, newPassword)
            return resetResults.first().await()
        }
    }
}

private data class RecoveryAttempt(
    val countryCode: String,
    val phone: String,
    val secretAnswer: String,
    val newPassword: String,
)
