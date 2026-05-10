package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.core.designsystem.theme.QuataDivider
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataSurface
import com.quata.core.designsystem.theme.QuataSurfaceAlt
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.QuataScreen
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
    var selectedCommunity by rememberSaveable(state.communities) { mutableStateOf<String?>(null) }
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
            onDismiss = { selectedCommunity = null }
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
    onDismiss: () -> Unit
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
                        NeighborhoodUserRow(user)
                    }
                }
            }
        }
    }
}

@Composable
private fun NeighborhoodUserRow(user: NeighborhoodUser) {
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
                onClick = {},
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black)
            ) {
                Text("Seguir", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = {},
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = QuataOrange)
            ) {
                Text("Perfil", fontSize = 14.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = {},
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = QuataOrange)
            ) {
                Text("PRIVI", fontSize = 14.sp, maxLines = 1)
            }
        }
    }
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
