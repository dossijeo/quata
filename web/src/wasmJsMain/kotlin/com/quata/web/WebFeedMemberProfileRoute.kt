package com.quata.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Feed-local presentation state for the existing Communities member-profile surface.
 *
 * It intentionally has no hash-route side effect: the Feed tab remains selected while a member
 * profile is open. Calling [close] consumes the request so a later recomposition cannot reopen
 * the previous profile after the user returned to the reel.
 */
internal class WebFeedMemberProfileRoute {
    var profileId: String? by mutableStateOf(null)
        private set

    fun open(profileId: String) {
        this.profileId = profileId.takeIf(String::isNotBlank)
    }

    fun close() {
        profileId = null
    }

    fun openConversation(
        conversationId: String,
        navigate: (String) -> Unit,
    ) {
        close()
        navigate(conversationId)
    }
}
