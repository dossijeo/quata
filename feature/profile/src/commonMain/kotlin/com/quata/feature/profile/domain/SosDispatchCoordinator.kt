package com.quata.feature.profile.domain

import com.quata.core.platform.GeoLocation
import com.quata.core.platform.LocationService
import com.quata.core.platform.PermissionService
import com.quata.core.platform.PermissionStatus
import com.quata.core.platform.PlatformPermission
import com.quata.core.platform.PlatformResult
import com.quata.core.text.SosLocationUnavailableReason
import com.quata.core.text.SosShortcodeKind
import com.quata.core.text.buildSosShortcode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun interface SosActorProvider {
    suspend fun currentActorId(): String?
}

interface SosDispatchTransport {
    suspend fun sendInitial(
        expectedActorId: String,
        contactIds: List<String>,
        text: String,
        latitude: Double?,
        longitude: Double?,
        accuracyMeters: Double?,
    ): SosInitialSendResult

    suspend fun sendLocationUpdate(
        expectedActorId: String,
        conversationId: String,
        text: String,
        clientMessageId: String,
    ): Result<Unit>
}

sealed interface SosInitialSendResult {
    data class Sent(val conversationId: String) : SosInitialSendResult
    data class RateLimited(val remainingMillis: Long) : SosInitialSendResult
    data class Failed(val reason: String? = null) : SosInitialSendResult
}

sealed interface SosDispatchOutcome {
    data class Sent(val conversationId: String, val locationRecoveryStarted: Boolean) : SosDispatchOutcome
    data class NeedsConfiguration(val profile: UserProfile, val candidates: List<EmergencyContactCandidate>) : SosDispatchOutcome
    data class RateLimited(val remainingMillis: Long) : SosDispatchOutcome
    data class Failed(val reason: String) : SosDispatchOutcome
    data object IgnoredWhileSending : SosDispatchOutcome
    data object NoPendingConfiguration : SosDispatchOutcome
}

data class SosDispatchState(
    val isSending: Boolean = false,
    val isRecoveringLocation: Boolean = false,
    val lastOutcome: SosDispatchOutcome? = null,
)

/**
 * Owns the portable SOS transaction. It reloads configuration for every attempt, binds the work
 * to one authenticated actor, sends exactly one immediate alert, and performs at most one later
 * location update. Backend cooldown remains authoritative across devices and processes.
 */
