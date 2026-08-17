package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.PostComment

const val PublicProfileCommentRowTestTagPrefix = "public-profile.comments.row."
const val PublicProfileCommentAuthorTestTagPrefix = "public-profile.comments.author."
const val PublicProfileCommentReplyTestTagPrefix = "public-profile.comments.reply."
const val PublicProfileCommentReplyToTestTagPrefix = "public-profile.comments.replyTo."

/** Portable comment-card structure for the Community profile comments panel. */
@Composable
fun CommunityProfileCommentRowContent(
    comment: PostComment,
    modifier: Modifier = Modifier,
    onOpenAuthorProfile: (String) -> Unit = {},
    replyLabel: String? = null,
    replyToLabel: ((String) -> String)? = null,
    onReply: (() -> Unit)? = null,
) {
    val template = quataTheme()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = template.colors.surfaceAlt),
        modifier = modifier
            .fillMaxWidth()
            .semantics { testTag = PublicProfileCommentRowTestTagPrefix + comment.id }
            .border(1.dp, template.colors.divider, RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(12.dp)) {
            val authorId = comment.authorId?.takeIf(String::isNotBlank)
            val authorProfileTag = authorId?.let { PublicProfileCommentAuthorTestTagPrefix + it }
            Text(
                comment.authorName,
                fontWeight = FontWeight.Bold,
                modifier = if (authorId != null) {
                    Modifier
                        .semantics {
                            authorProfileTag?.let { testTag = it }
                            contentDescription = listOfNotNull("${comment.authorName} profile", authorProfileTag).joinToString(" ")
                        }
                        .clickable { onOpenAuthorProfile(authorId) }
                } else {
                    Modifier
                },
            )
            Spacer(Modifier.height(6.dp))
            comment.replyToAuthorName?.let { author ->
                val replyToText = replyToLabel?.invoke(author) ?: author
                Text(
                    replyToText,
                    color = template.colors.accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        testTag = PublicProfileCommentReplyToTestTagPrefix + comment.id
                        contentDescription = "$replyToText ${PublicProfileCommentReplyToTestTagPrefix + comment.id}"
                    },
                )
                comment.replyToMessage?.takeIf(String::isNotBlank)?.let { quoted ->
                    Spacer(Modifier.height(4.dp))
                    Text(quoted, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
            }
            Text(comment.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onReply != null && replyLabel != null) {
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onReply,
                        modifier = Modifier.semantics {
                            testTag = PublicProfileCommentReplyTestTagPrefix + comment.id
                            contentDescription = "$replyLabel ${PublicProfileCommentReplyTestTagPrefix + comment.id}"
                        },
                    ) {
                        Text(replyLabel, color = template.colors.accent, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
