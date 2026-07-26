package com.quata.feature.profile.domain

import kotlinx.coroutines.flow.Flow

/**
 * Read-only projection used when a feature opens a community member's profile.
 *
 * This is deliberately separate from [ProfileRepository]: the latter owns the signed-in
 * person's editable account and SOS settings, whereas this contract must never expose those
 * fields or mutation operations for another member.
 */
interface ProfileViewerRepository {
    fun observeProfile(profileId: String): Flow<ProfileViewerResult>
}

data class ProfileViewerProfile(
    val id: String,
    val displayName: String,
    val neighborhood: String,
    val avatarUri: String?,
    val isCurrentUser: Boolean,
)

sealed interface ProfileViewerResult {
    data class Available(val profile: ProfileViewerProfile) : ProfileViewerResult

    /** The authenticated backend did not return this profile (not found or not readable). */
    data object Unavailable : ProfileViewerResult

    /** A transport/configuration failure, distinct from a deliberately unavailable profile. */
    data class Failure(val message: String) : ProfileViewerResult
}
