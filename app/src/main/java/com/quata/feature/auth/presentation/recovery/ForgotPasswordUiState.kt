package com.quata.feature.auth.presentation.recovery

data class ForgotPasswordUiState(
    val countryCode: String = "240",
    val phone: String = "",
    val secretQuestion: String = "",
    val secretAnswer: String = "",
    val newPassword: String = "",
    val isLoadingQuestion: Boolean = false,
    val isUpdating: Boolean = false,
    val error: String? = null
)
