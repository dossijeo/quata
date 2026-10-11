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
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.common.AppDispatchers
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.AuthSession
import com.quata.core.model.CountryPrefix
import com.quata.feature.auth.domain.RegisterAccountRequest
import com.quata.feature.auth.domain.RegisterRepository
import com.quata.feature.auth.presentation.register.RegisterEffect
import com.quata.feature.auth.presentation.register.RegisterForm
import com.quata.feature.auth.presentation.register.RegisterFormStrings
import com.quata.feature.auth.presentation.register.RegisterTestTags
import com.quata.feature.auth.presentation.register.RegisterUiEvent
import com.quata.feature.auth.presentation.register.RegisterViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import kotlin.test.assertEquals

/** Synthetic transport around the real shared registration form and ViewModel. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class RegisterFailureRetryUiTest {
    @Test
    fun failedSubmitKeepsFieldsAndOneExplicitRetryRegisters() = runComposeUiTest {
        val scheduler = TestCoroutineScheduler()
        val repository = SequencedRegisterRepository()
        val viewModel = RegisterViewModel(
            repository = repository,
            dispatchers = AppDispatchers(default = StandardTestDispatcher(scheduler)),
        )
        val catalog = AuthCatalog.copy(AuthCatalogLocale.Spanish)
        val expected = RegisterAccountRequest(
            displayName = "Synthetic User",
            neighborhood = "Synthetic District",
            countryCode = "240",
            phone = "799000000000",
            password = "Synthetic-password-7!",
            secretQuestion = "madre",
            secretAnswer = "Synthetic answer",
        )
        var successes = 0
        try {
            viewModel.onEvent(RegisterUiEvent.DisplayNameChanged(expected.displayName))
            viewModel.onEvent(RegisterUiEvent.NeighborhoodChanged(expected.neighborhood))
            viewModel.onEvent(RegisterUiEvent.CountryCodeChanged(expected.countryCode))
            viewModel.onEvent(RegisterUiEvent.PhoneChanged(expected.phone))
            viewModel.onEvent(RegisterUiEvent.PasswordChanged(expected.password))
            viewModel.onEvent(RegisterUiEvent.SecretQuestionChanged(expected.secretQuestion))
            viewModel.onEvent(RegisterUiEvent.SecretAnswerChanged(expected.secretAnswer))

            setContent {
                val state by viewModel.uiState.collectAsState()
                LaunchedEffect(viewModel) {
                    viewModel.effects.collect { if (it is RegisterEffect.Success) successes += 1 }
                }
                QuataTheme {
                    Column {
                        RegisterForm(
                            state = state,
                            prefixes = listOf(CountryPrefix("240", "+240")),
                            secretQuestions = catalog.secretQuestions,
                            strings = RegisterFormStrings(
                                displayName = catalog.register.displayName,
                                neighborhood = catalog.register.neighborhood,
                                phone = catalog.login.phone,
                                password = catalog.login.password,
                                secretAnswer = catalog.register.secretAnswer,
                                searchPrefix = catalog.login.searchPrefix,
                                creating = catalog.register.creating,
                                createAccount = catalog.register.createAccount,
                                back = catalog.register.back,
                            ),
                            isLandscape = false,
                            onEvent = viewModel::onEvent,
                            onBack = {},
                        )
                    }
                }
            }

            onNodeWithTag(RegisterTestTags.Submit).performClick()
            viewModel.onEvent(RegisterUiEvent.Submit)
            scheduler.runCurrent()

            onNodeWithTag(RegisterTestTags.Submit).assertIsNotEnabled()
            assertEquals(listOf(expected), repository.calls)

            repository.results.removeFirst().complete(
                Result.failure(IllegalStateException("synthetic-registration-rejected")),
            )
            scheduler.runCurrent()

            onNodeWithTag(RegisterTestTags.Error)
                .assertTextEquals("synthetic-registration-rejected")
            onNodeWithTag(RegisterTestTags.Submit).assertIsEnabled()
            assertEquals(expected, viewModel.uiState.value.toRequest())
            assertEquals(0, successes)

            onNodeWithTag(RegisterTestTags.Submit).performClick()
            scheduler.runCurrent()
            assertEquals(listOf(expected, expected), repository.calls)

            repository.results.removeFirst().complete(Result.success(session()))
            scheduler.runCurrent()

            assertEquals(1, successes)
            assertEquals(2, repository.calls.size)
        } finally {
            viewModel.close()
        }
    }

    private class SequencedRegisterRepository : RegisterRepository {
        val calls = mutableListOf<RegisterAccountRequest>()
        val results = ArrayDeque<CompletableDeferred<Result<AuthSession>>>().apply {
            add(CompletableDeferred())
            add(CompletableDeferred())
        }

        override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> {
            calls += request
            return results.first().await()
        }
    }
}

private fun com.quata.feature.auth.presentation.register.RegisterUiState.toRequest() =
    RegisterAccountRequest(
        displayName = displayName,
        neighborhood = neighborhood,
        countryCode = countryCode,
        phone = phone,
        password = password,
        secretQuestion = secretQuestion,
        secretAnswer = secretAnswer,
    )

private fun session() = AuthSession(
    token = "synthetic-access-token",
    userId = "synthetic-profile-id",
    authUserId = "synthetic-auth-user-id",
    accessToken = "synthetic-access-token",
    refreshToken = "synthetic-refresh-token",
    expiresAt = 4_102_444_800,
    email = "synthetic@example.test",
    displayName = "Synthetic User",
)
