package com.quata.core.platform

import com.quata.core.navigation.QuataChatDeepLink
import com.quata.core.navigation.QuataDeepLinkTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Pure routing contracts for the Apple targets. They deliberately have no UIKit runtime
 * dependency, so macOS CI can validate URL/notification routing without a simulator.
 */
class IosNavigationRoutingContractTest {
    @Test
    fun publicDeepLinksDispatchOnlyAfterAnAuthenticatedHostIsAttached() {
        val dispatcher = IosDeepLinkDispatcher()

        assertIs<PlatformResult.Unsupported>(dispatcher.handleUrl("https://egquata.com/#chat-sb%3A7?message=m-4"))

        val host = RecordingDestinationHost()
        dispatcher.attachHost(host)
        assertIs<PlatformResult.Success<Unit>>(dispatcher.handleUrl("https://egquata.com/#chat-sb%3A7?message=m-4"))
        assertEquals(QuataDeepLinkTarget.Chat(QuataChatDeepLink("sb:7", "m-4")), host.target)

        dispatcher.detachHost(host)
        assertIs<PlatformResult.Unsupported>(dispatcher.handleUrl("https://egquata.com/#release-history"))
    }

    @Test
    fun malformedOrNonQuataLinksAreExplicitFailuresRatherThanFallbackRoutes() {
        val dispatcher = IosDeepLinkDispatcher()
        dispatcher.attachHost(RecordingDestinationHost())

        assertIs<PlatformResult.Failure>(dispatcher.handleUrl("https://example.com/#chat-sb%3A7"))
        assertIs<PlatformResult.Failure>(dispatcher.handleUrl("https://egquata.com/#composer"))
        assertIs<PlatformResult.Failure>(dispatcher.handleUrl("other://egquata.com/#post-7"))
        assertIs<PlatformResult.Failure>(dispatcher.handleUrl("quata://www.egquata.com/#post-7"))
    }

    @Test
    fun registeredCustomSchemeRoutesPostOfficialAndChatThroughTheSharedParser() {
        val dispatcher = IosDeepLinkDispatcher()
        val host = RecordingDestinationHost()
        dispatcher.attachHost(host)

        assertIs<PlatformResult.Success<Unit>>(dispatcher.handleUrl("quata://egquata.com/#post-video-7"))
        assertEquals(QuataDeepLinkTarget.FeedPost("video-7"), host.target)

        assertIs<PlatformResult.Success<Unit>>(dispatcher.handleUrl("quata://egquata.com/#official-news-8"))
        assertEquals(QuataDeepLinkTarget.OfficialPost("news-8"), host.target)

        assertIs<PlatformResult.Success<Unit>>(dispatcher.handleUrl("quata://egquata.com/#chat-sb%3A9?message=m-3"))
        assertEquals(QuataDeepLinkTarget.Chat(QuataChatDeepLink("sb:9", "m-3")), host.target)
    }

    @Test
    fun authenticatedMenuOnlyRoutesToTheRequestedVertical() {
        val host = RecordingAuthenticatedRouteHost()
        val dispatcher = IosAuthenticatedRouteDispatcher(host)

        dispatcher.openNotifications()
        dispatcher.openProfileSos()
        dispatcher.openCommunities()
        dispatcher.openComposer()
        dispatcher.openSettings()
        dispatcher.openWhatsNew()
        dispatcher.openAbout()
        dispatcher.openReleaseHistory()

        assertEquals(
            listOf("notifications", "profileSos", "communities", "composer", "settings", "whatsNew", "about", "releaseHistory"),
            host.calls,
        )
    }

    @Test
    fun authenticatedChatDispatcherPropagatesConversationAndMessageTargetTogether() {
        val host = RecordingAuthenticatedRouteHost()

        IosAuthenticatedRouteDispatcher(host).open(
            QuataDeepLinkTarget.Chat(QuataChatDeepLink("conversation-7", "message-4")),
        )

        assertEquals("conversation-7" to "message-4", host.chatTarget)
    }

    private class RecordingDestinationHost : IosDeepLinkDestinationHost {
        var target: QuataDeepLinkTarget? = null
        override fun open(target: QuataDeepLinkTarget) { this.target = target }
    }

    private class RecordingAuthenticatedRouteHost : IosAuthenticatedRouteHost {
        val calls = mutableListOf<String>()
        var chatTarget: Pair<String, String?>? = null
        override fun showFeed(postId: String?) { calls += "feed" }
        override fun showChat(conversationId: String, messageId: String?) {
            calls += "chat"
            chatTarget = conversationId to messageId
        }
        override fun showOfficial(postId: String?) { calls += "official" }
        override fun showNotifications() { calls += "notifications" }
        override fun showProfileSos() { calls += "profileSos" }
        override fun showCommunities() { calls += "communities" }
        override fun showComposer() { calls += "composer" }
        override fun showSettings() { calls += "settings" }
        override fun showWhatsNew() { calls += "whatsNew" }
        override fun showAbout() { calls += "about" }
        override fun showReleaseHistory() { calls += "releaseHistory" }
    }
}
