package com.quata.core.navigation

sealed class AppDestinations(val route: String) {
    data object Login : AppDestinations("login")
    data object Register : AppDestinations("register")
    data object Feed : AppDestinations("feed")
    data object CreatePost : AppDestinations("create_post")
    data object Conversations : AppDestinations("conversations")
    data object Chat : AppDestinations("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
    data object Notifications : AppDestinations("notifications")
    data object Profile : AppDestinations("profile")
}
