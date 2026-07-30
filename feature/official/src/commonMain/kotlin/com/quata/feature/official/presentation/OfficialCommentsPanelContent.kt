package com.quata.feature.official.presentation

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import com.quata.core.model.PostComment
import com.quata.core.ui.components.QuataCommentInputContent
import com.quata.core.ui.components.QuataCommentInputStrings
import com.quata.core.ui.components.QuataCommentRowContent
import com.quata.core.ui.components.QuataCommentRowStrings
import com.quata.core.ui.components.QuataCommentsPanelHeaderContent
import com.quata.core.ui.components.QuataCommentsPanelPortraitContent
import com.quata.core.ui.components.QuataStandardFloatingPanelContent
import com.quata.feature.official.domain.OfficialPostItem

/** Common Official comments sheet: targets only supply native translation/report navigation. */
@Composable
fun OfficialCommentsPanelContent(
    post: OfficialPostItem,
    canParticipate: Boolean,
    strings: OfficialCommentsStrings,
    onAuthRequired: () -> Unit,
    onAddComment: (PostComment) -> Unit,
    onReportComment: (PostComment) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(post.id) { mutableStateOf(TextFieldValue()) }
    QuataStandardFloatingPanelContent(onDismiss = onDismiss) { panelModifier, _ ->
        QuataCommentsPanelPortraitContent(
            header = {
                QuataCommentsPanelHeaderContent(strings.title, post.commentsCount, trailingAction = { modifier ->
                    TextButton(onClick = onDismiss, modifier = modifier) { Text(strings.close) }
                })
            },
            comments = { listModifier ->
                LazyColumn(listModifier) {
                    items(post.comments, key = PostComment::id) { comment ->
                        QuataCommentRowContent(
                            comment = comment,
                            timestamp = comment.timestamp,
                            strings = QuataCommentRowStrings(replyTo = { "" }, report = strings.report, reply = strings.reply),
                            onReply = {},
                            onReport = { onReportComment(comment) },
                        )
                    }
                }
            },
            input = { inputModifier ->
                QuataCommentInputContent(
                    postId = post.id,
                    draft = draft,
                    replyTarget = null,
                    canParticipate = canParticipate,
                    currentUserLabel = "",
                    strings = QuataCommentInputStrings(strings.placeholder, strings.send),
                    timestamp = { "" },
                    leadingAction = {},
                    onDraftChange = { draft = it },
                    onAuthRequired = onAuthRequired,
                    onAddComment = onAddComment,
                    onCommentAdded = { draft = TextFieldValue() },
                    onFocused = {},
                    modifier = inputModifier,
                )
            },
            modifier = panelModifier,
        )
    }
}

data class OfficialCommentsStrings(
    val title: String = "Comentarios",
    val close: String = "Cerrar",
    val placeholder: String = "Escribe un comentario",
    val send: String = "Enviar",
    val report: String = "Reportar",
    val reply: String = "Responder",
)
