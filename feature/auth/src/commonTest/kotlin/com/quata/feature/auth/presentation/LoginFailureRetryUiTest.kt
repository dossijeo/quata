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
import com.quata.core.model.AuthSession
import com.quata.core.model.CountryPrefix
import com.quata.feature.auth.domain.LoginRepository
import com.quata.feature.auth.presentation.login.LoginEffect
import com.quata.feature.auth.presentation.login.LoginForm
import com.quata.feature.auth.presentation.login.LoginUiEvent
import com.quata.feature.auth.presentation.login.LoginViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import kotlin.test.assertEquals

/** Synthetic transport around the real shared form and ViewModel. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class LoginFailureRetryUiTest {
    @Test
    fun failedSubmitKeepsCredentialsAndOneExplicitRetryAuthenticates() = runComposeUiTest {
        val scheduler = TestCoroutineScheduler()
        val repository = SequencedLoginRepository()
        val viewModel = LoginViewModel(
            repository = repository,
            dispatchers = AppDispatchers(default = StandardTestDispatcher(scheduler)),
        )
        val catalog = AuthCatalog.copy(AuthCatalogLocale.Spanish)
        var successes = 0
        var failures = 0
        try {
            setContent {
                val state by viewModel.uiState.collectAsState()
                LaunchedEffect(viewModel) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            LoginEffect.Success -> successes += 1
                            is LoginEffect.Failure -> failures += 1
                        }
                    }
                }
                QuataTheme {
                    Column {
                        LoginForm(
                            state = state,
                            prefixes = listOf(CountryPrefix("240", "+240")),
                            strings = catalog.login,
                            isLandscape = false,
                            showMockNotice = false,
                            onEvent = viewModel::onEvent,
                            onForgotPassword = {},
                            onGoToRegister = {},
                        )
                    }
                }
            }

            onNodeWithTag("auth.phone.input").performTextInput("799000000000")
            onNodeWithTag("auth.password").performTextInput("Synthetic-password-7!")
            onNodeWithTag("auth.submit").performClick()
            viewModel.onEvent(LoginUiEvent.Submit)
            scheduler.runCurrent()

            onNodeWithTag("auth.submit").assertIsNotEnabled()
            assertEquals(1, repository.calls.size)
            assertEquals(
                LoginAttempt("240", "799000000000", "Synthetic-password-7!"),
                repository.calls.single(),
            )

            repository.results.removeFirst().complete(
                Result.failure(IllegalStateException("synthetic-login-rejected")),
            )
            scheduler.runCurrent()

            onNodeWithTag("auth.error").assertTextEquals("synthetic-login-rejected")
            onNodeWithTag("auth.phone.input").assertTextEquals("799000000000")
            assertEquals("799000000000", viewModel.uiState.value.phone)
            assertEquals("Synthetic-password-7!", viewModel.uiState.value.password)
            onNodeWithTag("auth.submit").assertIsEnabled()
            assertEquals(1, failures)
            assertEquals(0, successes)

            onNodeWithTag("auth.submit").performClick()
            scheduler.runCurrent()
            assertEquals(2, repository.calls.size)
            assertEquals(repository.calls[0], repository.calls[1])

            repository.results.removeFirst().complete(Result.success(session()))
            scheduler.runCurrent()

            assertEquals(1, failures)
            assertEquals(1, successes)
            assertEquals(2, repository.calls.size)
        } finally {
            viewModel.close()
        }
    }

    private class SequencedLoginRepository : LoginRepository {
        val calls = mutableListOf<LoginAttempt>()
        val results = ArrayDeque<CompletableDeferred<Result<AuthSession>>>().apply {
            add(CompletableDeferred())
            add(CompletableDeferred())
        }

        override suspend fun login(
            countryCode: String,
            phone: String,
            password: String,
        ): Result<AuthSession> {
            calls += LoginAttempt(countryCode, phone, password)
            return results.first().await()
        }
    }
}

private data class LoginAttempt(
    val countryCode: String,
    val phone: String,
    val password: String,
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
