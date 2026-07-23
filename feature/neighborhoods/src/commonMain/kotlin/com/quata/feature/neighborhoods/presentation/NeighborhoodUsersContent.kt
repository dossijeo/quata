package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataScreen
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodUser

data class NeighborhoodUsersStrings(
    val title: (String) -> String,
    val subtitle: String,
    val backContentDescription: String,
    val memberCount: (Int) -> String,
    val row: NeighborhoodUserRowStrings
)

@Composable
fun NeighborhoodUsersContent(
    padding: PaddingValues,
    community: NeighborhoodCommunity,
    currentUserId: String?,
    isOpeningChat: Boolean,
    openingPrivateChatUserId: String?,
    openingProfileUserId: String?,
    followingUserId: String?,
    strings: NeighborhoodUsersStrings,
    avatar: @Composable (NeighborhoodUser, Boolean, () -> Unit) -> Unit,
    onBack: () -> Unit,
    onFollowUser: (NeighborhoodUser) -> Unit,
    onOpenProfile: (NeighborhoodUser) -> Unit,
    onOpenPrivateChat: (NeighborhoodUser) -> Unit
) {
    QuataScreen(padding) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompactIconButton(onClick = onBack) {
                    CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, strings.backContentDescription)
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(strings.title(community.name), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Text(strings.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(18.dp))
            NeighborhoodCountPill(strings.memberCount(community.users.size))
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 22.dp), modifier = Modifier.fillMaxSize()) {
                items(community.users, key = { it.id }) { user ->
                    NeighborhoodUserRowContent(
                        user = user,
                        isOwnUser = user.id == currentUserId,
                        isFollowingLoading = followingUserId == user.id,
                        isOpeningChat = isOpeningChat && openingPrivateChatUserId == user.id,
                        strings = strings.row,
                        avatar = { avatar(user, openingProfileUserId == user.id) { onOpenProfile(user) } },
                        onFollowUser = { onFollowUser(user) },
                        onOpenPrivateChat = { onOpenPrivateChat(user) }
                    )
                }
            }
        }
    }
}
