package com.quata.feature.official.presentation

import com.quata.core.model.PostComment
import com.quata.feature.official.domain.OfficialPostDraft

sealed class OfficialFeedUiEvent {
    data object Refresh : OfficialFeedUiEvent()
    data object ClearMessage : OfficialFeedUiEvent()
    data class ToggleLike(val postId: String) : OfficialFeedUiEvent()
    data class AddComment(val postId: String, val comment: PostComment) : OfficialFeedUiEvent()
    data class DeletePost(val postId: String) : OfficialFeedUiEvent()
    data class CreatePost(val draft: OfficialPostDraft) : OfficialFeedUiEvent()
}
