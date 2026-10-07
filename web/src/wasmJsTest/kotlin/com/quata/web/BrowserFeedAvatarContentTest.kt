package com.quata.web

import com.quata.core.navigation.AuthenticationContinuationCoordinator
import com.quata.core.navigation.AuthenticationContinuationIntent
import com.quata.core.navigation.AuthenticationContinuationKind

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserFeedAvatarContentTest {

    @Test
    fun loginFromCommunitiesDirectoryRestoresThePendingProfile() {
        val route = WebFeedMemberProfileRoute(navigateConversation = {})
        val coordinator = AuthenticationContinuationCoordinator().apply {
            request(
                AuthenticationContinuationIntent(
                    kind = AuthenticationContinuationKind.CommunityProfileEnsureFollow,
                    originRoute = "communities",
                    targetId = "member-2",
                    contextId = "member-2",
                    desiredState = true,
                ),
            )
        }

        restorePendingCommunityProfileAfterAuthentication(coordinator, route)

        assertEquals("member-2", route.profileId)
    }

    @Test
    fun loginFromSecondaryProfileRestoresItWithoutDuplicatingTheProfileStack() {
        val route = WebFeedMemberProfileRoute(navigateConversation = {})
        route.bindContext(actorId = "actor", originFragment = "communities", sessionResolved = true)
        route.acceptVisibleRoute(listOf("member-1", "member-2"))
        val coordinator = AuthenticationContinuationCoordinator().apply {
            request(
                AuthenticationContinuationIntent(
                    kind = AuthenticationContinuationKind.CommunityProfileAddComment,
                    originRoute = "communities",
                    targetId = "post-1",
                    relatedId = "comment-1",
                    contextId = "member-2",
                    text = "respuesta",
                ),
            )
        }

        restorePendingCommunityProfileAfterAuthentication(coordinator, route)

        assertEquals("member-2", route.profileId)
        assertEquals(listOf("member-1", "member-2"), route.profileRoute)
    }
    @Test
    fun only_http_avatar_urls_reach_the_native_browser_image_element() {
        assertTrue(isBrowserAvatarUrl("https://cdn.example.test/avatar?id=profile-1"))
        assertTrue(isBrowserAvatarUrl("http://localhost/avatar.png"))
        assertFalse(isBrowserAvatarUrl("file:///private/avatar.png"))
        assertFalse(isBrowserAvatarUrl("data:image/png;base64,abc"))
        assertFalse(isBrowserAvatarUrl(""))
    }

    @Test
    fun closing_a_feed_member_profile_consumes_the_request() {
        val route = WebFeedMemberProfileRoute(navigateConversation = {})
        route.open("profile-1")
        assertTrue(route.profileId == "profile-1")

        route.close()

        assertTrue(route.profileId == null)
    }

    @Test
    fun opening_a_conversation_consumes_the_profile_before_navigation() {
        val events = mutableListOf<String>()
        lateinit var route: WebFeedMemberProfileRoute
        route = WebFeedMemberProfileRoute(navigateConversation = { conversationId ->
            val profileState = if (route.profileId == null) "closed" else "open"
            events += "navigate:$conversationId:$profileState"
        })
        route.open("profile-1")

        route.openConversation("conversation-1")

        assertEquals(null, route.profileId)
        assertEquals(listOf("navigate:conversation-1:closed"), events)
    }

    @Test
    fun only_successful_visible_routes_are_persisted_and_restore_on_the_exact_actor_and_origin() {
        var stored: WebProfileRouteSnapshot? = null
        var clears = 0
        fun route() = WebFeedMemberProfileRoute(
            navigateConversation = {},
            readStoredSnapshot = { stored },
            writeStoredSnapshot = { stored = it },
            clearStoredSnapshot = { stored = null; clears += 1 },
        )
        val route = route()
        route.bindContext(actorId = "actor", originFragment = "feed-post-7", sessionResolved = true)
        route.open("profile-parent")
        assertEquals(null, stored)
        route.acceptVisibleRoute(listOf("profile-parent"))
        route.open("profile-child")
        assertEquals(listOf("profile-parent"), stored?.route)
        route.acceptVisibleRoute(listOf("profile-parent", "profile-child"))

        val restored = route()
        restored.bindContext(actorId = "actor", originFragment = "feed-post-7", sessionResolved = true)

        assertEquals("profile-child", restored.profileId)
        assertEquals(listOf("profile-parent", "profile-child"), restored.profileRoute)
        assertTrue(clears >= 1)
    }

    @Test
    fun actor_or_origin_change_clears_the_restored_profile_route() {
        var stored: WebProfileRouteSnapshot? = WebProfileRouteSnapshot(
            actorId = "actor-a",
            originFragment = "official-7",
            route = listOf("profile-1", "profile-2"),
        )
        val route = WebFeedMemberProfileRoute(
            navigateConversation = {},
            readStoredSnapshot = { stored },
            writeStoredSnapshot = { stored = it },
            clearStoredSnapshot = { stored = null },
        )
        route.bindContext(actorId = "actor-b", originFragment = "official-7", sessionResolved = true)

        assertEquals(null, route.profileId)
        assertEquals(emptyList(), route.profileRoute)
        assertEquals(null, stored)
    }

    @Test
    fun profile_route_codec_round_trips_unicode_and_delimiters() {
        val route = listOf("perfil:raiz", "niña/二", "emoji-🧭")

        assertEquals(route, decodeWebProfileRoute(encodeWebProfileRoute(route)))
    }

    @Test
    fun profile_route_codec_rejects_incomplete_or_empty_entries() {
        assertEquals(null, decodeWebProfileRoute(null))
        assertEquals(null, decodeWebProfileRoute(""))
        assertEquals(null, decodeWebProfileRoute("0:"))
        assertEquals(null, decodeWebProfileRoute("4:abc"))
        assertEquals(null, decodeWebProfileRoute("x:abc"))
    }
}
