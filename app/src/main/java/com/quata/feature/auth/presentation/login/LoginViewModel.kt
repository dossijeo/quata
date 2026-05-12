package com.quata.feature.auth.presentation.login

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

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {
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
            is LoginUiEvent.GoogleSubmit -> loginGoogle(event.context)
        }
    }

    private fun login() = viewModelScope.launch {
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true, error = null)
        repository.login(state.countryCode, state.phone, state.password)
            .onSuccess { _effects.emit(LoginEffect.Success) }
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "Error al iniciar sesión") }
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    private fun loginGoogle(context: android.content.Context) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        repository.loginWithGoogle(context)
            .onSuccess { _effects.emit(LoginEffect.Success) }
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "Google Sign-In no configurado") }
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    companion object {
        fun factory(repository: AuthRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LoginViewModel(repository) as T
        }
    }
}

sealed class LoginEffect {
    data object Success : LoginEffect()
}
