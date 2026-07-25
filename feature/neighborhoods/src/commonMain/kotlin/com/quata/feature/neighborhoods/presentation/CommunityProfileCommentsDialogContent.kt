package com.quata.feature.neighborhoods.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.quata.core.model.Post
import com.quata.core.model.PostComment

/**
 * Shared profile-gallery comments overlay.
 *
 * The component owns only portable draft state and composition of the common comments panel.
 * Hosts retain authorization, persistence and local-comment identity through [createComment] and
 * [onAddComment], so no platform clock, resource or navigation API leaks into commonMain.
 */
@Composable
fun CommunityProfileCommentsDialogContent(
    post: Post,
    localComments: List<PostComment>,
    canParticipate: Boolean,
    strings: CommunityProfileCommentsDialogStrings,
    onAuthRequired: () -> Unit,
    createComment: (draft: String) -> PostComment,
    onAddComment: (PostComment) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(post.id) { mutableStateOf("") }
    CommunityProfileCommentsPanelContent(
        comments = post.comments + localComments,
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
                        onAddComment(createComment(draft.trim()))
                        draft = ""
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
