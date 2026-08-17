package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.model.PostComment
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataFloatingPanelContent

const val PublicProfileCommentsPanelTestTag = "public-profile.comments.panel"
const val PublicProfileCommentsListTestTag = "public-profile.comments.list"
const val PublicProfileCommentsCloseTestTag = "public-profile.comments.close"

/** Shared profile-post comments panel. Hosts inject row and input behavior through slots. */
@Composable
fun CommunityProfileCommentsPanelContent(
    comments: List<PostComment>,
    title: String,
    closeContentDescription: String,
    onDismiss: () -> Unit,
    translatorAction: (@Composable (Modifier) -> Unit)? = null,
    commentRow: @Composable (PostComment) -> Unit,
    input: @Composable () -> Unit
) {
    QuataFloatingPanelContent(
        onDismiss = onDismiss,
        landscapeWidthFraction = 0.68f,
    ) { panelModifier, _ ->
        Column(panelModifier.padding(18.dp).semantics { testTag = PublicProfileCommentsPanelTestTag }) {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 48.dp)
                )
                translatorAction?.invoke(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 48.dp),
                )
                CompactIconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .semantics { testTag = PublicProfileCommentsCloseTestTag },
                ) {
                    CompactIcon(Icons.Filled.Close, contentDescription = closeContentDescription)
                }
            }
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .semantics { testTag = PublicProfileCommentsListTestTag },
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(comments, key = { it.id }) { comment -> commentRow(comment) }
            }
            Spacer(Modifier.height(14.dp))
            input()
        }
    }
}
