package com.quata.feature.auth.presentation.login

import android.content.Context

sealed class LoginUiEvent {
    data class CountryCodeChanged(val value: String) : LoginUiEvent()
    data class PhoneChanged(val value: String) : LoginUiEvent()
    data class PasswordChanged(val value: String) : LoginUiEvent()
    data object Submit : LoginUiEvent()
    data class GoogleSubmit(val context: Context) : LoginUiEvent()
}
