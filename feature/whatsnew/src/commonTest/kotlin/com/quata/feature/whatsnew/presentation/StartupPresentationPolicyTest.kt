package com.quata.feature.whatsnew.presentation

import com.quata.feature.whatsnew.domain.PendingRelease
import com.quata.feature.whatsnew.domain.StartupDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupPresentationPolicyTest {
    @Test
    fun startupWhatsNewEvaluatesOnlyAfterAuthenticatedSessionIsResolvedOnce() {
        assertFalse(
            StartupPresentationPolicy.shouldEvaluateWhatsNew(
                isSessionResolved = false,
                isAuthenticated = true,
                hasEvaluated = false,
            ),
        )
        assertFalse(
            StartupPresentationPolicy.shouldEvaluateWhatsNew(
                isSessionResolved = true,
                isAuthenticated = false,
                hasEvaluated = false,
            ),
        )
        assertFalse(
            StartupPresentationPolicy.shouldEvaluateWhatsNew(
                isSessionResolved = true,
                isAuthenticated = true,
                hasEvaluated = true,
            ),
        )
        assertTrue(
            StartupPresentationPolicy.shouldEvaluateWhatsNew(
                isSessionResolved = true,
                isAuthenticated = true,
                hasEvaluated = false,
            ),
        )
    }

    @Test
    fun lateWhatsNewDecisionCanOnlyReplaceVisibleFeed() {
        val whatsNew = StartupDestination.WhatsNew(
            listOf(
                PendingRelease(
                    releaseId = "qadata-startup-release",
                    versionCode = 4,
                    versionName = "1.4",
                    localizedNote = "Novedad de arranque",
                    availableLanguageTags = setOf("es"),
                ),
            ),
        )

        assertEquals(
            whatsNew,
            StartupPresentationPolicy.destinationAfterEvaluation(StartupRouteKind.Feed, whatsNew),
        )
        assertEquals(
            StartupDestination.Main,
            StartupPresentationPolicy.destinationAfterEvaluation(StartupRouteKind.Other, whatsNew),
        )
        assertEquals(
            StartupDestination.Main,
            StartupPresentationPolicy.destinationAfterEvaluation(StartupRouteKind.Auth, whatsNew),
        )
        assertEquals(
            StartupDestination.Main,
            StartupPresentationPolicy.destinationAfterEvaluation(StartupRouteKind.Unknown, whatsNew),
        )
    }

    @Test
    fun routeClassifierKeepsFeedAuthAndUnknownSeparate() {
        assertEquals(StartupRouteKind.Feed, startupRouteKind("feed", feedRoute = "feed", authRoutes = setOf("auth")))
        assertEquals(StartupRouteKind.Auth, startupRouteKind("auth", feedRoute = "feed", authRoutes = setOf("auth")))
        assertEquals(StartupRouteKind.Other, startupRouteKind("chat", feedRoute = "feed", authRoutes = setOf("auth")))
        assertEquals(StartupRouteKind.Unknown, startupRouteKind(null, feedRoute = "feed", authRoutes = setOf("auth")))
    }

    @Test
    fun swiftVisibleFeedBridgeUsesTheSamePolicy() {
        assertTrue(shouldPresentStartupWhatsNew(isFeedVisible = true, shouldShow = true))
        assertFalse(shouldPresentStartupWhatsNew(isFeedVisible = false, shouldShow = true))
        assertFalse(shouldPresentStartupWhatsNew(isFeedVisible = true, shouldShow = false))
    }
}
