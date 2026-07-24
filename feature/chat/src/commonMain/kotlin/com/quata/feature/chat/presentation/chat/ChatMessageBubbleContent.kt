package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared ordering and spacing for a message bubble. Platform hosts provide the actual header,
 * translated text, attachments, maps and gesture-driven actions through slots.
 */
@Composable
fun ChatMessageBubbleContent(
    header: @Composable () -> Unit,
    body: @Composable () -> Unit,
    forwardedMarker: (@Composable () -> Unit)? = null,
    replyQuote: (@Composable () -> Unit)? = null,
    mapAction: (@Composable () -> Unit)? = null,
) {
    header()
    Spacer(Modifier.padding(2.dp))
    forwardedMarker?.let { marker ->
        marker()
        Spacer(Modifier.padding(2.dp))
    }
    replyQuote?.let { quote ->
        quote()
        Spacer(Modifier.padding(4.dp))
    }
    body()
    mapAction?.let { action ->
        Spacer(Modifier.padding(4.dp))
        action()
    }
}
