package com.quata.feature.notifications.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.quata.R
import com.quata.core.text.localizedChatPreview
import com.quata.feature.chat.presentation.relativeTimeLabel
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.delay

/** Android adapter for resources, relative time and navigation; shared content owns its ViewModel. */
@Composable
fun NotificationsScreen(
    padding: PaddingValues,
    repository: NotificationsRepository,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    val context = LocalContext.current
    var timestampNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val strings = NotificationsStrings(
        title = stringResource(R.string.notifications_title),
        subtitle = stringResource(R.string.notifications_subtitle),
        backContentDescription = stringResource(R.string.common_back),
        relativeTime = { createdAt, now -> relativeTimeLabel(context, createdAt, now) },
        localizedBody = context::localizedChatPreview
    )

    NotificationsHostContent(
        padding = padding,
        repository = repository,
        timestampNowMillis = timestampNowMillis,
        strings = strings,
        onBack = onBack,
        onOpenConversation = onOpenConversation,
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            timestampNowMillis = System.currentTimeMillis()
        }
    }
}
