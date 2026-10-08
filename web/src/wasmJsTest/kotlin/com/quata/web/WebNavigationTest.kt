package com.quata.web

import com.quata.core.navigation.quataChatUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertRoute("official-editor", "official-editor".toWebNavigationState())
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
        assertEquals("official", webFragmentToCanonicalPrimaryRoute("official-editor"))
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
        assertEquals("msg 9", navigation.chatMessageId)
        assertNull(navigation.postId)
    }

    @Test
    fun favoriteNavigationPreservesTheExactSourceMessage() {
        var browserFragment = ""
        val controller = WebNavigationController("chat", updateBrowserFragment = { browserFragment = it })

        controller.navigateConversation("sb:team/42", "msg 9")

        assertEquals("sb:team/42", controller.chatConversationId)
        assertEquals("msg 9", controller.chatMessageId)
        assertEquals("chat-sb%3Ateam%2F42?message=msg%209", browserFragment)
    }

    @Test
    fun communityConversationReturnsToCommunitiesWhileOtherChatsReturnToTheInbox() {
        var browserFragment = "communities"
        val controller = WebNavigationController("communities", updateBrowserFragment = { browserFragment = it })

        controller.navigateConversation("sb:42", returnFragment = "communities")
        controller.navigateBackFromConversation()

        assertRoute("communities", controller.state)
        assertEquals("communities", browserFragment)

        controller.navigateConversation("sb:43")
        controller.navigateBackFromConversation()

        assertRoute("chat", controller.state)
        assertEquals("chat", browserFragment)
    }

    @Test
    fun communityConversationReturnSurvivesAFullDocumentReload() {
        var browserFragment = "communities"
        var storedConversationId: String? = null
        var storedReturnFragment: String? = null
        val readReturn: (String) -> String? = { conversationId ->
            storedReturnFragment.takeIf { storedConversationId == conversationId }
        }
        val writeReturn: (String, String) -> Unit = { conversationId, fragment ->
            storedConversationId = conversationId
            storedReturnFragment = fragment
        }
        val clearReturn: () -> Unit = {
            storedConversationId = null
            storedReturnFragment = null
        }
        val firstDocument = WebNavigationController(
            initialFragment = "communities",
            updateBrowserFragment = { browserFragment = it },
            readConversationReturn = readReturn,
            writeConversationReturn = writeReturn,
            clearConversationReturn = clearReturn,
        )
        firstDocument.navigateConversation("sb:team/42", "message 9", returnFragment = "communities")

        val reloadedDocument = WebNavigationController(
            initialFragment = browserFragment,
            updateBrowserFragment = { browserFragment = it },
            readConversationReturn = readReturn,
            writeConversationReturn = writeReturn,
            clearConversationReturn = clearReturn,
        )
        assertEquals("sb:team/42", reloadedDocument.chatConversationId)
        assertEquals("message 9", reloadedDocument.chatMessageId)
        reloadedDocument.navigateBackFromConversation()

        assertRoute("communities", reloadedDocument.state)
        assertEquals("communities", browserFragment)
        assertNull(storedConversationId)
        assertNull(storedReturnFragment)
    }

    @Test
    fun notificationConversationReturnsToNotificationsAfterFullDocumentReload() {
        var browserFragment = "notifications"
        var storedConversationId: String? = null
        var storedReturnFragment: String? = null
        val readReturn: (String) -> String? = { conversationId ->
            storedReturnFragment.takeIf { storedConversationId == conversationId }
        }
        val writeReturn: (String, String) -> Unit = { conversationId, fragment ->
            storedConversationId = conversationId
            storedReturnFragment = fragment
        }
        val clearReturn: () -> Unit = {
            storedConversationId = null
            storedReturnFragment = null
        }
        val firstDocument = WebNavigationController(
            initialFragment = browserFragment,
            updateBrowserFragment = { browserFragment = it },
            readConversationReturn = readReturn,
            writeConversationReturn = writeReturn,
            clearConversationReturn = clearReturn,
        )

        firstDocument.navigateConversation(
            conversationId = "sb:notifications/42",
            messageId = "message 9/á",
            returnFragment = "notifications",
        )

        val reloadedDocument = WebNavigationController(
            initialFragment = browserFragment,
            updateBrowserFragment = { browserFragment = it },
            readConversationReturn = readReturn,
            writeConversationReturn = writeReturn,
            clearConversationReturn = clearReturn,
        )
        assertEquals("sb:notifications/42", reloadedDocument.chatConversationId)
        assertEquals("message 9/á", reloadedDocument.chatMessageId)

        reloadedDocument.navigateBackFromConversation()

        assertRoute("notifications", reloadedDocument.state)
        assertEquals("notifications", browserFragment)
        assertNull(storedConversationId)
        assertNull(storedReturnFragment)
    }

    @Test
    fun notificationReturnIsBoundToTheExactConversation() {
        var browserFragment = "notifications"
        val storedReturns = mutableMapOf<String, String>()
        val controller = WebNavigationController(
            initialFragment = browserFragment,
            updateBrowserFragment = { browserFragment = it },
            readConversationReturn = storedReturns::get,
            writeConversationReturn = storedReturns::set,
            clearConversationReturn = storedReturns::clear,
        )
        controller.navigateConversation("sb:notification-source", returnFragment = "notifications")

        controller.acceptBrowserFragment(quataChatUrl("sb:unrelated").substringAfter('#'))
        controller.navigateBackFromConversation()

        assertRoute("chat", controller.state)
        assertEquals("chat", browserFragment)
        assertTrue(storedReturns.isEmpty())
    }

    @Test
    fun changingTheCommunityConversationClearsItsReturnTarget() {
        var browserFragment = "communities"
        val controller = WebNavigationController("communities", updateBrowserFragment = { browserFragment = it })

        controller.navigateConversation("sb:42", returnFragment = "communities")
        controller.acceptBrowserFragment("chat-sb%3A43")
        controller.navigateBackFromConversation()

        assertRoute("chat", controller.state)
        assertEquals("chat", browserFragment)
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
        listOf("whats-new", "about", "release-history").forEach { route ->
            check(route.toWebNavigationState().isPublicRoute)
            check(!route.toWebNavigationState().requiresAuthentication)
        }
        check(!"chat".toWebNavigationState().isPublicRoute)
        check("chat".toWebNavigationState().requiresAuthentication)
        check("profile".toWebNavigationState().requiresAuthentication)
        check("official-editor".toWebNavigationState().requiresAuthentication)
        check(!"official-editor".toWebNavigationState().isPublicRoute)
        check(!"auth".toWebNavigationState().requiresAuthentication)
    }

    @Test
    fun retainsTheExactFragmentAcrossNavigationControllerUpdates() {
        var browserFragment = ""
        val controller = WebNavigationController(
            "chat-sb%3Ateam%2F42",
            updateBrowserFragment = { browserFragment = it },
        )
        assertEquals("chat-sb%3Ateam%2F42", controller.fragment)
        controller.navigate("official-bulletin-99")
        assertEquals("official-bulletin-99", controller.fragment)
        assertEquals("official-bulletin-99", browserFragment)
    }

    @Test
    fun `anonymous notification click returns to feed with pending chat while swipe stays in inbox`() {
        val click = anonymousNotificationClickEffect("conversation-7")
        val swipe = anonymousNotificationSwipeEffect()
        assertEquals(true, click.navigateFeed)
        assertEquals("chat-conversation-7", click.pendingFragment)
        assertEquals(false, swipe.navigateFeed)
        assertNull(swipe.pendingFragment)
    }

    @Test
    fun `anonymous Notifications back returns to the public Feed transport`() {
        var browserFragment = "notifications"
        val controller = WebNavigationController("notifications", updateBrowserFragment = { browserFragment = it })

        controller.navigate("")

        assertRoute("feed", controller.state)
        assertEquals("", controller.fragment)
        assertEquals("", browserFragment)
        assertEquals(WebPostgrestAuthMode.Public, webFeedReadAuthMode(WebFeedReadOperation.Feed))
        assertEquals(WebPostgrestAuthMode.Public, webFeedReadAuthMode(WebFeedReadOperation.FeedProfiles))
    }

    private fun assertRoute(expected: String, navigation: WebNavigationState) {
        assertEquals(expected, navigation.route)
    }
}
