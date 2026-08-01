package com.quata.feature.neighborhoods.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.quata.core.model.Post
import com.quata.core.model.PostComment

/**
 * Shared profile-gallery comments overlay.
 *
 * The component owns only portable draft state and composition of the common comments panel.
 * Hosts retain authorization and persistence through [onSubmitComment]. The draft is cleared only
 * after refreshed repository data confirms a new comment; no client-generated identity enters the timeline.
 */
@Composable
fun CommunityProfileCommentsDialogContent(
    post: Post,
    comments: List<PostComment>,
    currentUserId: String?,
    strings: CommunityProfileCommentsDialogStrings,
    errorMessage: String?,
    onAuthRequired: () -> Unit,
    onSubmitComment: (draft: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(post.id) { mutableStateOf("") }
    var submittedDraft by rememberSaveable(post.id) { mutableStateOf<String?>(null) }
    var commentIdsBeforeSubmit by rememberSaveable(post.id) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(comments, currentUserId, submittedDraft) {
        val submitted = submittedDraft
        val remoteConfirmation = isRemoteProfileCommentConfirmation(comments, currentUserId, submitted, commentIdsBeforeSubmit)
        if (remoteConfirmation) {
            draft = ""
            submittedDraft = null
            commentIdsBeforeSubmit = emptyList()
        }
    }
    CommunityProfileCommentsPanelContent(
        comments = comments,
        title = strings.title,
        closeContentDescription = strings.closeContentDescription,
        onDismiss = onDismiss,
        commentRow = { comment -> CommunityProfileCommentRowContent(comment) },
        input = {
            Column {
                errorMessage?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                CommunityProfileCommentInputContent(
                    value = draft,
                    placeholder = strings.placeholder,
                    sendLabel = strings.sendLabel,
                    onValueChange = { draft = it },
                    onSend = {
                        if (!currentUserId.isNullOrBlank()) {
                            val cleanDraft = draft.trim()
                            commentIdsBeforeSubmit = comments.map(PostComment::id)
                            submittedDraft = cleanDraft
                            onSubmitComment(cleanDraft)
                        } else {
                            onAuthRequired()
                        }
                    },
                )
            }
        },
    )
}

internal fun isRemoteProfileCommentConfirmation(
    comments: List<PostComment>,
    currentUserId: String?,
    submittedDraft: String?,
    commentIdsBeforeSubmit: List<String>,
): Boolean = submittedDraft != null && comments.any { comment ->
    comment.id !in commentIdsBeforeSubmit && comment.authorId == currentUserId && comment.message.trim() == submittedDraft
}

data class CommunityProfileCommentsDialogStrings(
    val title: String,
    val closeContentDescription: String,
    val placeholder: String,
    val sendLabel: String,
)
