package com.quata.feature.auth.presentation.login

data class LoginUiState(
    val countryCode: String = "240",
    val phone: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
