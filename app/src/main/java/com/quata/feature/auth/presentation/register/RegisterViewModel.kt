package com.quata.feature.auth.presentation.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegisterViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<RegisterEffect>()
    val effects: SharedFlow<RegisterEffect> = _effects.asSharedFlow()

    fun onEvent(event: RegisterUiEvent) {
        when (event) {
            is RegisterUiEvent.DisplayNameChanged -> _uiState.value = _uiState.value.copy(displayName = event.value, error = null)
            is RegisterUiEvent.EmailChanged -> _uiState.value = _uiState.value.copy(email = event.value, error = null)
            is RegisterUiEvent.PasswordChanged -> _uiState.value = _uiState.value.copy(password = event.value, error = null)
            RegisterUiEvent.Submit -> register()
        }
    }

    private fun register() = viewModelScope.launch {
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true, error = null)
        repository.register(state.email, state.password, state.displayName)
            .onSuccess { _effects.emit(RegisterEffect.Success) }
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "Error al registrar") }
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    companion object {
        fun factory(repository: AuthRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = RegisterViewModel(repository) as T
        }
    }
}

sealed class RegisterEffect { data object Success : RegisterEffect() }
