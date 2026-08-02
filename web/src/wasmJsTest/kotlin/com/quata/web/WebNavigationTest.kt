package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebNavigationTest {
    @Test
    fun resolvesNamedBrowserRoutesIndependentlyOfCaseAndSlashes() {
        assertRoute("auth", "auth".toWebNavigationState())
        assertRoute("auth", "/LOGIN/".toWebNavigationState())
        assertRoute("settings", "/SETTINGS/".toWebNavigationState())
        assertRoute("whats-new", "WHATS-NEW".toWebNavigationState())
        assertRoute("about", "about".toWebNavigationState())
        assertRoute("notifications", "notifications".toWebNavigationState())
        assertRoute("profile", "profile".toWebNavigationState())
        assertRoute("composer", "composer".toWebNavigationState())
        assertRoute("communities", "communities".toWebNavigationState())
        assertRoute("official", "official".toWebNavigationState())
        assertRoute("chat", "chat".toWebNavigationState())
    }

    @Test
    fun mapsTheCanonicalPrimaryNavigationToExistingWebHashes() {
        assertEquals(
            listOf("communities", "chat", "official", "", "profile", "composer"),
            listOf("neighborhoods", "conversations", "official", "feed", "profile", "composer").map(::canonicalPrimaryRouteToWebFragment),
        )
        assertEquals("neighborhoods", webFragmentToCanonicalPrimaryRoute("communities"))
        assertEquals("conversations", webFragmentToCanonicalPrimaryRoute("chat/thread-1"))
        assertEquals("feed", webFragmentToCanonicalPrimaryRoute(""))
        assertEquals("composer", webFragmentToCanonicalPrimaryRoute("composer"))
    }

    @Test
    fun resolvesExternalShareTargetRoutes() {
        assertRoute("share-target", "share-target".toWebNavigationState())
        assertRoute("share-target-error", "SHARE-TARGET-ERROR".toWebNavigationState())
    }

    @Test
    fun resolvesFeedAndOfficialSharedPostDeepLinks() {
        val feedPost = "post-publication-123".toWebNavigationState()
        assertRoute("post/publication-123", feedPost)
        assertEquals("publication-123", feedPost.postId)
        assertNull(feedPost.officialPostId)

        val officialPost = "official-bulletin-99".toWebNavigationState()
        assertRoute("official/bulletin-99", officialPost)
        assertEquals("bulletin-99", officialPost.officialPostId)
        assertNull(officialPost.postId)
    }

    @Test
    fun resolvesEncodedChatThreadDeepLink() {
        val navigation = "chat-sb%3Ateam%2F42?message=msg%209".toWebNavigationState()

        assertRoute("chat/sb:team/42", navigation)
        assertEquals("sb:team/42", navigation.chatConversationId)
        assertNull(navigation.postId)
    }

    @Test
    fun fallsBackToFeedForUnknownOrMalformedDeepLinks() {
        assertRoute("feed", "".toWebNavigationState())
        assertRoute("feed", "feed".toWebNavigationState())
        assertRoute("feed", "unknown".toWebNavigationState())
        assertRoute("feed", "chat-".toWebNavigationState())
        assertRoute("feed", "post-".toWebNavigationState())
    }

    @Test
    fun classifiesPublicAndPrivateRoutesForThePermanentShell() {
        check("".toWebNavigationState().isPublicRoute)
        check("official-bulletin-99".toWebNavigationState().isPublicRoute)
        check("post-publication-123".toWebNavigationState().isPublicRoute)
        check("notifications".toWebNavigationState().isPublicRoute)
        check(!"notifications".toWebNavigationState().requiresAuthentication)
        check(!"chat".toWebNavigationState().isPublicRoute)
        check("chat".toWebNavigationState().requiresAuthentication)
        check("profile".toWebNavigationState().requiresAuthentication)
        check(!"auth".toWebNavigationState().requiresAuthentication)
    }

    @Test
    fun retainsTheExactFragmentAcrossNavigationControllerUpdates() {
        var browserFragment = ""
        val controller = WebNavigationController("chat-sb%3Ateam%2F42") { browserFragment = it }
        assertEquals("chat-sb%3Ateam%2F42", controller.fragment)
        controller.navigate("official-bulletin-99")
        assertEquals("official-bulletin-99", controller.fragment)
        assertEquals("official-bulletin-99", browserFragment)
    }

    private fun assertRoute(expected: String, navigation: WebNavigationState) {
        assertEquals(expected, navigation.route)
    }
}
