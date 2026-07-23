package com.quata.feature.auth.presentation.recovery

sealed class ForgotPasswordUiEvent {
    data class CountryCodeChanged(val value: String) : ForgotPasswordUiEvent()
    data class PhoneChanged(val value: String) : ForgotPasswordUiEvent()
    data class SecretAnswerChanged(val value: String) : ForgotPasswordUiEvent()
    data class NewPasswordChanged(val value: String) : ForgotPasswordUiEvent()
    data object Submit : ForgotPasswordUiEvent()
}
