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
internal class WebFeedMemberProfileRoute(
    private val navigateConversation: (String) -> Unit,
) {
    var profileId: String? by mutableStateOf(null)
        private set

    fun open(profileId: String) {
        this.profileId = profileId.takeIf(String::isNotBlank)
        setWebMemberProfileRouteMarker(this.profileId)
    }

    fun close() {
        profileId = null
        setWebMemberProfileRouteMarker(null)
    }

    fun openConversation(conversationId: String) {
        close()
        navigateConversation(conversationId)
    }
}

@JsFun("""(profileId) => {
  const root = globalThis.document?.documentElement;
  if (!root) return;
  if (profileId) root.setAttribute('data-quata-member-profile-id', profileId);
  else root.removeAttribute('data-quata-member-profile-id');
}""")
private external fun setWebMemberProfileRouteMarker(profileId: String?)
