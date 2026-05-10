package com.quata.feature.neighborhoods.presentation

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.core.designsystem.theme.QuataDivider
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataSurface
import com.quata.core.designsystem.theme.QuataSurfaceAlt
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.textCanvasBrush
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun NeighborhoodsScreen(
    padding: PaddingValues,
    repository: NeighborhoodRepository,
    onOpenConversation: (String) -> Unit,
    viewModel: NeighborhoodsViewModel = viewModel(factory = NeighborhoodsViewModel.factory(repository))
) {
    val state by viewModel.uiState.collectAsState()
    var selectedCommunity by rememberSaveable { mutableStateOf<String?>(null) }
    val communityForDialog = state.communities.firstOrNull { it.name == selectedCommunity }

    QuataScreen(padding) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = "BARRIOS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                letterSpacing = 1.6.sp
            )
            Spacer(Modifier.height(10.dp))
            Text("Abre una comunidad", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Selecciona un barrio para abrir su chat comunitario.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            if (state.error != null) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(state.communities, key = { it.name }) { community ->
                    NeighborhoodCard(
                        community = community,
                        isOpeningChat = state.isOpeningChat,
                        onShowUsers = { selectedCommunity = community.name },
                        onOpenChat = {
                            viewModel.openChat(community.name, onOpenConversation)
                        }
                    )
                }
            }
        }
    }

    if (communityForDialog != null) {
        NeighborhoodUsersDialog(
            community = communityForDialog,
            onDismiss = { selectedCommunity = null },
            onFollowUser = { viewModel.toggleFollowUser(it.id) },
            onOpenProfile = { viewModel.openUserProfile(it.id) },
            onOpenPrivateChat = { user ->
                viewModel.openPrivateChat(user.id) { conversationId ->
                    selectedCommunity = null
                    onOpenConversation(conversationId)
                }
            }
        )
    }

    state.selectedProfile?.let { profile ->
        CommunityProfileDialog(
            profile = profile,
            onDismiss = { viewModel.closeUserProfile() },
            onFollow = { viewModel.toggleFollowUser(profile.user.id) },
            onOpenPrivateChat = {
                viewModel.openPrivateChat(profile.user.id) { conversationId ->
                    viewModel.closeUserProfile()
                    selectedCommunity = null
                    onOpenConversation(conversationId)
                }
            }
        )
    }
}

@Composable
private fun NeighborhoodCard(
    community: NeighborhoodCommunity,
    isOpeningChat: Boolean,
    onShowUsers: () -> Unit,
    onOpenChat: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = QuataSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, QuataDivider, RoundedCornerShape(22.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                NeighborhoodAvatar(community.name, modifier = Modifier.padding(top = 34.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            community.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            communityTimeLabel(community.lastMessageAtMillis),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        community.lastMessagePreview ?: "Abre la comunidad y empieza la conversacion.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CountPill("${community.users.size} ${if (community.users.size == 1) "usuario" else "usuarios"}")
                        CountPill("${community.messageCount} ${if (community.messageCount == 1) "mensaje" else "mensajes"}")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onShowUsers,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ver usuarios")
                }
                Button(
                    onClick = onOpenChat,
                    enabled = !isOpeningChat,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuataSurfaceAlt,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Abrir chat")
                }
            }
        }
    }
}

@Composable
private fun NeighborhoodAvatar(name: String, modifier: Modifier = Modifier) {
    val letter = name.trim().firstOrNull()?.uppercase() ?: "B"
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(QuataOrange),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
}

