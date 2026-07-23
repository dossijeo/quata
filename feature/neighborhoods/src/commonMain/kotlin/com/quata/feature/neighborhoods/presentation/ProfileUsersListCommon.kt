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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.feature.neighborhoods.domain.NeighborhoodUser

@Composable
fun ProfileUsersListCommon(title: String, users: List<NeighborhoodUser>, currentUserId: String?, isOpeningChat: Boolean, openingProfileUserId: String?, followingUserId: String?, strings: NeighborhoodUserRowStrings, back: String, avatar: @Composable (NeighborhoodUser, Boolean, () -> Unit) -> Unit, onBack: () -> Unit, onFollow: (NeighborhoodUser) -> Unit, onProfile: (NeighborhoodUser) -> Unit, onChat: (NeighborhoodUser) -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 780.dp).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { CompactIconButton(onClick = onBack) { CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, back) }; Spacer(Modifier.width(4.dp)); Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f)) }
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)) { items(users, key = { it.id }) { user -> NeighborhoodUserRowContent(user, user.id == currentUserId, followingUserId == user.id, isOpeningChat, strings, { avatar(user, openingProfileUserId == user.id) { onProfile(user) } }, { onFollow(user) }, { onChat(user) }) } }
    }
}
