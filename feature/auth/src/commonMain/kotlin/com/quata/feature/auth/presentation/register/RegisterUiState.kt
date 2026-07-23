package com.quata.feature.auth.presentation.register

data class RegisterUiState(
    val displayName: String = "",
    val neighborhood: String = "",
    val countryCode: String = "240",
    val phone: String = "",
    val password: String = "",
    val secretQuestion: String = "",
    val secretAnswer: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
