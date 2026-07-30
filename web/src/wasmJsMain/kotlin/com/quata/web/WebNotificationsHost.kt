@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.quata.feature.notifications.presentation.ChatPreviewCatalog
import com.quata.feature.notifications.presentation.NotificationsHostContent
import com.quata.feature.notifications.presentation.NotificationsStrings
import com.quata.feature.notifications.presentation.RelativeTimeCatalog
import com.quata.feature.notifications.presentation.SosPreviewCatalog
import com.quata.feature.notifications.presentation.NotificationDeliveryState
import com.quata.feature.notifications.presentation.notificationDeliveryNotice

@Composable
fun WebNotificationsHost(
    repository: WebNotificationsRepository,
    runtimeConfiguration: WebRuntimeConfiguration,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    NotificationsHostContent(
        padding = PaddingValues(), repository = repository, nowMillis = ::notificationsBrowserNowMillis,
        strings = NotificationsStrings(
            "Notificaciones", webNotificationsUnreadSubtitle, "Volver",
            RelativeTimeCatalog(
                seconds = { "hace $it s" }, oneMinute = "hace 1 min", minutes = { "hace $it min" }, hours = { "hace $it h" },
                days = { "hace $it d" }, oneWeek = "hace 1 semana", weeks = { "hace $it semanas" }, oneMonth = "hace 1 mes",
                months = { "hace $it meses" }, oneYear = "hace 1 año", years = { "hace $it años" },
            ),
            ChatPreviewCatalog("🖼️ Foto", "🎥 Vídeo", "📄 Documento", "🎤 Nota de voz", "📎 Archivo"),
            SosPreviewCatalog(
                locationUpdate = "Actualizacion de ubicacion SOS",
                locationUnavailable = "📍 Ubicación no disponible",
                approximateLocation = { "Ubicacion aproximada: $it" },
            ),
        ),
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
