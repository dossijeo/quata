package com.quata.core.navigation

enum class QuataShellRouteAccess {
    Public,
    Authentication,
    Private,
}

fun quataAppDestinationRouteAccess(route: String): QuataShellRouteAccess = when (route) {
    AppDestinations.Login.route,
    AppDestinations.Register.route,
    AppDestinations.ForgotPassword.route -> QuataShellRouteAccess.Authentication
    AppDestinations.Feed.route,
    AppDestinations.Neighborhoods.route,
    AppDestinations.Official.route,
    AppDestinations.Notifications.route,
    AppDestinations.WhatsNew.route,
    AppDestinations.About.route,
    AppDestinations.ReleaseHistory.route,
        AppDestinations.RichTextEditorQa.route -> QuataShellRouteAccess.Public
        else -> QuataShellRouteAccess.Private
    }

fun String.requiresQuataAppDestinationAuthentication(): Boolean =
    quataAppDestinationRouteAccess(this) == QuataShellRouteAccess.Private

fun quataAppDestinationRequiresAuthentication(route: String): Boolean =
    route.requiresQuataAppDestinationAuthentication()

fun quataWebRouteAccess(
    route: String,
    hasFeedPostTarget: Boolean = false,
    hasOfficialPostTarget: Boolean = false,
): QuataShellRouteAccess = when (route.substringBefore('/')) {
    "auth" -> QuataShellRouteAccess.Authentication
    "feed",
    "communities",
    "official",
    "notifications",
    "whats-new",
    "about",
    "release-history" -> QuataShellRouteAccess.Public
    "post" -> if (hasFeedPostTarget) QuataShellRouteAccess.Public else QuataShellRouteAccess.Private
    else -> if (hasFeedPostTarget || hasOfficialPostTarget) QuataShellRouteAccess.Public else QuataShellRouteAccess.Private
}