@OptIn(ExperimentalTime::class)
class SosDispatchCoordinator(
    private val profileRepository: ProfileRepository,
    private val actorProvider: SosActorProvider,
    private val transport: SosDispatchTransport,
    private val permissions: PermissionService,
    private val location: LocationService,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val recoveryPollMillis: Long = 5_000L,
    private val recoveryWindowMillis: Long = 30L * 60L * 1_000L,
    private val freshLocationMillis: Long = 60_000L,
) {
    private val mutableState = MutableStateFlow(SosDispatchState())
    val state: StateFlow<SosDispatchState> = mutableState.asStateFlow()

    private var pendingConfigurationActor: String? = null
    private val dispatchMutex = kotlinx.coroutines.sync.Mutex()
    private var activeDispatch: kotlinx.coroutines.Deferred<SosDispatchOutcome>? = null
    private var recoveryJob: Job? = null

    suspend fun dispatch(): SosDispatchOutcome = coroutineScope {
        if (!dispatchMutex.tryLock()) return@coroutineScope SosDispatchOutcome.IgnoredWhileSending
        mutableState.value = mutableState.value.copy(isSending = true, lastOutcome = null)
        val operation = async {
            try {
                dispatchOnce()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                SosDispatchOutcome.Failed(error.message ?: "sos_dispatch_failed")
            }
        }
        activeDispatch = operation
        try {
            operation.await().also { outcome ->
                if (activeDispatch === operation) {
                    mutableState.value = mutableState.value.copy(isSending = false, lastOutcome = outcome)
                }
            }
        } finally {
            operation.cancel()
            withContext(NonCancellable) { operation.join() }
            if (activeDispatch === operation) {
                activeDispatch = null
                mutableState.value = mutableState.value.copy(isSending = false)
            }
            dispatchMutex.unlock()
        }
    }

    suspend fun resumeAfterConfigurationSaved(): SosDispatchOutcome {
        val actor = actorProvider.currentActorId()?.takeIf(String::isNotBlank)
        if (pendingConfigurationActor == null) {
            val outcome = SosDispatchOutcome.NoPendingConfiguration
            mutableState.value = mutableState.value.copy(lastOutcome = outcome)
            return outcome
        }
        if (actor == null || pendingConfigurationActor != actor) {
            pendingConfigurationActor = null
            val outcome = SosDispatchOutcome.Failed("sos_actor_changed")
            mutableState.value = mutableState.value.copy(lastOutcome = outcome)
            return outcome
        }
        return dispatch()
    }

    fun cancel() {
        pendingConfigurationActor = null
        activeDispatch?.cancel()
        recoveryJob?.cancel()
        recoveryJob = null
        mutableState.value = SosDispatchState()
    }

    private suspend fun dispatchOnce(): SosDispatchOutcome {
        val actor = actorProvider.currentActorId()?.takeIf(String::isNotBlank)
            ?: return SosDispatchOutcome.Failed("sos_session_missing")
        val model = profileRepository.getProfileEditModel().getOrElse {
            return SosDispatchOutcome.Failed(it.message ?: "sos_profile_load_failed")
        }
        if (actorProvider.currentActorId() != actor) return SosDispatchOutcome.Failed("sos_actor_changed")

        val profile = model.profile
        val contactIds = profile.emergencyContactIds.distinct().take(5)
        if (contactIds.isEmpty()) {
            pendingConfigurationActor = actor
            return SosDispatchOutcome.NeedsConfiguration(profile, model.config.emergencyCandidates)
        }
        pendingConfigurationActor = null

        val locationResolution = resolveLocation()
        currentCoroutineContext().ensureActive()
        if (actorProvider.currentActorId() != actor) return SosDispatchOutcome.Failed("sos_actor_changed")
        currentCoroutineContext().ensureActive()
        val currentLocation = locationResolution.location
        val message = buildMessage(profile, currentLocation, locationResolution.unavailableReason)
        val sent = transport.sendInitial(
            expectedActorId = actor,
            contactIds = contactIds,
            text = message,
            latitude = currentLocation?.latitude,
            longitude = currentLocation?.longitude,
            accuracyMeters = currentLocation?.accuracyMeters?.toDouble(),
        )
        currentCoroutineContext().ensureActive()
        return when (sent) {
            is SosInitialSendResult.RateLimited -> SosDispatchOutcome.RateLimited(sent.remainingMillis.coerceAtLeast(1L))
            is SosInitialSendResult.Failed -> SosDispatchOutcome.Failed(sent.reason ?: "sos_send_failed")
            is SosInitialSendResult.Sent -> {
                val shouldRecover = currentLocation == null || currentLocation.isStale()
                if (shouldRecover) startForegroundRecovery(actor, profile, sent.conversationId)
                SosDispatchOutcome.Sent(sent.conversationId, shouldRecover)
            }
        }
    }

    private suspend fun resolveLocation(): LocationResolution {
        val initial = permissions.status(PlatformPermission.Location)
        val resolved = if (initial == PermissionStatus.Granted) initial else permissions.request(PlatformPermission.Location)
        if (resolved != PermissionStatus.Granted) {
            return LocationResolution(
                unavailableReason = if (resolved == PermissionStatus.Denied || resolved == PermissionStatus.PermanentlyDenied) {
                    SosLocationUnavailableReason.PermissionDenied
                } else {
                    SosLocationUnavailableReason.Unavailable
                },
            )
        }
        return when (val result = location.currentLocation()) {
            is PlatformResult.Success -> LocationResolution(result.value, null)
            is PlatformResult.Failure -> LocationResolution(null, SosLocationUnavailableReason.Failed)
            PlatformResult.Cancelled, PlatformResult.Unsupported -> LocationResolution(null, SosLocationUnavailableReason.Unavailable)
        }
    }

    private fun startForegroundRecovery(actor: String, profile: UserProfile, conversationId: String) {
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            mutableState.value = mutableState.value.copy(isRecoveringLocation = true)
            val deadline = nowMillis() + recoveryWindowMillis
            val clientMessageId = "sos-location:$actor:$conversationId:$deadline"
            try {
                while (nowMillis() <= deadline && actorProvider.currentActorId() == actor) {
                    if (permissions.status(PlatformPermission.Location) == PermissionStatus.Granted) {
                        val recovered = (location.currentLocation() as? PlatformResult.Success)?.value
                        currentCoroutineContext().ensureActive()
                        if (recovered != null && !recovered.isStale()) {
                            val update = buildMessage(profile, recovered, null, isLocationUpdate = true)
                            if (actorProvider.currentActorId() == actor) {
                                currentCoroutineContext().ensureActive()
                                if (transport.sendLocationUpdate(
                                        expectedActorId = actor,
                                        conversationId = conversationId,
                                        text = update,
                                        clientMessageId = clientMessageId,
                                    ).isSuccess
                                ) {
                                    break
                                }
                            }
                        }
                    }
                    delay(recoveryPollMillis)
                }
            } finally {
                mutableState.value = mutableState.value.copy(isRecoveringLocation = false)
            }
        }
    }

    private fun buildMessage(
        profile: UserProfile,
        location: GeoLocation?,
        unavailableReason: SosLocationUnavailableReason?,
        isLocationUpdate: Boolean = false,
    ): String = buildSosShortcode(
        kind = if (isLocationUpdate) SosShortcodeKind.LocationUpdate else SosShortcodeKind.Alert,
        senderName = profile.displayName,
        customMessage = profile.emergencyMessage.takeUnless { profile.emergencyMessageIsDefault },
        latitude = location?.latitude,
        longitude = location?.longitude,
        ageMillis = location?.timestampMillis?.let { (nowMillis() - it).coerceAtLeast(0L) },
        accuracyMeters = location?.accuracyMeters?.toDouble(),
        locationUnavailableReason = if (location == null) unavailableReason ?: SosLocationUnavailableReason.Unavailable else null,
    )

    private fun GeoLocation.isStale(): Boolean =
        timestampMillis?.let { nowMillis() - it > freshLocationMillis } ?: false

    private data class LocationResolution(
        val location: GeoLocation? = null,
        val unavailableReason: SosLocationUnavailableReason? = null,
    )
}
