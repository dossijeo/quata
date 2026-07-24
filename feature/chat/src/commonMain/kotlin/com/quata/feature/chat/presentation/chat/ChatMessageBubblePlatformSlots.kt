package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Platform-owned parts of a message bubble.
 *
 * The common bubble intentionally does not know about Android URIs, media players, map intents
 * or translation services. Hosts must supply each slot explicitly, using an empty slot where that
 * capability does not apply to the current message. This keeps the visual ordering portable while
 * retaining ownership of system integrations at the platform boundary.
 */
data class ChatMessageBubblePlatformSlots(
    val avatar: @Composable () -> Unit,
    val translatedTextModifier: (Modifier) -> Modifier,
    val richText: @Composable ColumnScope.(Color) -> Unit,
    val hasMediaAttachment: Boolean,
    val mediaAttachment: @Composable ColumnScope.(Color) -> Unit,
    val hasAudioAttachment: Boolean,
    val audioAttachment: @Composable ColumnScope.(Color) -> Unit,
    val hasUriAttachment: Boolean,
    val uriAttachment: @Composable ColumnScope.(Color) -> Unit,
    val hasMapAction: Boolean,
    val mapAction: @Composable () -> Unit,
    val hasActions: Boolean,
    val actions: @Composable (Modifier) -> Unit,
)

/** Explicit no-op slots for hosts whose current message has no platform-backed content. */
val EmptyChatMessageBubblePlatformSlots = ChatMessageBubblePlatformSlots(
    avatar = {},
    translatedTextModifier = { it },
    richText = {},
    hasMediaAttachment = false,
    mediaAttachment = {},
    hasAudioAttachment = false,
    audioAttachment = {},
    hasUriAttachment = false,
    uriAttachment = {},
    hasMapAction = false,
    mapAction = {},
    hasActions = false,
    actions = {},
)
