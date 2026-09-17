package com.quata.feature.profile.presentation

import com.quata.core.platform.LocationService
import com.quata.core.platform.PermissionService
import com.quata.core.session.IosRenewableAuthSession
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.SosRateLimitException
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.profile.domain.SosActorProvider
import com.quata.feature.profile.domain.SosDispatchCoordinator
import com.quata.feature.profile.domain.SosDispatchOutcome
import com.quata.feature.profile.domain.SosDispatchTransport
import com.quata.feature.profile.domain.SosInitialSendResult
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class IosSosDispatchResult(
    val code: String,
    val remainingMillis: Long = 0L,
)

/** Swift-facing owner for one Profile/Chat/session graph. */
class IosSosDispatchRuntime internal constructor(
    profileRepository: ProfileRepository,
    authSession: IosRenewableAuthSession,
    chatRepository: ChatRepository,
    permissions: PermissionService,
    location: LocationService,
) {
    private val scope = MainScope()
    private val coordinator = SosDispatchCoordinator(
        profileRepository = profileRepository,
        actorProvider = SosActorProvider { authSession.restoredSession()?.userId },
        transport = IosSosDispatchTransport(chatRepository),
        permissions = permissions,
        location = location,
        scope = scope,
    )

    fun dispatch(onComplete: (IosSosDispatchResult) -> Unit) {
        scope.launch { onComplete(coordinator.dispatch().toIosResult()) }
    }

    fun resumeAfterConfigurationSaved(onComplete: (IosSosDispatchResult) -> Unit) {
        scope.launch { onComplete(coordinator.resumeAfterConfigurationSaved().toIosResult()) }
    }

    fun cancel() = coordinator.cancel()

    fun close() {
        coordinator.cancel()
        scope.cancel()
    }
}

private class IosSosDispatchTransport(
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

private fun SosDispatchOutcome.toIosResult(): IosSosDispatchResult = when (this) {
    is SosDispatchOutcome.Sent -> IosSosDispatchResult("sent")
    is SosDispatchOutcome.NeedsConfiguration -> IosSosDispatchResult("needs_configuration")
    is SosDispatchOutcome.RateLimited -> IosSosDispatchResult("rate_limited", remainingMillis)
    is SosDispatchOutcome.Failed -> IosSosDispatchResult("failed")
    SosDispatchOutcome.IgnoredWhileSending -> IosSosDispatchResult("ignored_while_sending")
    SosDispatchOutcome.NoPendingConfiguration -> IosSosDispatchResult("no_pending_configuration")
}
