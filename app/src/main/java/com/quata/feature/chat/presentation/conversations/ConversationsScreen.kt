package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
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
import com.quata.core.model.Conversation
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.QuataCard
import com.quata.core.ui.components.QuataScreen
import com.quata.feature.chat.domain.ChatRepository

@Composable
fun ConversationsScreen(
    padding: PaddingValues,
    repository: ChatRepository,
    onOpenConversation: (String) -> Unit,
    viewModel: ConversationsViewModel = viewModel(factory = ConversationsViewModel.factory(repository))
) {
    val state by viewModel.uiState.collectAsState()

    QuataScreen(padding) {
        Column(Modifier.padding(18.dp)) {
            Text("Chat", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text("Conversaciones recientes", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.padding(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.conversations) { item -> ConversationCard(item, onOpenConversation) }
            }
        }
    }
}

@Composable
private fun ConversationCard(item: Conversation, onOpenConversation: (String) -> Unit) {
    QuataCard(modifier = Modifier.clickable { onOpenConversation(item.id) }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarLetter(item.title)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.title, fontWeight = FontWeight.Bold)
                Text(item.lastMessagePreview, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (item.unreadCount > 0) {
                Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(item.unreadCount.toString()) }
            } else {
                Text(item.updatedAt, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}
