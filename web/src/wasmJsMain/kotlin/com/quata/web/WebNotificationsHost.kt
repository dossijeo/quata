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
import com.quata.feature.notifications.presentation.NotificationDeliveryState
import com.quata.feature.notifications.presentation.notificationDeliveryNotice
import kotlinx.coroutines.delay

@Composable
fun WebNotificationsHost(
    repository: WebNotificationsRepository,
    runtimeConfiguration: WebRuntimeConfiguration,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    var nowMillis by remember { mutableLongStateOf(notificationsBrowserNowMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000L); nowMillis = notificationsBrowserNowMillis() } }
    NotificationsHostContent(
        padding = PaddingValues(), repository = repository, timestampNowMillis = nowMillis,
        strings = NotificationsStrings("Notificaciones", webNotificationsUnreadSubtitle, "Volver", { createdAt, _ -> createdAt.ifBlank { "Ahora" } }, { it }),
        deliveryNotice = notificationDeliveryNotice(
            if (runtimeConfiguration.isBackendConfigured) {
                NotificationDeliveryState.DeliveryUnverified
            } else {
                NotificationDeliveryState.NotConfigured
            },
        ),
        onBack = onBack, onOpenConversation = onOpenConversation,
    )
}

internal const val webNotificationsUnreadSubtitle = "Mensajes no leídos"

@JsFun("() => Date.now()")
private external fun notificationsBrowserNowMillisAsDouble(): Double

/** Date.now() is a JavaScript Number, not the BigInt required by a Wasm Kotlin Long. */
internal fun notificationsBrowserNowMillis(): Long = notificationsBrowserNowMillisAsDouble().toLong()
