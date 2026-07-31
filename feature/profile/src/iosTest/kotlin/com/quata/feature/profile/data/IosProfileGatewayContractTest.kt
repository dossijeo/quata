package com.quata.feature.profile.data

import com.quata.core.data.toFoundationData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IosProfileGatewayContractTest {
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
    fun sos_replace_is_delete_then_post_with_positions() = runTest {
        val transport = RecordingTransport()
        val gateway = gateway(transport)

        gateway.saveEmergencyContacts("profile-1", listOf("peer-b", "peer-a", "peer-b"))

        assertEquals(listOf("DELETE", "POST"), transport.requests.map { it.method })
        assertEquals(
            "[{\"profile_id\":\"profile-1\",\"contact_profile_id\":\"peer-b\",\"position\":1},{\"profile_id\":\"profile-1\",\"contact_profile_id\":\"peer-a\",\"position\":2}]",
            transport.requests.last().body,
        )
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
}
