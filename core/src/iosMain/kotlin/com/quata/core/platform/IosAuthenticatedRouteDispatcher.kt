package com.quata.core.platform

import com.quata.core.navigation.QuataDeepLinkTarget

/**
 * Narrow UIKit-facing route contract for the authenticated iOS composition root.
 *
 * The common deep-link parser remains the authority for the destination and identifiers. Swift
 * owns the actual controller transition and supplies real feature factories only after their
 * repositories are available. This prevents the iOS host from manufacturing a Chat, Official or
 * Notifications repository merely to satisfy a navigation event.
 */
interface IosAuthenticatedRouteHost {
    fun showFeed(postId: String?)
    fun showChat(conversationId: String, messageId: String?)
    fun showOfficial(postId: String?)
    fun showNotifications()
    fun showProfileSos()
    fun showCommunities()
    fun showComposer()
    fun showSettings()
    fun showWhatsNew()
    fun showAbout()
    fun showReleaseHistory()
}

/**
 * Adapts portable deep-link targets to primitive UIKit route arguments.
 *
 * `Notifications` is intentionally exposed as a first-class host operation even though public
 * notification payloads currently resolve to Chat targets. It lets the authenticated launcher
 * present the shared Notifications Compose host from an in-app affordance without adding UIKit
 * knowledge to common navigation.
 */
class IosAuthenticatedRouteDispatcher(
    private val host: IosAuthenticatedRouteHost,
) : IosDeepLinkDestinationHost {
    override fun open(target: QuataDeepLinkTarget) {
        when (target) {
            is QuataDeepLinkTarget.FeedPost -> host.showFeed(target.postId)
            is QuataDeepLinkTarget.Chat -> host.showChat(
                conversationId = target.target.conversationId,
                messageId = target.target.messageId,
            )
            is QuataDeepLinkTarget.OfficialPost -> host.showOfficial(target.postId)
            QuataDeepLinkTarget.RichTextEditorQa -> host.showOfficial(postId = null)
            QuataDeepLinkTarget.WhatsNew -> host.showWhatsNew()
            QuataDeepLinkTarget.About -> host.showAbout()
            QuataDeepLinkTarget.ReleaseHistory -> host.showReleaseHistory()
        }
    }

    fun openNotifications() {
        host.showNotifications()
    }

    /** Profile/SOS has no public URL contract yet, but remains a first-class authenticated route. */
    fun openProfileSos() {
        host.showProfileSos()
    }

    /** Communities has no public deep-link contract yet; this is an authenticated in-app route. */
    fun openCommunities() {
        host.showCommunities()
    }

    /** Composer is an authenticated in-app route; there is intentionally no public URL parser. */
    fun openComposer() {
        host.showComposer()
    }

    /** Appearance preferences are local iOS settings and have no public URL contract. */
    fun openSettings() {
        host.showSettings()
    }

    /** Local release catalog; callable from an in-app menu and from the public deep-link parser. */
    fun openWhatsNew() {
        host.showWhatsNew()
    }

    /** Portable About surface, callable from in-app chrome and public deep links. */
    fun openAbout() {
        host.showAbout()
    }

    /** Complete local release history, shared by About/menu and public deep links. */
    fun openReleaseHistory() {
        host.showReleaseHistory()
    }
}
