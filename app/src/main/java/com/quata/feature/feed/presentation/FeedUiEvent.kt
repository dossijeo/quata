package com.quata.feature.feed.presentation

import com.quata.core.model.PostComment

sealed class FeedUiEvent {
    data object Refresh : FeedUiEvent()
    data object LoadOlderPage : FeedUiEvent()
    data class PostDisplayed(val postId: String, val nextPostId: String? = null) : FeedUiEvent()
    data class ToggleLike(val postId: String) : FeedUiEvent()
    data class ReportPost(val postId: String) : FeedUiEvent()
    data class AddComment(val postId: String, val comment: PostComment) : FeedUiEvent()
    data class DeletePost(val postId: String) : FeedUiEvent()
}
