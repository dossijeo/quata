package com.quata.feature.profile.presentation

sealed class ProfileUiEvent {
    data class AvatarChanged(val uri: String?) : ProfileUiEvent()
    data class NameChanged(val value: String) : ProfileUiEvent()
    data class NeighborhoodChanged(val value: String) : ProfileUiEvent()
    data class CountryCodeChanged(val value: String) : ProfileUiEvent()
    data class PhoneChanged(val value: String) : ProfileUiEvent()
    data class NewPasswordChanged(val value: String) : ProfileUiEvent()
    data class SecretQuestionChanged(val value: String) : ProfileUiEvent()
    data class SecretAnswerChanged(val value: String) : ProfileUiEvent()
    data class EmergencyMessageChanged(val value: String) : ProfileUiEvent()
    data class EmergencyContactToggled(val contactId: String) : ProfileUiEvent()
    data object Save : ProfileUiEvent()
    data object ClearMessages : ProfileUiEvent()
}
