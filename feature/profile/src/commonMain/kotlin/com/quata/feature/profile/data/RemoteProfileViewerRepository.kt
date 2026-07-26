package com.quata.feature.profile.data

import com.quata.feature.profile.domain.ProfileViewerProfile
import com.quata.feature.profile.domain.ProfileViewerRepository
import com.quata.feature.profile.domain.ProfileViewerResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

/** Adapts the authenticated Profile gateway for safe, read-only member profile viewing. */
class RemoteProfileViewerRepository(
    private val remote: ProfileRemoteGateway,
    private val sessions: ProfileSessionProvider,
) : ProfileViewerRepository {
    override fun observeProfile(profileId: String): Flow<ProfileViewerResult> = flow {
        val currentProfileId = sessions.currentSession()?.profileId
        val record = remote.getProfile(profileId)
        emit(
            if (record == null) {
                ProfileViewerResult.Unavailable
            } else {
                ProfileViewerResult.Available(
                    ProfileViewerProfile(
                        id = record.id,
                        displayName = record.displayName.cleanViewerValue()
                            ?: record.legacyName.cleanViewerValue()
                            ?: record.id,
                        neighborhood = record.neighborhood.cleanViewerValue()
                            ?: record.legacyNeighborhood.cleanViewerValue().orEmpty(),
                        avatarUri = record.avatarUrl.cleanViewerValue()
                            ?: record.legacyAvatar.cleanViewerValue(),
                        isCurrentUser = record.id == currentProfileId,
                    ),
                )
            },
        )
    }.catch { error ->
        emit(ProfileViewerResult.Failure(error.message ?: "profile_read_failed"))
    }
}

private fun String?.cleanViewerValue(): String? = this?.trim()?.takeIf(String::isNotEmpty)
