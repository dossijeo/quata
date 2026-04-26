package com.quata.feature.auth.presentation.register

sealed class RegisterUiEvent {
    data class DisplayNameChanged(val value: String) : RegisterUiEvent()
    data class EmailChanged(val value: String) : RegisterUiEvent()
    data class PasswordChanged(val value: String) : RegisterUiEvent()
    data object Submit : RegisterUiEvent()
}
