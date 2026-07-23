package com.quata.feature.auth.presentation.register

import com.quata.core.common.AppDispatchers
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.domain.RegisterAccountRequest
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

class RegisterViewModel(
    private val repository: AuthRepository,
    dispatchers: AppDispatchers = AppDispatchers()
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<RegisterEffect>()
    val effects: SharedFlow<RegisterEffect> = _effects.asSharedFlow()

    fun onEvent(event: RegisterUiEvent) {
        when (event) {
            is RegisterUiEvent.DisplayNameChanged -> update { copy(displayName = event.value, error = null) }
            is RegisterUiEvent.NeighborhoodChanged -> update { copy(neighborhood = event.value, error = null) }
            is RegisterUiEvent.CountryCodeChanged -> update { copy(countryCode = event.value, error = null) }
            is RegisterUiEvent.PhoneChanged -> update { copy(phone = event.value, error = null) }
            is RegisterUiEvent.PasswordChanged -> update { copy(password = event.value, error = null) }
            is RegisterUiEvent.SecretQuestionChanged -> update { copy(secretQuestion = event.value, error = null) }
            is RegisterUiEvent.SecretAnswerChanged -> update { copy(secretAnswer = event.value, error = null) }
            RegisterUiEvent.Submit -> register()
        }
    }

    private fun register() = scope.launch {
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true, error = null)
        repository.register(
            RegisterAccountRequest(
                displayName = state.displayName,
                neighborhood = state.neighborhood,
                countryCode = state.countryCode,
                phone = state.phone,
                password = state.password,
                secretQuestion = state.secretQuestion,
                secretAnswer = state.secretAnswer
            )
        )
            .onSuccess { _effects.emit(RegisterEffect.Success) }
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "Error al registrar") }
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    private fun update(transform: RegisterUiState.() -> RegisterUiState) {
        _uiState.value = _uiState.value.transform()
    }

    fun close() {
        scope.coroutineContext.cancel()
    }
}

sealed class RegisterEffect {
    data object Success : RegisterEffect()
}
