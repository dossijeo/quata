package com.quata.feature.official.presentation

import androidx.compose.runtime.Composable
import com.quata.core.model.PostComment
import com.quata.feature.official.domain.OfficialPostItem

/**
 * Resolves the post currently being discussed and keeps the Official comment actions in common
 * presentation code. The actual panel is supplied by the platform host because it owns avatar
 * rendering, localized resources, translation/navigation affordances, IME and platform actions.
 *
 * This deliberately does not prescribe a modal implementation: Android may use its existing
 * comments sheet while Web and iOS can provide their own accessible host without duplicating the
 * Official state/authentication rules.
 */
@Composable
fun OfficialCommentsPanelEntryContent(
    selectedPost: OfficialPostItem?,
    posts: List<OfficialPostItem>,
    currentUserId: String?,
    onAuthRequired: () -> Unit,
    onAddComment: (postId: String, comment: PostComment) -> Unit,
    onReportComment: (commentId: String) -> Unit,
    onDismiss: () -> Unit,
    panel: @Composable (
        post: OfficialPostItem,
        canParticipate: Boolean,
        onAddComment: (PostComment) -> Unit,
        onReportComment: (PostComment) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit,
) {
    val selected = selectedPost ?: return
    val currentPost = posts.firstOrNull { it.id == selected.id } ?: selected
    val canParticipate = currentUserId != null

    panel(
        currentPost,
        canParticipate,
        onAddComment = { comment ->
            if (canParticipate) {
                onAddComment(currentPost.id, comment)
            } else {
                onAuthRequired()
            }
        },
        onReportComment = { comment ->
            if (canParticipate) {
                onReportComment(comment.id)
            } else {
                onAuthRequired()
            }
        },
        onDismiss = onDismiss,
    )
}
