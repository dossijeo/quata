package com.quata.feature.profile.domain

import com.quata.core.model.CountryPrefix
import com.quata.core.platform.GeoLocation
import com.quata.core.platform.LocationService
import com.quata.core.platform.PermissionService
import com.quata.core.platform.PermissionStatus
import com.quata.core.platform.PlatformPermission
import com.quata.core.platform.PlatformResult
import com.quata.core.text.parseSosShortcode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SosDispatchCoordinatorTest {
    @Test
    fun emptyContactsRequiresConfigurationWithoutSending() = runTest {
        val transport = RecordingTransport()
        val coordinator = coordinator(profile = profile(contactIds = emptyList()), transport = transport)

        val outcome = coordinator.dispatch()

        assertIs<SosDispatchOutcome.NeedsConfiguration>(outcome)
        assertTrue(transport.initialMessages.isEmpty())
    }

    @Test
    fun immediateAlertWithoutLocationProducesOneLaterFreshUpdate() = runTest {
        var now = 100_000L
        val locations = ArrayDeque<PlatformResult<GeoLocation>>().apply {
            add(PlatformResult.Failure("unavailable"))
            add(PlatformResult.Success(GeoLocation(40.4, -3.7, 7f, now)))
        }
        val transport = RecordingTransport()
        val coordinator = coordinator(
            profile = profile(),
            transport = transport,
            location = object : LocationService {
                override suspend fun currentLocation(): PlatformResult<GeoLocation> = locations.removeFirst()
            },
            nowMillis = { now },
        )

        val outcome = assertIs<SosDispatchOutcome.Sent>(coordinator.dispatch())
        advanceUntilIdle()

        assertTrue(outcome.locationRecoveryStarted)
        assertEquals(1, transport.initialMessages.size)
        assertFalse(transport.initialMessages.single().parseSosShortcode()!!.hasLocation)
        assertEquals(1, transport.updates.size)
        assertTrue(transport.updates.single().parseSosShortcode()!!.hasLocation)
    }

    @Test
    fun secondTapIsIgnoredWhileTheFirstRpcIsInFlight() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val transport = object : RecordingTransport() {
            override suspend fun sendInitial(
                expectedActorId: String,
                contactIds: List<String>, text: String, latitude: Double?, longitude: Double?, accuracyMeters: Double?,
            ): SosInitialSendResult {
                initialMessages += text
                entered.complete(Unit)
                release.await()
                return SosInitialSendResult.Sent("sb:7")
            }
        }
        val coordinator = coordinator(profile(), transport)
        val first = async { coordinator.dispatch() }
        entered.await()

        assertEquals(SosDispatchOutcome.IgnoredWhileSending, coordinator.dispatch())
        release.complete(Unit)
        assertIs<SosDispatchOutcome.Sent>(first.await())
        assertEquals(1, transport.initialMessages.size)
    }

    @Test
    fun backendCooldownRemainsTypedAndDoesNotStartRecovery() = runTest {
        val transport = RecordingTransport(initialResult = SosInitialSendResult.RateLimited(42_000L))
        val coordinator = coordinator(profile(), transport)

        val outcome = assertIs<SosDispatchOutcome.RateLimited>(coordinator.dispatch())
        advanceUntilIdle()

        assertEquals(42_000L, outcome.remainingMillis)
        assertTrue(transport.updates.isEmpty())
        assertFalse(coordinator.state.value.isRecoveringLocation)
    }

    @Test
    fun cancelStopsAnInFlightDispatchWithoutPublishingLateState() = runTest {
        val entered = CompletableDeferred<Unit>()
        val transport = object : RecordingTransport() {
            override suspend fun sendInitial(
                expectedActorId: String,
                contactIds: List<String>,
                text: String,
                latitude: Double?,
                longitude: Double?,
                accuracyMeters: Double?,
            ): SosInitialSendResult {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        val coordinator = coordinator(profile(), transport)
        val dispatch = async { coordinator.dispatch() }
        entered.await()

        coordinator.cancel()
        dispatch.join()

        assertTrue(dispatch.isCancelled)
        assertEquals(SosDispatchState(), coordinator.state.value)
    }

    @Test
    fun cancellingCallerDuringNonCancellableLocationDoesNotReachTransport() = runTest {
        val locationEntered = CompletableDeferred<Unit>()
        lateinit var resumeLocation: () -> Unit
        val transport = RecordingTransport()
        val coordinator = coordinator(
            profile(),
            transport,
            location = object : LocationService {
                override suspend fun currentLocation(): PlatformResult<GeoLocation> = suspendCoroutine { continuation ->
                    resumeLocation = {
                        continuation.resume(PlatformResult.Success(GeoLocation(40.4, -3.7, 5f, 100_000L)))
                    }
                    locationEntered.complete(Unit)
                }
            },
        )
        val caller = async { coordinator.dispatch() }
        locationEntered.await()

        caller.cancel()
        resumeLocation()
        caller.join()

        assertTrue(caller.isCancelled)
        assertTrue(transport.initialMessages.isEmpty())
        assertFalse(coordinator.state.value.isSending)
    }

    @Test
    fun lateInitialResponseAfterCallerCancellationCannotStartRecoveryOrUnlockEarly() = runTest {
        val sendEntered = CompletableDeferred<Unit>()
        lateinit var resumeSend: () -> Unit
        val transport = object : RecordingTransport() {
            override suspend fun sendInitial(
                expectedActorId: String,
                contactIds: List<String>,
                text: String,
                latitude: Double?,
                longitude: Double?,
                accuracyMeters: Double?,
            ): SosInitialSendResult = suspendCoroutine { continuation ->
                initialMessages += text
                resumeSend = { continuation.resume(SosInitialSendResult.Sent("sb:7")) }
                sendEntered.complete(Unit)
            }
        }
        val coordinator = coordinator(
            profile(),
            transport,
            location = object : LocationService {
                override suspend fun currentLocation(): PlatformResult<GeoLocation> =
                    PlatformResult.Failure("unavailable")
            },
        )
        val caller = async { coordinator.dispatch() }
        sendEntered.await()
        caller.cancel()

        assertEquals(SosDispatchOutcome.IgnoredWhileSending, coordinator.dispatch())
        resumeSend()
        caller.join()
        advanceUntilIdle()

        assertTrue(caller.isCancelled)
        assertTrue(transport.updates.isEmpty())
        assertFalse(coordinator.state.value.isRecoveringLocation)
    }

    @Test
    fun recoveryRetriesUseOneActorBoundIdempotencyKey() = runTest {
        val locations = ArrayDeque<PlatformResult<GeoLocation>>().apply {
            add(PlatformResult.Failure("unavailable"))
            add(PlatformResult.Success(GeoLocation(40.4, -3.7, 7f, 100_000L)))
            add(PlatformResult.Success(GeoLocation(40.4, -3.7, 7f, 100_000L)))
        }
        val transport = RecordingTransport(updateResults = ArrayDeque(listOf(Result.failure(IllegalStateException("lost response")), Result.success(Unit))))
        val coordinator = coordinator(
            profile(),
            transport,
            location = object : LocationService {
                override suspend fun currentLocation(): PlatformResult<GeoLocation> = locations.removeFirst()
            },
        )

        assertIs<SosDispatchOutcome.Sent>(coordinator.dispatch())
        advanceUntilIdle()

        assertEquals(listOf("actor-1", "actor-1"), transport.updateActors)
        assertEquals(2, transport.updateClientMessageIds.size)
        assertEquals(1, transport.updateClientMessageIds.distinct().size)
    }

    @Test
    fun savingConfigurationWithoutPendingSosIsANoop() = runTest {
        val transport = RecordingTransport()
        val coordinator = coordinator(profile(), transport)

        assertEquals(SosDispatchOutcome.NoPendingConfiguration, coordinator.resumeAfterConfigurationSaved())
        assertTrue(transport.initialMessages.isEmpty())
    }

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        profile: UserProfile,
        transport: SosDispatchTransport,
        location: LocationService = object : LocationService {
            override suspend fun currentLocation(): PlatformResult<GeoLocation> =
                PlatformResult.Success(GeoLocation(40.4, -3.7, 5f, 100_000L))
        },
        nowMillis: () -> Long = { 100_000L },
    ) = SosDispatchCoordinator(
        profileRepository = FakeProfileRepository(profile),
        actorProvider = SosActorProvider { "actor-1" },
        transport = transport,
        permissions = GrantedPermissions,
        location = location,
        scope = this,
        nowMillis = nowMillis,
        recoveryPollMillis = 1L,
        recoveryWindowMillis = 10L,
    )

    private fun profile(contactIds: List<String> = listOf("contact-1")) = UserProfile(
        displayName = "Gabriel",
        neighborhood = "Centro",
        countryCode = "+34",
        phone = "600000000",
        emergencyContactIds = contactIds,
        emergencyMessage = "Necesito ayuda",
        emergencyMessageIsDefault = false,
    )
}

