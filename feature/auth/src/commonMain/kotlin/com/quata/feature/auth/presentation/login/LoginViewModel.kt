package com.quata.feature.auth.presentation.login

import com.quata.core.common.AppDispatchers
import com.quata.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: AuthRepository,
    dispatchers: AppDispatchers = AppDispatchers()
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<LoginEffect>()
    val effects: SharedFlow<LoginEffect> = _effects.asSharedFlow()

    fun onEvent(event: LoginUiEvent) {
        when (event) {
            is LoginUiEvent.CountryCodeChanged -> _uiState.value = _uiState.value.copy(countryCode = event.value, error = null)
            is LoginUiEvent.PhoneChanged -> _uiState.value = _uiState.value.copy(phone = event.value, error = null)
            is LoginUiEvent.PasswordChanged -> _uiState.value = _uiState.value.copy(password = event.value, error = null)
            LoginUiEvent.Submit -> login()
        }
    }

    private fun login() = scope.launch {
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true, error = null)
        repository.login(state.countryCode, state.phone, state.password)
            .onSuccess { _effects.emit(LoginEffect.Success) }
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "Error al iniciar sesión") }
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    fun close() {
        scope.coroutineContext.cancel()
    }
}

sealed class LoginEffect {
    data object Success : LoginEffect()
}
