package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.compactButtonMinSize
import com.quata.core.designsystem.theme.quataTheme
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity

data class NeighborhoodListStrings(
    val title: String,
    val searchPlaceholder: String,
    val loading: String,
    val oneUser: String,
    val users: (Int) -> String,
    val oneMessage: String,
    val messages: (Int) -> String,
    val viewUsers: String,
    val openChat: String,
    val timeLabel: (Long?) -> String
)

@Composable
fun NeighborhoodListContent(
    padding: PaddingValues,
    communities: List<NeighborhoodCommunity>,
    query: String,
    isLoading: Boolean,
    error: String?,
    currentUserId: String?,
    openingNeighborhood: String?,
    strings: NeighborhoodListStrings,
    onQueryChange: (String) -> Unit,
    onShowUsers: (NeighborhoodCommunity) -> Unit,
    onOpenChat: (NeighborhoodCommunity) -> Unit
) {
    val template = quataTheme()
    val visibleCommunities = remember(communities, query) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) communities else communities.filter { it.name.contains(cleanQuery, ignoreCase = true) }
    }
    QuataScreen(padding) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(strings.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(strings.searchPlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
            }
            if (isLoading && communities.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(strings.loading, color = template.colors.textSecondary, fontWeight = FontWeight.SemiBold)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    items(visibleCommunities, key = { it.name }) { community ->
                        NeighborhoodCardContent(
                            community = community,
                            canOpenChat = currentUserId?.let { id -> community.users.any { it.id != id } }
                                ?: community.users.isNotEmpty(),
                            isOpeningChat = openingNeighborhood == community.name,
                            strings = strings,
                            onShowUsers = { onShowUsers(community) },
                            onOpenChat = { onOpenChat(community) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NeighborhoodCardContent(
    community: NeighborhoodCommunity,
    canOpenChat: Boolean,
    isOpeningChat: Boolean,
    strings: NeighborhoodListStrings,
    onShowUsers: () -> Unit,
    onOpenChat: () -> Unit
) {
    val template = quataTheme()
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = template.colors.surface),
        modifier = Modifier.fillMaxWidth().border(1.dp, template.colors.divider, RoundedCornerShape(22.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                NeighborhoodAvatarContent(community.name, Modifier.padding(top = 34.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(community.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(strings.timeLabel(community.lastMessageAtMillis), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    community.lastMessagePreview?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)) }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeighborhoodCountPill(if (community.users.size == 1) strings.oneUser else strings.users(community.users.size))
                        NeighborhoodCountPill(if (community.messageCount == 1) strings.oneMessage else strings.messages(community.messageCount))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onShowUsers, shape = RoundedCornerShape(6.dp), modifier = Modifier.compactButtonMinSize(), contentPadding = CompactButtonContentPadding) { Text(strings.viewUsers) }
                Button(onClick = onOpenChat, enabled = canOpenChat && !isOpeningChat, shape = RoundedCornerShape(12.dp), modifier = Modifier.compactButtonMinSize(), contentPadding = CompactButtonContentPadding, colors = ButtonDefaults.buttonColors(containerColor = template.colors.surfaceAlt, contentColor = MaterialTheme.colorScheme.onSurface)) {
                    if (isOpeningChat) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(8.dp)) }
                    Text(strings.openChat)
                }
            }
        }
    }
}

@Composable private fun NeighborhoodAvatarContent(name: String, modifier: Modifier = Modifier) {
    val template = quataTheme()
    Box(modifier = modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(template.colors.accent), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "B", color = template.colors.accentContent, fontWeight = FontWeight.Black, fontSize = template.textSizes.title)
    }
}

@Composable fun NeighborhoodCountPill(text: String) {
    val template = quataTheme()
    Box(Modifier.background(template.colors.sosSurface, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(text, color = template.colors.textPrimary, fontSize = template.textSizes.caption)
    }
}
