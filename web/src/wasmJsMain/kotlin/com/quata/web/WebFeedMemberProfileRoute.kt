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
    private val profileStack = mutableListOf<String>()

    var profileId: String? by mutableStateOf(null)
        private set

    fun open(profileId: String) {
        val normalizedProfileId = profileId.takeIf(String::isNotBlank) ?: return
        if (profileStack.lastOrNull() != normalizedProfileId) {
            profileStack += normalizedProfileId
        }
        publishCurrentProfile()
    }

    fun close() {
        if (profileStack.isNotEmpty()) profileStack.removeAt(profileStack.lastIndex)
        publishCurrentProfile()
    }

    fun openConversation(conversationId: String) {
        profileStack.clear()
        publishCurrentProfile()
        navigateConversation(conversationId)
    }

    private fun publishCurrentProfile() {
        profileId = profileStack.lastOrNull()
        setWebMemberProfileRouteMarker(profileId)
    }
}

@JsFun("""(profileId) => {
  const root = globalThis.document?.documentElement;
  if (!root) return;
  if (profileId) root.setAttribute('data-quata-member-profile-id', profileId);
  else root.removeAttribute('data-quata-member-profile-id');
}""")
private external fun setWebMemberProfileRouteMarker(profileId: String?)
