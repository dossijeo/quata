package com.quata.feature.profile.presentation

import com.quata.feature.profile.domain.CountryPrefix
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.SecretQuestionOption
import com.quata.feature.profile.domain.UserProfile

data class ProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val profile: UserProfile? = null,
    val countryPrefixes: List<CountryPrefix> = emptyList(),
    val secretQuestions: List<SecretQuestionOption> = emptyList(),
    val emergencyCandidates: List<EmergencyContactCandidate> = emptyList(),
    val newPassword: String = "",
    val newSecretAnswer: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null
)
