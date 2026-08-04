package com.quata.core.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One launcher-owned signal for the Android-style member-profile loading identity on iOS.
 *
 * UIKit starts and finishes the real repository preload, while every mounted Compose source
 * (Feed, Official, Conversations or Chat) observes the same profile ID and draws the common
 * rotating halo around the originating avatar. No profile data or navigation state is copied
 * into Swift.
 */
class IosMemberProfileOpeningState {
    private val mutableProfileId = MutableStateFlow<String?>(null)

    val profileId: StateFlow<String?> = mutableProfileId.asStateFlow()

    /** Prevents concurrent taps from opening two profile requests or two modal controllers. */
    fun begin(profileId: String): Boolean =
        profileId.isNotBlank() && mutableProfileId.compareAndSet(expect = null, update = profileId)

    /** A stale completion cannot clear a newer request. */
    fun finish(profileId: String) {
        mutableProfileId.compareAndSet(expect = profileId, update = null)
    }

    fun clear() {
        mutableProfileId.value = null
    }
}
