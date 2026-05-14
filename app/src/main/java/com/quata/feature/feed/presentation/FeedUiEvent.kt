package com.quata.feature.feed.presentation

import com.quata.core.model.PostComment

sealed class FeedUiEvent {
    data object Refresh : FeedUiEvent()
    data class PostDisplayed(val postId: String) : FeedUiEvent()
    data class ToggleLike(val postId: String) : FeedUiEvent()
    data class ReportPost(val postId: String) : FeedUiEvent()
    data class AddComment(val postId: String, val comment: PostComment) : FeedUiEvent()
}
