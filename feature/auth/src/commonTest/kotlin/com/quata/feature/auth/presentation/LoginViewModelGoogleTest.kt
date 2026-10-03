package com.quata.feature.auth.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.model.AuthSession
import com.quata.feature.auth.domain.GoogleAuthProvider
import com.quata.feature.auth.domain.GoogleOAuthUserCancellation
import com.quata.feature.auth.domain.LoginRepository
import com.quata.feature.auth.presentation.login.LoginEffect
import com.quata.feature.auth.presentation.login.LoginUiEvent
import com.quata.feature.auth.presentation.login.LoginViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelGoogleTest {
    @Test
    fun googleSuccessUsesTheSharedNavigationEffectAndRestoresIdleState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = RecordingGoogleProvider(Result.success(session()))
        val viewModel = LoginViewModel(
            repository = UnusedLoginRepository,
            googleAuthProvider = provider,
            dispatchers = AppDispatchers(default = dispatcher),
        )
        val effect = async { viewModel.effects.first() }
        runCurrent()

        viewModel.onEvent(LoginUiEvent.GoogleSubmit)
        assertEquals(1, provider.beginCalls)
        assertEquals(1, provider.calls)
        runCurrent()

        assertEquals(LoginEffect.Success, effect.await())
        assertEquals(1, provider.calls)
        assertFalse(viewModel.uiState.value.isGoogleLoading)
        assertFalse(viewModel.uiState.value.isBusy)
        assertEquals(null, viewModel.uiState.value.error)
        viewModel.close()
    }

    @Test
    fun googleFailureStaysVisibleAndDoesNotSubmitPhoneCredentials() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = RecordingGoogleProvider(Result.failure(IllegalStateException("google_profile_not_linked")))
        val repository = RecordingLoginRepository()
        val viewModel = LoginViewModel(
            repository = repository,
            googleAuthProvider = provider,
            dispatchers = AppDispatchers(default = dispatcher),
        )
        val effect = async { viewModel.effects.first() }
        runCurrent()

        viewModel.onEvent(LoginUiEvent.GoogleSubmit)
        assertEquals(1, provider.beginCalls)
        assertEquals(1, provider.calls)
        runCurrent()

        assertEquals(LoginEffect.Failure("Could not sign in with Google."), effect.await())
        assertEquals(1, provider.calls)
        assertEquals(0, repository.calls)
        assertEquals("Could not sign in with Google.", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isBusy)
        viewModel.close()
    }

    @Test
    fun oneGoogleAttemptOwnsTheScreenUntilItCompletes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val completion = CompletableDeferred<Result<AuthSession>>()
        val provider = object : GoogleAuthProvider {
            var beginCalls = 0
            var calls = 0
            override fun beginSignIn(): suspend () -> Result<AuthSession> {
                beginCalls += 1
                return suspend {
                    calls += 1
                    completion.await()
                }
            }
        }
        val repository = RecordingLoginRepository()
        val viewModel = LoginViewModel(
            repository = repository,
            googleAuthProvider = provider,
            dispatchers = AppDispatchers(default = dispatcher),
        )

        viewModel.onEvent(LoginUiEvent.GoogleSubmit)
        assertEquals(1, provider.beginCalls)
        assertEquals(1, provider.calls)
        runCurrent()
        assertTrue(viewModel.uiState.value.isGoogleLoading)
        assertTrue(viewModel.uiState.value.isBusy)

        viewModel.onEvent(LoginUiEvent.GoogleSubmit)
        viewModel.onEvent(LoginUiEvent.Submit)
        runCurrent()
        assertEquals(1, provider.calls)
        assertEquals(0, repository.calls)

        val effect = async { viewModel.effects.first() }
        completion.complete(Result.success(session()))
        runCurrent()
        assertEquals(LoginEffect.Success, effect.await())
        assertFalse(viewModel.uiState.value.isBusy)
        viewModel.close()
    }

    @Test
    fun googleAttemptCanBeCancelledWithoutPublishingFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val completion = CompletableDeferred<AuthSession>()
        val provider = object : GoogleAuthProvider {
            override fun beginSignIn(): suspend () -> Result<AuthSession> = suspend {
                runCatching { completion.await() }
            }
        }
        val viewModel = LoginViewModel(
            repository = UnusedLoginRepository,
            googleAuthProvider = provider,
            dispatchers = AppDispatchers(default = dispatcher),
        )

        viewModel.onEvent(LoginUiEvent.GoogleSubmit)
        runCurrent()
        assertTrue(viewModel.uiState.value.isGoogleLoading)

        viewModel.onEvent(LoginUiEvent.GoogleCancel)
        runCurrent()

        assertFalse(viewModel.uiState.value.isGoogleLoading)
        assertFalse(viewModel.uiState.value.isBusy)
        assertEquals(null, viewModel.uiState.value.error)
        viewModel.close()
    }

    @Test
    fun providerCancellationResultIsRethrownWithoutPublishingFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = RecordingGoogleProvider(Result.failure(GoogleOAuthUserCancellation()))
        val viewModel = LoginViewModel(
            repository = UnusedLoginRepository,
            googleAuthProvider = provider,
            dispatchers = AppDispatchers(default = dispatcher),
        )

        viewModel.onEvent(LoginUiEvent.GoogleSubmit)
        runCurrent()

        assertFalse(viewModel.uiState.value.isGoogleLoading)
        assertEquals(null, viewModel.uiState.value.error)
        viewModel.close()
    }

    @Test
    fun nativeGoogleTimeoutPublishesFailureAndRestoresIdleState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = GoogleAuthProvider {
            suspend {
                withTimeout(1) { awaitCancellation() }
            }
        }
        val viewModel = LoginViewModel(
            repository = UnusedLoginRepository,
            googleAuthProvider = provider,
            googleFailureMessage = "Google timed out",
            dispatchers = AppDispatchers(default = dispatcher),
        )
        val effect = async { viewModel.effects.first() }

        viewModel.onEvent(LoginUiEvent.GoogleSubmit)
        advanceUntilIdle()

        assertEquals(LoginEffect.Failure("Google timed out"), effect.await())
        assertEquals("Google timed out", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isGoogleLoading)
        viewModel.close()
    }
}

private class RecordingGoogleProvider(
    private val result: Result<AuthSession>,
) : GoogleAuthProvider {
    var beginCalls = 0
    var calls = 0

    override fun beginSignIn(): suspend () -> Result<AuthSession> {
        beginCalls += 1
        return suspend {
            calls += 1
            result
        }
    }
}

private class RecordingLoginRepository : LoginRepository {
    var calls = 0

    override suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession> {
        calls += 1
        return Result.failure(IllegalStateException("phone_login_not_expected"))
    }
}

private object UnusedLoginRepository : LoginRepository {
    override suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession> =
        Result.failure(IllegalStateException("phone_login_not_expected"))
}

private fun session() = AuthSession(
    token = "access-token",
    userId = "profile-id",
    authUserId = "auth-user-id",
    accessToken = "access-token",
    refreshToken = "refresh-token",
    expiresAt = 4_102_444_800,
    email = "google@example.test",
    displayName = "Google User",
)
