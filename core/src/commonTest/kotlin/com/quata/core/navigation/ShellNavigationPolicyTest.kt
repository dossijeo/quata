package com.quata.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShellNavigationPolicyTest {
    @Test
    fun appDestinationRoutesClassifyPublicAuthenticationAndPrivateShellAccess() {
        listOf(
            AppDestinations.Feed.route,
            AppDestinations.Neighborhoods.route,
            AppDestinations.Official.route,
            AppDestinations.Notifications.route,
            AppDestinations.WhatsNew.route,
            AppDestinations.About.route,
            AppDestinations.ReleaseHistory.route,
        ).forEach { route ->
            assertEquals(QuataShellRouteAccess.Public, quataAppDestinationRouteAccess(route), route)
            assertFalse(route.requiresQuataAppDestinationAuthentication(), route)
        }

        listOf(
            AppDestinations.Login.route,
            AppDestinations.Register.route,
            AppDestinations.ForgotPassword.route,
        ).forEach { route ->
            assertEquals(QuataShellRouteAccess.Authentication, quataAppDestinationRouteAccess(route), route)
            assertFalse(route.requiresQuataAppDestinationAuthentication(), route)
        }

        listOf(
            AppDestinations.Conversations.route,
            AppDestinations.Chat.createRoute("sb:42"),
            AppDestinations.Profile.route,
            AppDestinations.CreatePost.route,
            AppDestinations.OfficialPostEditor.route,
            AppDestinations.UserProfile.createRoute("profile-1"),
        ).forEach { route ->
            assertEquals(QuataShellRouteAccess.Private, quataAppDestinationRouteAccess(route), route)
            assertTrue(route.requiresQuataAppDestinationAuthentication(), route)
            assertTrue(quataAppDestinationRequiresAuthentication(route), route)
        }
    }

    @Test
    fun webRoutesUseTheSameAccessContractWithWebFragments() {
        listOf(
            "feed",
            "communities",
            "official",
            "notifications",
            "whats-new",
            "about",
            "release-history",
        ).forEach { route ->
            assertEquals(QuataShellRouteAccess.Public, quataWebRouteAccess(route), route)
        }

        assertEquals(QuataShellRouteAccess.Authentication, quataWebRouteAccess("auth"))
        assertEquals(QuataShellRouteAccess.Public, quataWebRouteAccess("post/publication-123", hasFeedPostTarget = true))
        assertEquals(QuataShellRouteAccess.Public, quataWebRouteAccess("official/bulletin-99", hasOfficialPostTarget = true))

        listOf("chat", "chat/sb:42", "profile", "composer", "settings", "share-target", "official-editor").forEach { route ->
            assertEquals(QuataShellRouteAccess.Private, quataWebRouteAccess(route), route)
        }
        assertEquals(QuataShellRouteAccess.Private, quataWebRouteAccess("post/"))
    }
}
