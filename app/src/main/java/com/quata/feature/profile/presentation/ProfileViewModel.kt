package com.quata.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.profile.domain.ProfileUpdate
import com.quata.feature.profile.domain.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: ProfileRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun onEvent(event: ProfileUiEvent) {
        when (event) {
            is ProfileUiEvent.AvatarChanged -> updateProfile { copy(avatarUri = event.uri) }
            is ProfileUiEvent.NameChanged -> updateProfile {
                copy(
                    displayName = event.value,
                    emergencyMessage = if (emergencyMessageIsDefault) defaultEmergencyMessage(event.value) else emergencyMessage
                )
            }
            is ProfileUiEvent.NeighborhoodChanged -> updateProfile { copy(neighborhood = event.value) }
            is ProfileUiEvent.CountryCodeChanged -> updateProfile { copy(countryCode = event.value) }
            is ProfileUiEvent.PhoneChanged -> updateProfile { copy(phone = event.value) }
            is ProfileUiEvent.NewPasswordChanged -> _uiState.update { it.copy(newPassword = event.value) }
            is ProfileUiEvent.SecretQuestionChanged -> updateProfile { copy(selectedSecretQuestion = event.value) }
            is ProfileUiEvent.SecretAnswerChanged -> _uiState.update { it.copy(newSecretAnswer = event.value) }
            is ProfileUiEvent.EmergencyMessageChanged -> updateProfile {
                copy(emergencyMessage = event.value, emergencyMessageIsDefault = false)
            }
            is ProfileUiEvent.EmergencyContactToggled -> toggleEmergencyContact(event.contactId)
            ProfileUiEvent.Save -> saveProfile()
            ProfileUiEvent.ClearMessages -> _uiState.update { it.copy(errorMessage = null, successMessage = null) }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getProfileEditModel()
                .onSuccess { model ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            profile = model.profile,
                            countryPrefixes = model.config.countryPrefixes,
                            secretQuestions = model.config.secretQuestions,
                            emergencyCandidates = model.config.emergencyCandidates
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "No se pudo cargar el perfil"
                        )
                    }
                }
        }
    }

    private fun saveProfile() {
        val state = _uiState.value
        val profile = state.profile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }
            repository.saveProfile(
                ProfileUpdate(
                    displayName = profile.displayName,
                    neighborhood = profile.neighborhood,
                    countryCode = profile.countryCode,
                    phone = profile.phone,
                    avatarUri = profile.avatarUri,
                    newPassword = state.newPassword,
                    secretQuestion = profile.selectedSecretQuestion,
                    secretAnswer = state.newSecretAnswer,
                    emergencyContactIds = profile.emergencyContactIds,
                    emergencyMessage = profile.emergencyMessage,
                    emergencyMessageIsDefault = profile.emergencyMessageIsDefault
                )
            )
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            newPassword = "",
                            newSecretAnswer = "",
                            successMessage = "Cambios guardados"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "No se pudieron guardar los cambios"
                        )
                    }
                }
        }
    }

    private fun updateProfile(transform: UserProfile.() -> UserProfile) {
        _uiState.update { state ->
            val profile = state.profile ?: return@update state
            state.copy(profile = profile.transform())
        }
    }

    private fun toggleEmergencyContact(contactId: String) {
        updateProfile {
            val selected = emergencyContactIds.toMutableList()
            if (selected.contains(contactId)) {
                selected.remove(contactId)
            } else if (selected.size < 5) {
                selected.add(contactId)
            }
            copy(emergencyContactIds = selected)
        }
    }

    private fun defaultEmergencyMessage(displayName: String): String =
        "🚨 SOS REAL: ${displayName.ifBlank { "Usuario" }} necesita ayuda urgente. Eres uno de sus contactos de emergencia en QUATA."

    class Factory(
        private val repository: ProfileRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileViewModel(repository) as T
        }
    }
}
