package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.PostComment

data class QuataCommentRowStrings(val replyTo: (String) -> String, val report: String, val reply: String)

@Composable
fun QuataCommentRowContent(
    comment: PostComment,
    timestamp: String,
    strings: QuataCommentRowStrings,
    modifier: Modifier = Modifier,
    authorProfileTestTagPrefix: String? = null,
    replyTestTagPrefix: String? = null,
    reportTestTagPrefix: String? = null,
    replyQuoteTestTagPrefix: String? = null,
    onOpenAuthorProfile: (String) -> Unit = {},
    onReply: () -> Unit,
    onReport: () -> Unit
) {
    val template = quataTheme()
    Surface(color = template.colors.surface, contentColor = template.colors.textPrimary, shape = RoundedCornerShape(18.dp), modifier = modifier.fillMaxWidth().border(1.dp, template.colors.divider, RoundedCornerShape(18.dp))) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(14.dp)) {
            if (comment.replyToAuthorName != null) { Box(Modifier.fillMaxHeight().width(2.dp).background(template.colors.accent)); Spacer(Modifier.width(14.dp)) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    val authorId = comment.authorId?.takeIf(String::isNotBlank)
                    val authorProfileTag = authorId?.let { id -> authorProfileTestTagPrefix?.let { prefix -> prefix + id } }
                    val authorModifier = Modifier.weight(1f).then(
                        if (authorId != null) {
                            Modifier
                                .clickable { onOpenAuthorProfile(authorId) }
                                .semantics {
                                    authorProfileTag?.let { testTag = it }
                                    contentDescription = listOfNotNull("${comment.authorName} profile", authorProfileTag).joinToString(" ")
                                }
                        } else {
                            Modifier
                        }
                    )
                    Text(comment.authorName, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = template.colors.textPrimary, modifier = authorModifier)
                    Text(timestamp, color = template.colors.textSecondary, fontSize = 13.sp)
                }
                comment.replyToAuthorName?.let { author ->
                    val replyQuoteTag = replyQuoteTestTagPrefix?.let { it + comment.id }
                    Spacer(Modifier.height(8.dp)); Text(
                        strings.replyTo(author),
                        color = template.colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.semantics {
                            replyQuoteTag?.let { testTag = it }
                            contentDescription = listOfNotNull(strings.replyTo(author), replyQuoteTag).joinToString(" ")
                        },
                    )
                    comment.replyToMessage?.takeIf { it.isNotBlank() }?.let { quoted ->
                        Spacer(Modifier.height(4.dp)); QuataEmojiStaticText(quoted, template.colors.textSecondary, 13.sp, TextUnit.Unspecified, 2, TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(12.dp)); QuataEmojiStaticText(comment.message, template.colors.textPrimary, 16.sp, 21.sp, Int.MAX_VALUE, TextOverflow.Clip, modifier = Modifier.fillMaxWidth())
                Row(Modifier.align(Alignment.End)) {
                    val reportTag = reportTestTagPrefix?.let { it + comment.id }
                    val replyTag = replyTestTagPrefix?.let { it + comment.id }
                    TextButton(
                        onClick = onReport,
                        modifier = Modifier.semantics {
                            reportTag?.let { testTag = it }
                            contentDescription = listOfNotNull(strings.report, reportTag).joinToString(" ")
                        },
                    ) { Text(strings.report, color = template.colors.textSecondary) }
                    TextButton(
                        onClick = onReply,
                        modifier = Modifier.semantics {
                            replyTag?.let { testTag = it }
                            contentDescription = listOfNotNull(strings.reply, replyTag).joinToString(" ")
                        },
                    ) { Text(strings.reply, color = template.colors.accent, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}
