package com.quata.feature.feed.presentation

sealed class FeedUiEvent {
    data object Refresh : FeedUiEvent()
}
