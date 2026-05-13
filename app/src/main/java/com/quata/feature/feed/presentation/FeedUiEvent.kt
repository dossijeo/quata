package com.quata.feature.feed.presentation

sealed class FeedUiEvent {
    data object Refresh : FeedUiEvent()
    data class PostDisplayed(val postId: String) : FeedUiEvent()
}
