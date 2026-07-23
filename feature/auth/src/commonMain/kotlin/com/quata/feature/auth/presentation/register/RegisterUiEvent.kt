package com.quata.feature.auth.presentation.register

sealed class RegisterUiEvent {
    data class DisplayNameChanged(val value: String) : RegisterUiEvent()
    data class NeighborhoodChanged(val value: String) : RegisterUiEvent()
    data class CountryCodeChanged(val value: String) : RegisterUiEvent()
    data class PhoneChanged(val value: String) : RegisterUiEvent()
    data class PasswordChanged(val value: String) : RegisterUiEvent()
    data class SecretQuestionChanged(val value: String) : RegisterUiEvent()
    data class SecretAnswerChanged(val value: String) : RegisterUiEvent()
    data object Submit : RegisterUiEvent()
}
