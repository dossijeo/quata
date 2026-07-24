package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ComposerPreviewActionLabels(
    val like: String,
    val comments: String,
    val share: String,
    val report: String,
    val rank: String,
    val live: String
)

/** Shared non-interactive action rail used by the composer preview. */
@Composable
fun ComposerPreviewActionsContent(
    showRankLiveActions: Boolean,
    labels: ComposerPreviewActionLabels,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showRankLiveActions) ComposerPreviewRankLiveActionsContent(labels)
        ComposerPreviewIconAction(Icons.Filled.FavoriteBorder, labels.like, "0")
        ComposerPreviewIconAction(Icons.Filled.ChatBubble, labels.comments, "0")
        ComposerPreviewIconAction(Icons.Filled.Share, labels.share)
        ComposerPreviewIconAction(Icons.Filled.Flag, labels.report)
    }
}

@Composable
fun ComposerPreviewRankLiveActionsContent(
    labels: ComposerPreviewActionLabels,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ComposerPreviewTextAction("\uD83D\uDD25", labels.rank, "3")
        ComposerPreviewTextAction(labels.live, labels.live)
    }
}

@Composable
private fun ComposerPreviewTextAction(text: String, contentDescription: String, count: String? = null) {
    ComposerPreviewActionSurface(contentDescription) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                color = Color.White,
                fontSize = if (text.length <= 2) 18.sp else 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                textAlign = TextAlign.Center,
                lineHeight = if (text.length <= 2) 19.sp else 11.sp
            )
            count?.let { Text(it, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 9.sp) }
        }
    }
}

@Composable
private fun ComposerPreviewIconAction(icon: ImageVector, contentDescription: String, count: String? = null) {
    ComposerPreviewActionSurface(contentDescription) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(22.dp))
            count?.let { Text(it, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp) }
        }
    }
}

@Composable
private fun ComposerPreviewActionSurface(contentDescription: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(Color.Black.copy(alpha = 0.42f), CircleShape),
        contentAlignment = Alignment.Center
    ) { content() }
}
