package com.quata.feature.auth.presentation.login

sealed class LoginUiEvent {
    data class CountryCodeChanged(val value: String) : LoginUiEvent()
    data class PhoneChanged(val value: String) : LoginUiEvent()
    data class PasswordChanged(val value: String) : LoginUiEvent()
    data object Submit : LoginUiEvent()
}
