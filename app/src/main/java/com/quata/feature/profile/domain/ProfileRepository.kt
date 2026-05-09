package com.quata.feature.profile.domain

interface ProfileRepository {
    suspend fun getProfileEditModel(): Result<ProfileEditModel>
    suspend fun saveProfile(update: ProfileUpdate): Result<Unit>
}
