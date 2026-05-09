package com.quata.feature.profile.domain

data class CountryPrefix(
    val code: String,
    val label: String
)

data class SecretQuestionOption(
    val value: String,
    val label: String
)

data class EmergencyContactCandidate(
    val id: String,
    val displayName: String,
    val email: String,
    val neighborhood: String,
    val phone: String = ""
)

data class UserProfile(
    val displayName: String,
    val neighborhood: String,
    val countryCode: String,
    val phone: String,
    val avatarUri: String? = null,
    val selectedSecretQuestion: String = "",
    val emergencyContactIds: List<String> = emptyList(),
    val emergencyMessage: String,
    val emergencyMessageIsDefault: Boolean = true
)

data class ProfileEditConfig(
    val countryPrefixes: List<CountryPrefix>,
    val secretQuestions: List<SecretQuestionOption>,
    val emergencyCandidates: List<EmergencyContactCandidate>
)

data class ProfileEditModel(
    val profile: UserProfile,
    val config: ProfileEditConfig
)

data class ProfileUpdate(
    val displayName: String,
    val neighborhood: String,
    val countryCode: String,
    val phone: String,
    val avatarUri: String?,
    val newPassword: String,
    val secretQuestion: String,
    val secretAnswer: String,
    val emergencyContactIds: List<String>,
    val emergencyMessage: String,
    val emergencyMessageIsDefault: Boolean
)
