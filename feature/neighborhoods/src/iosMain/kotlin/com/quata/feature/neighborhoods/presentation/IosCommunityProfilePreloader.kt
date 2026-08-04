package com.quata.feature.neighborhoods.presentation

import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Swift-facing asynchronous boundary for loading a real public profile before navigation.
 *
 * The repository and model remain Kotlin contracts. UIKit receives only the completed common
 * model (or an honest error message) and therefore never constructs an intermediate profile UI.
 */
class IosCommunityProfilePreloader(
    private val repository: NeighborhoodRepository,
) {
    private val scope = MainScope()

    fun load(
        profileId: String,
        onCompleted: (CommunityUserProfile?, String?) -> Unit,
    ) {
        scope.launch {
            repository.getUserProfile(profileId).fold(
                onSuccess = { profile -> onCompleted(profile, null) },
                onFailure = { error ->
                    onCompleted(null, error.message?.takeIf(String::isNotBlank) ?: "profile_load_failed")
                },
            )
        }
    }

    fun close() {
        scope.cancel()
    }
}
