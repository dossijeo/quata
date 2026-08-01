package com.quata.feature.neighborhoods.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
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
    canParticipate: Boolean,
    strings: CommunityProfileCommentsDialogStrings,
    onAuthRequired: () -> Unit,
    onSubmitComment: (draft: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(post.id) { mutableStateOf("") }
    var submittedAtCount by rememberSaveable(post.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(comments.size) {
        val previousCount = submittedAtCount
        if (previousCount != null && comments.size > previousCount) {
            draft = ""
            submittedAtCount = null
        }
    }
    CommunityProfileCommentsPanelContent(
        comments = comments,
        title = strings.title,
        closeContentDescription = strings.closeContentDescription,
        onDismiss = onDismiss,
        commentRow = { comment -> CommunityProfileCommentRowContent(comment) },
        input = {
            CommunityProfileCommentInputContent(
                value = draft,
                placeholder = strings.placeholder,
                sendLabel = strings.sendLabel,
                onValueChange = { draft = it },
                onSend = {
                    if (canParticipate) {
                        onSubmitComment(draft.trim())
                        submittedAtCount = comments.size
                    } else {
                        onAuthRequired()
                    }
                },
            )
        },
    )
}

data class CommunityProfileCommentsDialogStrings(
    val title: String,
    val closeContentDescription: String,
    val placeholder: String,
    val sendLabel: String,
)
