package com.quata.web

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.SosRateLimitException
import com.quata.feature.profile.domain.SosDispatchOutcome
import com.quata.feature.profile.domain.SosDispatchTransport
import com.quata.feature.profile.domain.SosInitialSendResult

internal class WebSosDispatchTransport(
    private val chatRepository: ChatRepository,
) : SosDispatchTransport {
    override suspend fun sendInitial(
        expectedActorId: String,
        contactIds: List<String>,
        text: String,
        latitude: Double?,
        longitude: Double?,
        accuracyMeters: Double?,
    ): SosInitialSendResult = chatRepository
        .sendSosMessage(contactIds, text, latitude, longitude, accuracyMeters, expectedActorId)
        .fold(
            onSuccess = { SosInitialSendResult.Sent(it) },
            onFailure = { error ->
                if (error is SosRateLimitException) SosInitialSendResult.RateLimited(error.remainingMillis)
                else SosInitialSendResult.Failed(error.message)
            },
        )

    override suspend fun sendLocationUpdate(
        expectedActorId: String,
        conversationId: String,
        text: String,
        clientMessageId: String,
    ): Result<Unit> = chatRepository.sendMessage(
        conversationId = conversationId,
        text = text,
        clientMessageId = clientMessageId,
        expectedActorId = expectedActorId,
    )
}

@Composable
internal fun WebSosDispatchFeedbackDialog(
    outcome: SosDispatchOutcome?,
    onDismiss: () -> Unit,
) {
    val message = when (outcome) {
        is SosDispatchOutcome.Sent -> "SOS enviado a tus contactos de emergencia."
        is SosDispatchOutcome.RateLimited -> {
            val seconds = (outcome.remainingMillis + 999L) / 1_000L
            "Ya has enviado un SOS recientemente. Inténtalo de nuevo en ${seconds}s."
        }
        is SosDispatchOutcome.Failed -> "No se pudo enviar el SOS. Vuelve a intentarlo."
        else -> return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SOS") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Aceptar") } },
    )
}
