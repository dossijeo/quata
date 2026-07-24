package com.quata.feature.notifications.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.quata.core.model.NotificationItem
import com.quata.feature.notifications.domain.NotificationsRepository

/**
 * Platform-neutral presentation host for the notification list.
 *
 * Launchers/receivers supply navigation callbacks, while this host owns only the shared
 * ViewModel lifecycle and routes its state into [NotificationsContent].
 */
@Composable
fun NotificationsHostContent(
    padding: PaddingValues,
    repository: NotificationsRepository,
    timestampNowMillis: Long,
    strings: NotificationsStrings,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(repository) { NotificationsViewModel(repository) }
    val state by viewModel.uiState.collectAsState()
    DisposableEffect(viewModel) { onDispose(viewModel::close) }
    Box(modifier = modifier) {
        NotificationsContent(
            padding = padding,
            state = state,
            timestampNowMillis = timestampNowMillis,
            strings = strings,
            onBack = onBack,
            onOpenConversation = onOpenConversation,
            onMarkRead = viewModel::markRead,
            onDismiss = viewModel::dismiss,
        )
    }
}
