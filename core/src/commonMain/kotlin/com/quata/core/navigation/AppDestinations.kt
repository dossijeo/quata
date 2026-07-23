package com.quata.core.navigation

sealed class AppDestinations(val route: String) {
    companion object {
        const val FavoriteMessagesConversationId = "__favorite_messages__"
    }

    data object Login : AppDestinations("login")
    data object Register : AppDestinations("register")
    data object ForgotPassword : AppDestinations("forgot_password")
    data object Feed : AppDestinations("feed")
    data object Neighborhoods : AppDestinations("neighborhoods")
    data object Official : AppDestinations("official")
    data object OfficialPostEditor : AppDestinations("official/editor")
    data object RichTextEditorQa : AppDestinations("debug/rich_text_editor_qa")
    data object CreatePost : AppDestinations("create_post")
    data object Conversations : AppDestinations("conversations")
    data object Chat : AppDestinations("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/${quataUrlEncode(conversationId)}"
    }
    data object Notifications : AppDestinations("notifications")
    data object ReleaseHistory : AppDestinations("release_history")
    data object Profile : AppDestinations("profile")
    data object UserProfile : AppDestinations("user_profile/{userId}") {
        fun createRoute(userId: String) = "user_profile/${quataUrlEncode(userId)}"
    }
}