private open class RecordingTransport(
    private val initialResult: SosInitialSendResult = SosInitialSendResult.Sent("sb:7"),
    private val updateResults: ArrayDeque<Result<Unit>> = ArrayDeque(),
) : SosDispatchTransport {
    val initialMessages = mutableListOf<String>()
    val updates = mutableListOf<String>()
    val initialActors = mutableListOf<String>()
    val updateActors = mutableListOf<String>()
    val updateClientMessageIds = mutableListOf<String>()

    override suspend fun sendInitial(
        expectedActorId: String,
        contactIds: List<String>, text: String, latitude: Double?, longitude: Double?, accuracyMeters: Double?,
    ): SosInitialSendResult {
        initialActors += expectedActorId
        initialMessages += text
        return initialResult
    }

    override suspend fun sendLocationUpdate(
        expectedActorId: String,
        conversationId: String,
        text: String,
        clientMessageId: String,
    ): Result<Unit> {
        updateActors += expectedActorId
        updateClientMessageIds += clientMessageId
        updates += text
        return if (updateResults.isEmpty()) Result.success(Unit) else updateResults.removeFirst()
    }
}

private object GrantedPermissions : PermissionService {
    override suspend fun status(permission: PlatformPermission) = PermissionStatus.Granted
    override suspend fun request(permission: PlatformPermission) = PermissionStatus.Granted
}

private class FakeProfileRepository(private val profile: UserProfile) : ProfileRepository {
    private val model = ProfileEditModel(
        profile,
        ProfileEditConfig(
            countryPrefixes = listOf(CountryPrefix("+34", "España (+34)")),
            secretQuestions = emptyList(),
            emergencyCandidates = emptyList(),
        ),
    )

    override fun observeProfileEditModel(): Flow<Result<ProfileEditModel>> = flowOf(Result.success(model))
    override suspend fun getProfileEditModel(): Result<ProfileEditModel> = Result.success(model)
    override suspend fun saveProfile(update: ProfileUpdate): Result<Unit> = Result.success(Unit)
    override suspend fun saveEmergencySettings(contactIds: List<String>, message: String, messageIsDefault: Boolean) = Result.success(Unit)
    override fun defaultEmergencyMessage(displayName: String) = "Emergency from $displayName"
    override fun changesSavedMessage() = "Saved"
    override fun emergencyContactsSavedMessage() = "SOS saved"
}
