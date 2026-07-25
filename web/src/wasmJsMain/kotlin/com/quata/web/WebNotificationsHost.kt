@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quata.feature.notifications.presentation.NotificationsHostContent
import com.quata.feature.notifications.presentation.NotificationsStrings
import kotlinx.coroutines.delay

@Composable
fun WebNotificationsHost(repository: WebNotificationsRepository, onBack: () -> Unit, onOpenConversation: (String) -> Unit) {
    var nowMillis by remember { mutableLongStateOf(browserNowMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000L); nowMillis = browserNowMillis() } }
    NotificationsHostContent(
        padding = PaddingValues(), repository = repository, timestampNowMillis = nowMillis,
        strings = NotificationsStrings("Notificaciones", "Mensajes no leídos", "Volver", { createdAt, _ -> createdAt.ifBlank { "Ahora" } }, { it }),
        onBack = onBack, onOpenConversation = onOpenConversation,
    )
}

private fun browserNowMillis(): Long = js("Date.now()")
