package com.quata.feature.whatsnew.presentation

import com.quata.feature.whatsnew.domain.StartupDestination

enum class StartupRouteKind {
    Feed,
    Auth,
    Other,
    Unknown,
}

/**
 * Shared startup presentation rules for Android, Web and iOS launchers.
 *
 * Platform launchers own native session restoration and route rendering, but the late
 * What's New decision must never replace a route selected while startup work was running.
 */
object StartupPresentationPolicy {
    fun shouldEvaluateWhatsNew(
        isSessionResolved: Boolean,
        isAuthenticated: Boolean,
        hasEvaluated: Boolean,
    ): Boolean = isSessionResolved && isAuthenticated && !hasEvaluated

    fun destinationAfterEvaluation(
        routeKind: StartupRouteKind,
        decision: StartupDestination,
    ): StartupDestination = when {
        routeKind == StartupRouteKind.Feed && decision is StartupDestination.WhatsNew -> decision
        else -> StartupDestination.Main
    }

    fun shouldPresentWhatsNew(
        routeKind: StartupRouteKind,
        shouldShow: Boolean,
    ): Boolean = routeKind == StartupRouteKind.Feed && shouldShow
}

fun startupRouteKind(
    currentRoute: String?,
    feedRoute: String,
    authRoutes: Set<String> = emptySet(),
): StartupRouteKind = when {
    currentRoute == null -> StartupRouteKind.Unknown
    currentRoute == feedRoute -> StartupRouteKind.Feed
    currentRoute in authRoutes -> StartupRouteKind.Auth
    else -> StartupRouteKind.Other
}

fun shouldPresentStartupWhatsNew(
    isFeedVisible: Boolean,
    shouldShow: Boolean,
): Boolean = StartupPresentationPolicy.shouldPresentWhatsNew(
    routeKind = if (isFeedVisible) StartupRouteKind.Feed else StartupRouteKind.Other,
    shouldShow = shouldShow,
)
