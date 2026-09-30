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
        val route = WebFeedMemberProfileRoute { }
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
        val route = WebFeedMemberProfileRoute { }
        route.open("member-1")
        route.open("member-2")
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
        route.close()

        assertEquals("member-1", route.profileId)
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
        val route = WebFeedMemberProfileRoute { }
        route.open("profile-1")
        assertTrue(route.profileId == "profile-1")

        route.close()

        assertTrue(route.profileId == null)
    }

    @Test
    fun opening_a_conversation_consumes_the_profile_before_navigation() {
        val events = mutableListOf<String>()
        lateinit var route: WebFeedMemberProfileRoute
        route = WebFeedMemberProfileRoute { conversationId ->
            val profileState = if (route.profileId == null) "closed" else "open"
            events += "navigate:$conversationId:$profileState"
        }
        route.open("profile-1")

        route.openConversation("conversation-1")

        assertEquals(null, route.profileId)
        assertEquals(listOf("navigate:conversation-1:closed"), events)
    }

    @Test
    fun closing_a_nested_profile_restores_its_parent_before_closing_the_surface() {
        val route = WebFeedMemberProfileRoute { }
        route.open("profile-parent")
        route.open("profile-child")

        route.close()
        assertEquals("profile-parent", route.profileId)

        route.close()
        assertEquals(null, route.profileId)
    }

    @Test
    fun reopening_the_current_profile_does_not_duplicate_the_stack_entry() {
        val route = WebFeedMemberProfileRoute { }
        route.open("profile-1")
        route.open("profile-1")

        route.close()

        assertEquals(null, route.profileId)
    }
}
