package com.quata.feature.chat.presentation.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataSurface
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.QuataPrimaryButton
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.QuataTextField
import com.quata.feature.chat.domain.ChatRepository

@Composable
fun ChatScreen(
    padding: PaddingValues,
    conversationId: String,
    repository: ChatRepository,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(conversationId, repository))
) {
    val state by viewModel.uiState.collectAsState()

    QuataScreen(padding) {
        Column {
            ChatHeader(conversation = state.conversation, onBack = onBack)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.messages) { MessageBubble(it) }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuataTextField(
                    value = state.messageText,
                    onValueChange = { viewModel.onEvent(ChatUiEvent.MessageChanged(it)) },
                    label = "Mensaje",
                    modifier = Modifier.weight(1f)
                )
            }
            QuataPrimaryButton(
                text = "Enviar",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) { viewModel.onEvent(ChatUiEvent.Send) }
        }
    }
}

@Composable
private fun ChatHeader(conversation: Conversation?, onBack: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val title = conversation?.headerTitle().orEmpty().ifBlank { "Chat" }
    val isGroup = conversation?.isGroup == true
    Surface(color = QuataSurface.copy(alpha = 0.88f), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isGroup) { expanded = !expanded }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atras")
                }
                ChatAvatar(conversation)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isGroup) {
                        Text(
                            "${conversation?.participantNames?.size ?: 0} miembros",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            if (expanded && conversation != null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(conversation.participantNames) { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarLetter(name, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatAvatar(conversation: Conversation?) {
    if (conversation?.isGroup == true) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(QuataOrange.copy(alpha = 0.22f))
                .border(1.dp, QuataOrange.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Group, contentDescription = null, tint = Color.White)
        }
    } else {
        AvatarLetter(conversation?.title.orEmpty().ifBlank { "C" }, modifier = Modifier.size(46.dp))
    }
}

private fun Conversation.headerTitle(): String =
    if (isGroup && participantNames.isNotEmpty()) participantNames.joinToString(", ") else title

@Composable
private fun MessageBubble(message: Message) {
    val context = LocalContext.current
    val mapsUrl = message.text.extractMapsUrl()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .background(if (message.isMine) QuataOrange else QuataSurface, RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            val textColor = if (message.isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Text(message.senderName, fontWeight = FontWeight.Bold, color = textColor)
            Spacer(Modifier.padding(2.dp))
            Text(message.text, color = textColor)
            if (mapsUrl != null) {
                Spacer(Modifier.padding(4.dp))
                Text(
                    text = "Abrir ubicacion en Google Maps",
                    color = if (message.isMine) Color.Black else QuataOrange,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clickable { context.openMaps(mapsUrl) }
                )
            }
        }
    }
}

private fun String.extractMapsUrl(): String? =
    Regex("""https://maps\.google\.com/\?q=[^\s]+""").find(this)?.value

private fun android.content.Context.openMaps(url: String) {
    val uri = Uri.parse(url)
    val mapsIntent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
    runCatching { startActivity(mapsIntent) }
        .onFailure { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}
