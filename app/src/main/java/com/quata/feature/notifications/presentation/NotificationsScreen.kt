package com.quata.feature.notifications.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.core.model.NotificationItem
import com.quata.core.ui.components.QuataCard
import com.quata.core.ui.components.QuataScreen
import com.quata.feature.notifications.domain.NotificationsRepository

@Composable
fun NotificationsScreen(
    padding: PaddingValues,
    repository: NotificationsRepository,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    viewModel: NotificationsViewModel = viewModel(factory = NotificationsViewModel.factory(repository))
) {
    val state by viewModel.uiState.collectAsState()

    QuataScreen(padding) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(stringResource(R.string.notifications_title), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(stringResource(R.string.notifications_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.padding(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.items, key = { it.id }) { item ->
                    DismissibleNotificationCard(
                        item = item,
                        onClick = {
                            viewModel.markRead(item)
                            onOpenConversation(item.conversationId)
                        },
                        onDismiss = { viewModel.dismiss(item) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleNotificationCard(
    item: NotificationItem,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        content = {
            NotificationCard(
                item = item,
                modifier = Modifier.clickable(onClick = onClick)
            )
        }
    )
}

@Composable
private fun NotificationCard(
    item: NotificationItem,
    modifier: Modifier = Modifier
) {
    QuataCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(item.title, fontWeight = FontWeight.Bold)
            Text(item.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (item.unreadCount > 1) "${item.createdAt} · ${item.unreadCount}" else item.createdAt,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
        }
    }
}
