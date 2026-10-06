package com.quata.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quata.core.navigation.AuthenticationContinuationCoordinator

/**
 * Feed-local presentation state for the existing Communities member-profile surface.
 *
 * It intentionally has no hash-route side effect: the Feed tab remains selected while a member
 * profile is open. Calling [close] consumes the request so a later recomposition cannot reopen
 * the previous profile after the user returned to the reel.
 */
internal class WebFeedMemberProfileRoute(
    private val navigateConversation: (String) -> Unit,
    private val readStoredSnapshot: () -> WebProfileRouteSnapshot? = ::readWebProfileRouteSnapshot,
    private val writeStoredSnapshot: (WebProfileRouteSnapshot) -> Unit = ::writeWebProfileRouteSnapshot,
    private val clearStoredSnapshot: () -> Unit = ::clearWebProfileRouteSnapshot,
) {
    private var contextBound = false
    private var actorId: String? = null
    private var originFragment: String? = null

    var profileRoute: List<String> by mutableStateOf(emptyList())
        private set

    var profileId: String? by mutableStateOf(null)
        private set

    fun bindContext(actorId: String?, originFragment: String, sessionResolved: Boolean) {
        if (!sessionResolved) return
        if (!contextBound) {
            contextBound = true
            this.actorId = actorId
            this.originFragment = originFragment
            val restored = readStoredSnapshot()
                ?.takeIf { it.actorId == actorId && it.originFragment == originFragment }
                ?.route
                .orEmpty()
            if (restored.isNotEmpty()) {
                profileRoute = restored
                profileId = restored.last()
                publishCurrentProfile()
            } else {
                clearStoredSnapshot()
            }
            return
        }
        if (this.actorId != actorId || (profileId != null && this.originFragment != originFragment)) {
            close()
            this.actorId = actorId
            this.originFragment = originFragment
        } else if (profileId == null) {
            this.originFragment = originFragment
        }
    }

    fun open(profileId: String) {
        val normalizedProfileId = profileId.takeIf(String::isNotBlank) ?: return
        this.profileId = normalizedProfileId
        publishCurrentProfile()
    }

    fun close() {
        profileRoute = emptyList()
        profileId = null
        clearStoredSnapshot()
        publishCurrentProfile()
    }

    fun acceptVisibleRoute(route: List<String>) {
        val accepted = route.filter(String::isNotBlank)
        profileRoute = accepted
        profileId = accepted.lastOrNull()
        if (accepted.isEmpty()) {
            clearStoredSnapshot()
        } else {
            val origin = originFragment ?: return
            writeStoredSnapshot(WebProfileRouteSnapshot(actorId, origin, accepted))
        }
        publishCurrentProfile()
    }

    fun openConversation(conversationId: String) {
        close()
        navigateConversation(conversationId)
    }

    private fun publishCurrentProfile() {
        setWebMemberProfileRouteMarker(profileId)
    }
}

internal data class WebProfileRouteSnapshot(
    val actorId: String?,
    val originFragment: String,
    val route: List<String>,
)

internal fun encodeWebProfileRoute(route: List<String>): String = buildString {
    route.forEach { profileId ->
        append(profileId.length)
        append(':')
        append(profileId)
    }
}

internal fun decodeWebProfileRoute(encoded: String?): List<String>? {
    if (encoded == null) return null
    val route = mutableListOf<String>()
    var offset = 0
    while (offset < encoded.length) {
        val separator = encoded.indexOf(':', startIndex = offset)
        if (separator <= offset) return null
        val length = encoded.substring(offset, separator).toIntOrNull() ?: return null
        if (length <= 0) return null
        val start = separator + 1
        val end = start + length
        if (end > encoded.length) return null
        route += encoded.substring(start, end)
        offset = end
    }
    return route.takeIf { it.isNotEmpty() }
}

private fun readWebProfileRouteSnapshot(): WebProfileRouteSnapshot? {
    val origin = readWebProfileRouteOrigin() ?: return null
    val route = decodeWebProfileRoute(readWebProfileRouteIds()) ?: return null
    return WebProfileRouteSnapshot(readWebProfileRouteActor(), origin, route)
}

private fun writeWebProfileRouteSnapshot(snapshot: WebProfileRouteSnapshot) {
    writeWebProfileRouteStorage(snapshot.actorId, snapshot.originFragment, encodeWebProfileRoute(snapshot.route))
}

internal fun restorePendingCommunityProfileAfterAuthentication(
    coordinator: AuthenticationContinuationCoordinator,
    profileRoute: WebFeedMemberProfileRoute,
) {
    coordinator.pendingIntent()?.contextId?.let(profileRoute::open)
}

@JsFun("""(profileId) => {
  const root = globalThis.document?.documentElement;
  if (!root) return;
  if (profileId) root.setAttribute('data-quata-member-profile-id', profileId);
  else root.removeAttribute('data-quata-member-profile-id');
}""")
private external fun setWebMemberProfileRouteMarker(profileId: String?)

@JsFun("() => globalThis.sessionStorage?.getItem('quata.web.profile-route.actor') ?? null")
private external fun readWebProfileRouteActor(): String?

@JsFun("() => globalThis.sessionStorage?.getItem('quata.web.profile-route.origin') ?? null")
private external fun readWebProfileRouteOrigin(): String?

@JsFun("() => globalThis.sessionStorage?.getItem('quata.web.profile-route.ids') ?? null")
private external fun readWebProfileRouteIds(): String?

@JsFun("""(actorId, origin, ids) => {
  const storage = globalThis.sessionStorage;
  if (!storage) return;
  if (actorId) storage.setItem('quata.web.profile-route.actor', actorId);
  else storage.removeItem('quata.web.profile-route.actor');
  storage.setItem('quata.web.profile-route.origin', origin);
  storage.setItem('quata.web.profile-route.ids', ids);
}""")
private external fun writeWebProfileRouteStorage(actorId: String?, origin: String, ids: String)

@JsFun("""() => {
  const storage = globalThis.sessionStorage;
  storage?.removeItem('quata.web.profile-route.actor');
  storage?.removeItem('quata.web.profile-route.origin');
  storage?.removeItem('quata.web.profile-route.ids');
}""")
private external fun clearWebProfileRouteSnapshot()
