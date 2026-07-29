package com.quata.core.navigation

/** The only destinations displayed by the authenticated primary navigation chrome. */
data class PrimaryNavigationDestination(val route: String)

val primaryNavigationDestinations = listOf(
    PrimaryNavigationDestination(AppDestinations.Neighborhoods.route),
    PrimaryNavigationDestination(AppDestinations.Conversations.route),
    PrimaryNavigationDestination(AppDestinations.Official.route),
    PrimaryNavigationDestination(AppDestinations.Feed.route),
    PrimaryNavigationDestination(AppDestinations.Profile.route),
)
