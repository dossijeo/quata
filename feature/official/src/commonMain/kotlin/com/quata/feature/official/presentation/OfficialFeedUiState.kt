package com.quata.feature.official.presentation

import com.quata.core.model.User
import com.quata.feature.official.domain.OfficialPostItem

data class OfficialFeedUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasMoreOlderPosts: Boolean = true,
    val posts: List<OfficialPostItem> = emptyList(),
    val currentUser: User? = null,
    val isPublishing: Boolean = false,
    val error: String? = null,
    val commentErrorsByPostId: Map<String, String> = emptyMap(),
    val commentErrorsByCommentId: Map<String, String> = emptyMap(),
    val confirmedCommentIds: Set<String> = emptySet(),
    val message: String? = null,
    val createdPostId: String? = null
)

object OfficialFeedMessages {
    const val CommentReported = "comment_reported"
    const val CommentReportFailed = "comment_report_failed"
    const val PostCreated = "post_created"
    const val PostDeleted = "post_deleted"
}
