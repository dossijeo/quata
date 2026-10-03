package com.quata.feature.auth.presentation.login

import com.quata.core.common.AppDispatchers
import com.quata.feature.auth.domain.GoogleAuthProvider
import com.quata.feature.auth.domain.GoogleOAuthUserCancellation
import com.quata.feature.auth.domain.LoginRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class LoginViewModel(
    private val repository: LoginRepository,
    private val googleAuthProvider: GoogleAuthProvider? = null,
    private val googleFailureMessage: String = "Could not sign in with Google.",
    dispatchers: AppDispatchers = AppDispatchers()
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<LoginEffect>()
    val effects: SharedFlow<LoginEffect> = _effects.asSharedFlow()
    private var googleLoginJob: Job? = null

    fun onEvent(event: LoginUiEvent) {
        when (event) {
            is LoginUiEvent.CountryCodeChanged -> _uiState.value = _uiState.value.copy(countryCode = event.value, error = null)
            is LoginUiEvent.PhoneChanged -> _uiState.value = _uiState.value.copy(phone = event.value, error = null)
            is LoginUiEvent.PasswordChanged -> _uiState.value = _uiState.value.copy(password = event.value, error = null)
            LoginUiEvent.Submit -> login()
            LoginUiEvent.GoogleSubmit -> loginWithGoogle()
            LoginUiEvent.GoogleCancel -> googleLoginJob?.cancel(GoogleOAuthUserCancellation())
        }
    }

    private fun login() = scope.launch {
        if (_uiState.value.isBusy) return@launch
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true, error = null)
        repository.login(state.countryCode, state.phone, state.password)
            .onSuccess { _effects.emit(LoginEffect.Success) }
            .onFailure {
                val message = it.message ?: "Error al iniciar sesión"
                _uiState.value = _uiState.value.copy(error = message)
                _effects.emit(LoginEffect.Failure(message))
            }
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    private fun loginWithGoogle() {
        if (_uiState.value.isBusy) return
        val provider = googleAuthProvider ?: return
        val signIn = provider.beginSignIn()
        // Enter the prepared operation in the originating UI event so a Web popup remains
        // covered by the same user activation and cancellation is installed before disposal.
        googleLoginJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            _uiState.value = _uiState.value.copy(isGoogleLoading = true, error = null)
            try {
                val result = try {
                    signIn()
                } catch (failure: TimeoutCancellationException) {
                    Result.failure(failure)
                }
                result
                    .onSuccess { _effects.emit(LoginEffect.Success) }
                    .onFailure { failure ->
                        if (failure is TimeoutCancellationException) {
                            _uiState.value = _uiState.value.copy(error = googleFailureMessage)
                            _effects.emit(LoginEffect.Failure(googleFailureMessage))
                            return@onFailure
                        }
                        if (failure is CancellationException) throw failure
                        val message = googleFailureMessage
                        _uiState.value = _uiState.value.copy(error = message)
                        _effects.emit(LoginEffect.Failure(message))
                    }
            } finally {
                _uiState.value = _uiState.value.copy(isGoogleLoading = false)
                googleLoginJob = null
            }
        }
    }

    fun close() {
        scope.coroutineContext.cancel()
    }
}

sealed class LoginEffect {
    data object Success : LoginEffect()
    data class Failure(val message: String) : LoginEffect()
}
