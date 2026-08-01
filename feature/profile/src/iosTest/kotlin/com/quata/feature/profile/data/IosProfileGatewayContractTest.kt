package com.quata.feature.profile.data

import com.quata.core.data.toFoundationData
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.model.AuthSession
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.PlatformFile
import com.quata.core.model.currentEpochSeconds
import com.quata.core.preferences.SessionStorage
import com.quata.core.session.IosAuthSessionRefresher
import com.quata.core.session.IosRenewableAuthSession
import com.quata.feature.profile.presentation.IosProfileSosRuntimeBootstrap
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IosProfileGatewayContractTest {
    @Test
    fun session_resolution_that_never_emits_is_bounded_and_visible() = runTest {
        val provider = IosProfileKeychainSessionProvider(
            resolver = { awaitCancellation() },
            timeoutMillis = 50,
        )

        val failure = assertFailsWith<IllegalStateException> { provider.currentSession() }

        assertEquals("ios_profile_session_timeout", failure.message)
    }

    @Test
    fun expired_session_refresh_success_returns_the_renewed_bearer() = runTest {
        val storage = MemorySessionStorage(expiredSession())
        val renewed = expiredSession().copy(accessToken = "renewed-token", expiresAt = currentEpochSeconds() + 3_600)
        val renewable = IosRenewableAuthSession(IosAuthSessionRefresher { renewed }, storage)

        val resolved = IosProfileKeychainSessionProvider(renewable).currentSession()

        assertEquals("renewed-token", resolved?.accessToken)
        assertEquals("profile-1", resolved?.profileId)
    }

    @Test
    fun profile_bootstrap_retains_the_exact_feed_auth_renewable_session() {
        val renewable = IosRenewableAuthSession(
            IosAuthSessionRefresher { null },
            MemorySessionStorage(expiredSession()),
        )
        val bootstrap = IosProfileSosRuntimeBootstrap(
            configuration = IosProfileRuntimeConfiguration("https://project.supabase.co", "public-key"),
            authSession = renewable,
            languageTag = "en",
        )

        assertTrue(bootstrap.usesRenewableSession(renewable))
    }

    @Test
    fun profile_bootstrap_restores_and_forwards_required_appearance_state() {
        val renewable = IosRenewableAuthSession(
            IosAuthSessionRefresher { null },
            MemorySessionStorage(expiredSession()),
        )
        val bootstrap = IosProfileSosRuntimeBootstrap(
            configuration = IosProfileRuntimeConfiguration("https://project.supabase.co", "public-key"),
            authSession = renewable,
            languageTag = "en",
        )
        var persistedTouchFlow: Boolean? = null
        var persistedTheme: String? = null

        val dependencies = bootstrap.profileHostDependencies(
            onLogout = {},
            onDeactivateAccount = {},
            onDeleteAccountData = {},
            filePicker = UnsupportedFilePicker,
            touchFlowEnabled = true,
            themeModeStorageValue = "dark-mode",
            onTouchFlowEnabledChange = { persistedTouchFlow = it },
            onThemeModeStorageValueChange = { persistedTheme = it },
        )

        assertTrue(dependencies.touchFlowEnabled)
        assertEquals(QuataThemeMode.Dark, dependencies.themeMode)
        dependencies.onTouchFlowEnabledChange(false)
        dependencies.onThemeModeChange(QuataThemeMode.Light)
        assertEquals(false, persistedTouchFlow)
        assertEquals("light-mode", persistedTheme)
    }

    @Test
    fun expired_session_refresh_failure_never_reuses_the_stale_bearer() = runTest {
        val renewable = IosRenewableAuthSession(IosAuthSessionRefresher { null }, MemorySessionStorage(expiredSession()))

        val failure = assertFailsWith<IllegalStateException> {
            IosProfileKeychainSessionProvider(renewable).currentSession()
        }

        assertEquals("ios_profile_session_refresh_failed", failure.message)
    }

    @Test
    fun cancelling_session_resolution_cancels_the_in_flight_resolver() = runTest {
        var cancelled = false
        val provider = IosProfileKeychainSessionProvider(
            resolver = {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            },
            timeoutMillis = 60_000,
        )
        val job = launch { provider.currentSession() }
        runCurrent()

        job.cancelAndJoin()

        assertTrue(cancelled)
    }

    @Test
    fun own_patch_has_bearer_filter_and_allowlisted_body() = runTest {
        val transport = RecordingTransport()
        val gateway = gateway(transport)

        gateway.saveProfile("profile-1", mapOf("display_name" to "Ada", "forbidden" to "drop"))

        val request = transport.requests.single()
        assertEquals("PATCH", request.method)
        assertTrue(request.url.endsWith("/rest/v1/community_profiles?id=eq.profile-1"))
        assertEquals("Bearer access-token", request.headers["Authorization"])
        assertEquals("{\"display_name\":\"Ada\"}", request.body)
    }

    @Test
    fun sos_replace_serializes_exact_valid_json_for_zero_one_two_and_five_rows() = runTest {
        listOf(0, 1, 2, 5).forEach { count ->
            val transport = RecordingTransport()
            val gateway = gateway(transport)
            val ids = (1..count).map { "peer-$it" }

            gateway.saveEmergencyContacts("profile-1", ids)

            assertEquals(if (count == 0) listOf("DELETE") else listOf("DELETE", "POST"), transport.requests.map { it.method })
            if (count > 0) {
                val expected = ids.mapIndexed { index, id ->
                    "{\"profile_id\":\"profile-1\",\"contact_profile_id\":\"$id\",\"position\":${index + 1}}"
                }.joinToString(separator = ",", prefix = "[", postfix = "]")
                assertEquals(expected, transport.requests.last().body, "row count $count")
            }
        }
    }

    @Test
    fun missing_session_never_reaches_transport() = runTest {
        val transport = RecordingTransport()
        val gateway = gateway(transport, session = null)
        assertFailsWith<IllegalStateException> {
            gateway.saveProfile("profile-1", mapOf("display_name" to "Ada"))
        }
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun recovery_rejects_2xx_false_and_malformed_bodies() = runTest {
        listOf("{\"ok\":false}", "not-json").forEach { response ->
            val transport = RecordingTransport(response)
            assertFailsWith<IllegalStateException> {
                gateway(transport).saveRecoverySecret("profile-1", "pet", "Luna")
            }
        }
    }

    @Test
    fun failed_sos_post_is_not_reported_as_success() = runTest {
        val transport = RecordingTransport(failPost = true)
        assertFailsWith<IllegalStateException> {
            gateway(transport).saveEmergencyContacts("profile-1", listOf("peer-a"))
        }
        assertEquals(listOf("DELETE", "POST"), transport.requests.map { it.method })
    }

    @Test
    fun local_avatar_uses_android_bucket_path_headers_and_public_url() = runTest {
        var recorded: IosProfileAvatarUploadRequest? = null
        val uploader = IosProfileAvatarUploader(
            configuration = IosProfileRuntimeConfiguration("https://project.supabase.co", "public-key"),
            sessionProvider = IosProfileSessionProvider { IosProfileSession("access-token", "profile-1") },
            transport = IosProfileAvatarBinaryTransport { recorded = it },
            encoder = IosProfileAvatarEncoder { platform.Foundation.NSData() },
            token = { "fixed-token" },
        )

        val result = uploader.uploadIfNeeded("profile-1", "file:///tmp/avatar.png")

        assertEquals("https://project.supabase.co/storage/v1/object/public/community-posts/avatars/profile-1/fixed-token.jpg", result)
        assertEquals("https://project.supabase.co/storage/v1/object/community-posts/avatars/profile-1/fixed-token.jpg", recorded?.url)
        assertEquals("Bearer access-token", recorded?.headers?.get("Authorization"))
        assertEquals("true", recorded?.headers?.get("x-upsert"))
    }

    @Test
    fun avatar_actor_mismatch_fails_before_encoding_or_upload() = runTest {
        var touched = false
        val uploader = IosProfileAvatarUploader(
            configuration = IosProfileRuntimeConfiguration("https://project.supabase.co", "public-key"),
            sessionProvider = IosProfileSessionProvider { IosProfileSession("access-token", "other-profile") },
            transport = IosProfileAvatarBinaryTransport { touched = true },
            encoder = IosProfileAvatarEncoder { touched = true; platform.Foundation.NSData() },
            token = { "fixed-token" },
        )
        assertFailsWith<IllegalStateException> {
            uploader.uploadIfNeeded("profile-1", "file:///tmp/avatar.png")
        }
        assertTrue(!touched)
    }

    private fun gateway(
        transport: IosProfileHttpTransport,
        session: IosProfileSession? = IosProfileSession("access-token", "profile-1"),
    ) = IosProfilePostgrestGateway(
        configuration = IosProfileRuntimeConfiguration("https://project.supabase.co", "public-key"),
        sessionProvider = IosProfileSessionProvider { session },
        transport = transport,
    )

    private class RecordingTransport(
        private val response: String = "{\"ok\":true}",
        private val failPost: Boolean = false,
    ) : IosProfileHttpTransport {
        val requests = mutableListOf<IosProfileHttpRequest>()
        override suspend fun execute(request: IosProfileHttpRequest): platform.Foundation.NSData {
            requests += request
            if (failPost && request.method == "POST" && request.url.contains("community_emergency_contacts")) {
                error("post_failed")
            }
            return response.encodeToByteArray().toFoundationData()
        }
    }

    private class MemorySessionStorage(initial: AuthSession?) : SessionStorage {
        private var value = initial
        override fun saveSession(session: AuthSession) { value = session }
        override fun getSession(): AuthSession? = value
        override fun clear() { value = null }
    }

    private object UnsupportedFilePicker : FilePickerService {
        override suspend fun pickFiles(
            acceptedMimeTypes: List<String>,
            allowMultiple: Boolean,
        ): PlatformResult<List<PlatformFile>> = PlatformResult.Unsupported
    }

    private fun expiredSession() = AuthSession(
        token = "stale-token",
        userId = "profile-1",
        email = "profile@example.test",
        displayName = "Profile",
        accessToken = "stale-token",
        refreshToken = "refresh-token",
        expiresAt = currentEpochSeconds() - 1,
    )
}
