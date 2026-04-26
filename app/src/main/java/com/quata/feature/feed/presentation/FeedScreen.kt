package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.core.model.Post
import com.quata.core.ui.components.QuataCard
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.UserHeader
import com.quata.feature.feed.domain.FeedRepository

@Composable
fun FeedScreen(
    padding: PaddingValues,
    feedRepository: FeedRepository,
    viewModel: FeedViewModel = viewModel(factory = FeedViewModel.factory(feedRepository))
) {
    val state by viewModel.uiState.collectAsState()

    QuataScreen(padding) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Qüata", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.onEvent(FeedUiEvent.Refresh) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refrescar")
                }
            }

            when {
                state.error != null -> Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                state.posts.isEmpty() && !state.isLoading -> Text("No hay publicaciones todavía")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(state.posts) { post -> PostCard(post) }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PostCard(post: Post) {
    QuataCard {
        Column(Modifier.padding(16.dp)) {
            UserHeader(post.author.displayName, post.createdAt)
            Spacer(Modifier.height(14.dp))
            Text(post.text, lineHeight = 22.sp)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(" ${post.likesCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(" ${post.commentsCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
