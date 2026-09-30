package com.quata.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthenticationContinuationCoordinatorTest {
    @Test
    fun matchingSurfaceClaimsAnIntentExactlyOnce() {
        val coordinator = AuthenticationContinuationCoordinator()
        val intent = AuthenticationContinuationIntent(
            kind = AuthenticationContinuationKind.FeedTogglePostLike,
            originRoute = "feed/post-7",
            targetId = "post-7",
        )
        val pending = coordinator.request(intent)

        assertEquals(intent, coordinator.claim(pending.requestId))
        assertNull(coordinator.claim(pending.requestId))
        assertNull(coordinator.pending.value)
    }

    @Test
    fun staleClaimCannotConsumeAReplacementIntent() {
        val coordinator = AuthenticationContinuationCoordinator()
        val first = coordinator.request(intent(AuthenticationContinuationKind.FeedOpenComposer))
        val replacement = coordinator.request(intent(AuthenticationContinuationKind.FeedReportPost))

        assertNull(coordinator.claim(first.requestId))
        assertEquals(replacement.intent, coordinator.pending.value?.intent)
        assertEquals(replacement.intent, coordinator.claim(replacement.requestId))
    }

    @Test
    fun promptDismissalClearsOnlyTheDisplayedRequest() {
        val coordinator = AuthenticationContinuationCoordinator()
        val first = coordinator.request(intent(AuthenticationContinuationKind.FeedOpenComposer))
        val replacement = coordinator.request(intent(AuthenticationContinuationKind.FeedReportComment))

        assertFalse(coordinator.clear(first.requestId))
        assertTrue(coordinator.clear(replacement.requestId))
        assertNull(coordinator.pending.value)
    }

    @Test
    fun markerNeverContainsTargetsOrUserAuthoredText() {
        val coordinator = AuthenticationContinuationCoordinator()
        val pending = coordinator.request(
            AuthenticationContinuationIntent(
                kind = AuthenticationContinuationKind.FeedAddComment,
                originRoute = "feed/post-11",
                targetId = "post-11",
                relatedId = "comment-4",
                text = "private draft",
            ),
        )

        assertEquals(
            AuthenticationContinuationMarker(
                requestId = pending.requestId,
                kind = AuthenticationContinuationKind.FeedAddComment,
                originRoute = "feed/post-11",
            ),
            coordinator.marker(),
        )
    }

    @Test
    fun abandoningAuthenticationClearsEveryPendingField() {
        val coordinator = AuthenticationContinuationCoordinator()
        coordinator.request(
            AuthenticationContinuationIntent(
                kind = AuthenticationContinuationKind.FeedReportPost,
                originRoute = "feed/post-9",
                targetId = "post-9",
            ),
        )

        coordinator.clearAll()

        assertNull(coordinator.pending.value)
        assertNull(coordinator.marker())
    }

    private fun intent(kind: AuthenticationContinuationKind) = AuthenticationContinuationIntent(
        kind = kind,
        originRoute = "feed",
    )
}