@Composable
private fun CountPill(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF5A372B), CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun NeighborhoodUsersDialog(
    community: NeighborhoodCommunity,
    onDismiss: () -> Unit,
    onFollowUser: (NeighborhoodUser) -> Unit,
    onOpenProfile: (NeighborhoodUser) -> Unit,
    onOpenPrivateChat: (NeighborhoodUser) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Usuarios · ${community.name}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Text(
                            "Comunidad creada por usuarios de QUATA",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
                Spacer(Modifier.height(14.dp))
                CountPill("${community.users.size} ${if (community.users.size == 1) "usuario" else "usuarios"}")
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    items(community.users, key = { it.id }) { user ->
                        NeighborhoodUserRow(
                            user = user,
                            onFollowUser = { onFollowUser(user) },
                            onOpenProfile = { onOpenProfile(user) },
                            onOpenPrivateChat = { onOpenPrivateChat(user) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NeighborhoodUserRow(
    user: NeighborhoodUser,
    onFollowUser: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenPrivateChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, QuataDivider, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarLetter(user.displayName, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.displayName, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user.neighborhood, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onFollowUser,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black)
            ) {
                Text(if (user.isFollowing) "Siguiendo" else "Seguir", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onOpenProfile,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = QuataOrange)
            ) {
                Text("Perfil", fontSize = 14.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onOpenPrivateChat,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = QuataOrange)
            ) {
                Text("PRIVI", fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CommunityProfileDialog(
    profile: CommunityUserProfile,
    onDismiss: () -> Unit,
    onFollow: () -> Unit,
    onOpenPrivateChat: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp),
                contentPadding = PaddingValues(20.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.user.displayName, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Text(profile.user.neighborhood, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ProfileAvatar(profile.user, Modifier.size(92.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        ProfileKpi(profile.user.postsCount.toString(), "Posts", Modifier.weight(1f))
                        ProfileKpi(profile.user.followersCount.toString(), "Seguidores", Modifier.weight(1f))
                        ProfileKpi(profile.user.followingCount.toString(), "Siguiendo", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onFollow,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (profile.user.isFollowing) "Siguiendo" else "Seguir", fontSize = 18.sp)
                        }
                        OutlinedButton(
                            onClick = onOpenPrivateChat,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = QuataOrange),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("PRIVI", fontSize = 18.sp)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    val pagerState = rememberPagerState(pageCount = { profile.posts.size })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Fotos y videos", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        if (profile.posts.isNotEmpty()) {
                            Text("${pagerState.currentPage + 1} / ${profile.posts.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (profile.posts.isEmpty()) {
                        Text(
                            "Este perfil todavia no tiene publicaciones visibles",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 28.dp)
                        )
                    } else {
                        ProfilePostsPager(profile.posts, pagerState)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(user: NeighborhoodUser, modifier: Modifier = Modifier) {
    if (user.avatarUrl != null) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = user.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape).border(1.dp, QuataDivider, CircleShape)
        )
    } else {
        AvatarLetter(user.displayName, modifier = modifier.clip(CircleShape))
    }
}

@Composable
private fun ProfileKpi(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(1.dp, QuataDivider, RoundedCornerShape(16.dp))
            .background(QuataSurface, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun ProfilePostsPager(
    posts: List<Post>,
    pagerState: androidx.compose.foundation.pager.PagerState
) {
    val context = LocalContext.current
    var commentsPost by remember { mutableStateOf<Post?>(null) }
    val localComments = remember { mutableStateMapOf<String, List<PostComment>>() }
    Column {
        HorizontalPager(state = pagerState, modifier = Modifier.height(440.dp)) { page ->
            val post = posts[page]
            ProfilePostPreview(
                post = post,
                commentsCount = post.comments.size + localComments[post.id].orEmpty().size,
                onOpenComments = { commentsPost = post },
                onShare = { context.shareProfilePost(post) },
                onReport = {
                    Toast.makeText(context, "Publicacion reportada correctamente", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    commentsPost?.let { post ->
        ProfileCommentsDialog(
            post = post,
            localComments = localComments[post.id].orEmpty(),
            onAddComment = { comment ->
                localComments[post.id] = localComments[post.id].orEmpty() + comment
            },
            onDismiss = { commentsPost = null }
        )
    }
}

@Composable
private fun ProfilePostPreview(
    post: Post,
    commentsCount: Int,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit
) {
    var liked by rememberSaveable(post.id) { mutableStateOf(false) }
    val likes = post.likesCount + if (liked) 1 else 0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
    ) {
        when {
            post.imageUrl != null -> AsyncImage(
                model = post.imageUrl,
                contentDescription = post.text,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(430.dp)
            )
            post.videoUrl != null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF111827), Color(0xFF334155))))
            ) {
                Text("Video", modifier = Modifier.align(Alignment.Center), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            }
            else -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .background(textCanvasBrush(post.text))
                    .padding(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(post.text, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1f)) {
                Text(post.author.displayName, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(post.text, color = Color.White.copy(alpha = 0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniFeedAction(
                    icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    count = likes.toString(),
                    onClick = { liked = !liked }
                )
                MiniFeedAction(Icons.Filled.ChatBubble, commentsCount.toString(), onClick = onOpenComments)
                MiniFeedAction(Icons.Filled.Share, null, onClick = onShare)
                MiniFeedAction(Icons.Filled.Flag, null, onClick = onReport)
            }
        }
    }
}

@Composable
private fun MiniFeedAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: String?,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        if (count != null) Text(count, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileCommentsDialog(
    post: Post,
    localComments: List<PostComment>,
    onAddComment: (PostComment) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by rememberSaveable(post.id) { mutableStateOf("") }
    val comments = post.comments + localComments
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Comentarios", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        Column {
                            Text(comment.authorName, fontWeight = FontWeight.Bold)
                            Text(comment.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Escribe un comentario...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = draft.isNotBlank(),
                        onClick = {
                            onAddComment(
                                PostComment(
                                    id = "profile_${post.id}_${System.currentTimeMillis()}",
                                    authorName = "Tu",
                                    message = draft.trim(),
                                    timestamp = "Ahora"
                                )
                            )
                            draft = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black)
                    ) {
                        Text("Enviar")
                    }
                }
            }
        }
    }
}

private fun android.content.Context.shareProfilePost(post: Post) {
    val shareText = buildString {
        append(post.author.displayName)
        append(": ")
        append(post.text)
        post.imageUrl?.let { append("\n").append(it) }
        post.videoUrl?.let { append("\n").append(it) }
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    startActivity(Intent.createChooser(sendIntent, "Compartir publicacion"))
}

private fun communityTimeLabel(lastMessageAtMillis: Long?): String {
    if (lastMessageAtMillis == null) return "Nueva"
    val zone = ZoneId.systemDefault()
    val messageDate = Instant.ofEpochMilli(lastMessageAtMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val days = ChronoUnit.DAYS.between(messageDate, today)
    return when {
        days == 0L -> DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(lastMessageAtMillis).atZone(zone))
        days == 1L -> "Ayer"
        days < 7L -> "Hace $days dias"
        days < 30L -> {
            val weeks = (days / 7).coerceAtLeast(1)
            "Hace $weeks ${if (weeks == 1L) "semana" else "semanas"}"
        }
        days < 365L -> {
            val months = (days / 30).coerceAtLeast(1)
            "Hace $months ${if (months == 1L) "mes" else "meses"}"
        }
        else -> {
            val years = (days / 365).coerceAtLeast(1)
            "Hace $years ${if (years == 1L) "ano" else "anos"}"
        }
    }
}
