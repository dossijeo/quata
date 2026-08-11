package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.feature.neighborhoods.domain.NeighborhoodUser

const val PublicProfileUserListRootTestTagPrefix = "public-profile.list."
const val PublicProfileUserListBackTestTagPrefix = "public-profile.list.back."
const val PublicProfileUserListRowTestTagPrefix = "public-profile.list.row."
const val PublicProfileUserListAvatarTestTagPrefix = "public-profile.list.avatar."
const val PublicProfileUserListNameTestTagPrefix = "public-profile.list.name."
const val PublicProfileUserListFollowActionTestTagPrefix = "public-profile.list.follow."
const val PublicProfileUserListChatActionTestTagPrefix = "public-profile.list.chat."

@Composable
fun ProfileUsersListCommon(listKind: String, title: String, users: List<NeighborhoodUser>, currentUserId: String?, isOpeningChat: Boolean, openingProfileUserId: String?, followingUserId: String?, strings: NeighborhoodUserRowStrings, back: String, avatar: @Composable (NeighborhoodUser, Boolean, Modifier, () -> Unit) -> Unit, onBack: () -> Unit, onFollow: (NeighborhoodUser) -> Unit, onProfile: (NeighborhoodUser) -> Unit, onChat: (NeighborhoodUser) -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 780.dp).padding(horizontal = 18.dp, vertical = 14.dp).semantics { testTag = PublicProfileUserListRootTestTagPrefix + listKind }) {
        Row(verticalAlignment = Alignment.CenterVertically) { CompactIconButton(onClick = onBack, modifier = Modifier.semantics { testTag = PublicProfileUserListBackTestTagPrefix + listKind }) { CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, back) }; Spacer(Modifier.width(4.dp)); Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f)) }
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            items(users, key = { it.id }) { user ->
                val rowKey = "$listKind.${user.id}"
                NeighborhoodUserRowContent(
                    user = user,
                    isOwnUser = user.id == currentUserId,
                    isFollowingLoading = followingUserId == user.id,
                    isOpeningChat = isOpeningChat,
                    strings = strings,
                    avatar = { avatar(user, openingProfileUserId == user.id, Modifier.semantics { testTag = PublicProfileUserListAvatarTestTagPrefix + rowKey }) { onProfile(user) } },
                    onFollowUser = { onFollow(user) },
                    onOpenPrivateChat = { onChat(user) },
                    modifier = Modifier.semantics { testTag = PublicProfileUserListRowTestTagPrefix + rowKey },
                    nameModifier = Modifier.semantics { testTag = PublicProfileUserListNameTestTagPrefix + rowKey },
                    followModifier = Modifier.semantics { testTag = PublicProfileUserListFollowActionTestTagPrefix + rowKey },
                    chatModifier = Modifier.semantics { testTag = PublicProfileUserListChatActionTestTagPrefix + rowKey },
                )
            }
        }
    }
}
