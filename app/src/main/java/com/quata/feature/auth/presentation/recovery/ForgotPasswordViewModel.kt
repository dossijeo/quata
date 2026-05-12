package com.quata.feature.auth.presentation.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.feature.auth.domain.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ForgotPasswordEffect>()
    val effects: SharedFlow<ForgotPasswordEffect> = _effects.asSharedFlow()

    private var questionLookupJob: Job? = null

    fun onEvent(event: ForgotPasswordUiEvent) {
        when (event) {
            is ForgotPasswordUiEvent.CountryCodeChanged -> {
                update { copy(countryCode = event.value, secretQuestion = "", error = null) }
                scheduleQuestionLookup()
            }
            is ForgotPasswordUiEvent.PhoneChanged -> {
                update { copy(phone = event.value, secretQuestion = "", error = null) }
                scheduleQuestionLookup()
            }
            is ForgotPasswordUiEvent.SecretAnswerChanged -> update { copy(secretAnswer = event.value, error = null) }
            is ForgotPasswordUiEvent.NewPasswordChanged -> update { copy(newPassword = event.value, error = null) }
            ForgotPasswordUiEvent.Submit -> resetPassword()
        }
    }

    private fun scheduleQuestionLookup() {
        questionLookupJob?.cancel()
        questionLookupJob = viewModelScope.launch {
            delay(250L)
            val state = _uiState.value
            if (state.phone.filter(Char::isDigit).length < 5) return@launch
            _uiState.value = state.copy(isLoadingQuestion = true)
            repository.getPasswordRecoveryQuestion(state.countryCode, state.phone)
                .onSuccess { question ->
                    _uiState.value = _uiState.value.copy(
                        secretQuestion = question?.secretQuestion.orEmpty(),
                        isLoadingQuestion = false,
                        error = if (question == null) "No hay ninguna cuenta con ese teléfono" else null
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoadingQuestion = false,
                        error = it.message ?: "No se pudo cargar la pregunta secreta"
                    )
                }
        }
    }

    private fun resetPassword() = viewModelScope.launch {
        val state = _uiState.value
        if (state.secretQuestion.isBlank()) {
            _uiState.value = state.copy(error = "Introduce un teléfono registrado")
            return@launch
        }
        _uiState.value = state.copy(isUpdating = true, error = null)
        repository.resetPassword(
            countryCode = state.countryCode,
            phone = state.phone,
            secretAnswer = state.secretAnswer,
            newPassword = state.newPassword
        )
            .onSuccess { _effects.emit(ForgotPasswordEffect.PasswordUpdated) }
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo actualizar la contraseña") }
        _uiState.value = _uiState.value.copy(isUpdating = false)
    }

    private fun update(transform: ForgotPasswordUiState.() -> ForgotPasswordUiState) {
        _uiState.value = _uiState.value.transform()
    }

    companion object {
        fun factory(repository: AuthRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ForgotPasswordViewModel(repository) as T
        }
    }
}

sealed class ForgotPasswordEffect {
    data object PasswordUpdated : ForgotPasswordEffect()
}
