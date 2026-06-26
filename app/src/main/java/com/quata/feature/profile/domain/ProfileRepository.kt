package com.quata.feature.profile.domain

import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfileEditModel(): Flow<Result<ProfileEditModel>>
    suspend fun getProfileEditModel(): Result<ProfileEditModel>
    suspend fun saveProfile(update: ProfileUpdate): Result<Unit>
    suspend fun saveEmergencySettings(
        contactIds: List<String>,
        message: String,
        messageIsDefault: Boolean
    ): Result<Unit>
    fun defaultEmergencyMessage(displayName: String): String
    fun changesSavedMessage(): String
    fun emergencyContactsSavedMessage(): String
}
