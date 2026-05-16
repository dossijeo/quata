package com.quata.core.navigation

import android.net.Uri

sealed class AppDestinations(val route: String) {
    companion object {
        const val FavoriteMessagesConversationId = "__favorite_messages__"
    }
    data object Login : AppDestinations("login")
    data object Register : AppDestinations("register")
    data object ForgotPassword : AppDestinations("forgot_password")
    data object Feed : AppDestinations("feed")
    data object Neighborhoods : AppDestinations("neighborhoods")
    data object CreatePost : AppDestinations("create_post")
    data object Conversations : AppDestinations("conversations")
    data object Chat : AppDestinations("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/${Uri.encode(conversationId)}"
    }
    data object Notifications : AppDestinations("notifications")
    data object Profile : AppDestinations("profile")
    data object UserProfile : AppDestinations("user_profile/{userId}") {
        fun createRoute(userId: String) = "user_profile/$userId"
    }
}
