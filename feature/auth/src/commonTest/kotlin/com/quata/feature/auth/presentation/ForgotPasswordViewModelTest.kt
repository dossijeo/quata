package com.quata.feature.auth.presentation

import com.quata.core.common.AppDispatchers
import com.quata.feature.auth.domain.PasswordRecoveryQuestion
import com.quata.feature.auth.domain.PasswordRecoveryRepository
import com.quata.feature.auth.presentation.recovery.ForgotPasswordEffect
import com.quata.feature.auth.presentation.recovery.ForgotPasswordUiEvent
import com.quata.feature.auth.presentation.recovery.ForgotPasswordViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {
    @Test
    fun looksUpTheRecoveryQuestionAfterTheSharedPhoneDebounce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakePasswordRecoveryRepository(
            question = Result.success(PasswordRecoveryQuestion(secretQuestion = "madre")),
        )
        val viewModel = ForgotPasswordViewModel(repository, AppDispatchers(default = dispatcher))

        viewModel.onEvent(ForgotPasswordUiEvent.PhoneChanged("680 242 607"))
        advanceTimeBy(249)
        runCurrent()
        assertEquals(0, repository.questionRequests.size)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("240" to "680 242 607"), repository.questionRequests)
        assertEquals("madre", viewModel.uiState.value.secretQuestion)
        assertFalse(viewModel.uiState.value.isLoadingQuestion)
        assertEquals(null, viewModel.uiState.value.error)

        viewModel.close()
    }

    @Test
    fun accountNotFoundAndBackendFailuresStayVisibleInTheCommonState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val notFound = ForgotPasswordViewModel(
            FakePasswordRecoveryRepository(question = Result.success(null)),
            AppDispatchers(default = dispatcher),
        )

        notFound.onEvent(ForgotPasswordUiEvent.PhoneChanged("680242608"))
        advanceTimeBy(250)
        runCurrent()

        assertEquals("", notFound.uiState.value.secretQuestion)
        assertEquals("No hay ninguna cuenta con ese teléfono", notFound.uiState.value.error)
        notFound.close()

        val failing = ForgotPasswordViewModel(
            FakePasswordRecoveryRepository(question = Result.failure(IllegalStateException("bridge_down"))),
            AppDispatchers(default = dispatcher),
        )

        failing.onEvent(ForgotPasswordUiEvent.PhoneChanged("680242609"))
        advanceTimeBy(250)
        runCurrent()

        assertEquals("bridge_down", failing.uiState.value.error)
        assertFalse(failing.uiState.value.isLoadingQuestion)
        failing.close()
    }

    @Test
    fun successfulResetEmitsTheNavigationEffectAndRestoresTheUpdatingState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakePasswordRecoveryRepository(
            question = Result.success(PasswordRecoveryQuestion(secretQuestion = "madre")),
            reset = Result.success(Unit),
        )
        val viewModel = ForgotPasswordViewModel(repository, AppDispatchers(default = dispatcher))
        val effect = async { viewModel.effects.first() }

        viewModel.onEvent(ForgotPasswordUiEvent.PhoneChanged("680242607"))
        advanceTimeBy(250)
        runCurrent()
        viewModel.onEvent(ForgotPasswordUiEvent.SecretAnswerChanged("Luna"))
        viewModel.onEvent(ForgotPasswordUiEvent.NewPasswordChanged("21085800"))
        viewModel.onEvent(ForgotPasswordUiEvent.Submit)
        runCurrent()

        assertEquals(ForgotPasswordEffect.PasswordUpdated, effect.await())
        assertEquals(listOf(ResetRequest("240", "680242607", "Luna", "21085800")), repository.resetRequests)
        assertFalse(viewModel.uiState.value.isUpdating)
        assertEquals(null, viewModel.uiState.value.error)

        viewModel.close()
    }

    @Test
    fun submitWithoutAResolvedQuestionFailsBeforeCallingTheResetTransport() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakePasswordRecoveryRepository()
        val viewModel = ForgotPasswordViewModel(repository, AppDispatchers(default = dispatcher))

        viewModel.onEvent(ForgotPasswordUiEvent.PhoneChanged("680242607"))
        viewModel.onEvent(ForgotPasswordUiEvent.Submit)
        runCurrent()

        assertTrue(repository.resetRequests.isEmpty())
        assertEquals("Introduce un teléfono registrado", viewModel.uiState.value.error)

        viewModel.close()
    }
}

private class FakePasswordRecoveryRepository(
    private val question: Result<PasswordRecoveryQuestion?> = Result.success(null),
    private val reset: Result<Unit> = Result.success(Unit),
) : PasswordRecoveryRepository {
    val questionRequests = mutableListOf<Pair<String, String>>()
    val resetRequests = mutableListOf<ResetRequest>()

    override suspend fun getPasswordRecoveryQuestion(
        countryCode: String,
        phone: String,
    ): Result<PasswordRecoveryQuestion?> {
        questionRequests += countryCode to phone
        return question
    }

    override suspend fun resetPassword(
        countryCode: String,
        phone: String,
        secretAnswer: String,
        newPassword: String,
    ): Result<Unit> {
        resetRequests += ResetRequest(countryCode, phone, secretAnswer, newPassword)
        return reset
    }
}

private data class ResetRequest(
    val countryCode: String,
    val phone: String,
    val secretAnswer: String,
    val newPassword: String,
)
