package com.quata.feature.official.presentation

import com.quata.core.model.User
import com.quata.feature.official.domain.OfficialPostItem

data class OfficialFeedUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasMoreOlderPosts: Boolean = true,
    val olderPageError: String? = null,
    val posts: List<OfficialPostItem> = emptyList(),
    val rankingPosts: List<OfficialPostItem>? = null,
    val isLoadingRanking: Boolean = false,
    val rankingError: String? = null,
    val focusedPostLoads: Map<String, OfficialFocusedPostLoad> = emptyMap(),
    val currentUser: User? = null,
    val isCurrentUserRoleResolved: Boolean = false,
    val isPublishing: Boolean = false,
    val error: String? = null,
    val commentErrorsByPostId: Map<String, String> = emptyMap(),
    val commentErrorsByCommentId: Map<String, String> = emptyMap(),
    val confirmedCommentIds: Set<String> = emptySet(),
    val message: String? = null,
    val createdPostId: String? = null
)

enum class OfficialFocusedPostLoad { Loading, Loaded, NotFound, Failed }

object OfficialFeedMessages {
    const val OlderPageLoadFailed = "older_page_load_failed"
    const val CommentReported = "comment_reported"
    const val CommentReportFailed = "comment_report_failed"
    const val PostCreated = "post_created"
    const val PostDeleted = "post_deleted"
}
