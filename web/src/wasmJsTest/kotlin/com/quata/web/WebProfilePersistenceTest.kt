package com.quata.web

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WebProfilePersistenceTest {
    @Test
    fun defaultsToOfflineDraftUntilRemoteMutationEvidenceIsVerified() {
        assertEquals(
            WebProfilePersistenceMode.OfflineDraft,
            webProfilePersistenceMode(hasRemoteRepository = true, hasConfiguredAuthenticatedSession = true),
        )
        assertEquals(
            WebProfilePersistenceMode.Remote,
            webProfilePersistenceMode(
                hasRemoteRepository = true,
                hasConfiguredAuthenticatedSession = true,
                hasVerifiedRemoteMutationEvidence = true,
            ),
        )
        assertEquals(
            WebProfilePersistenceMode.OfflineDraft,
            webProfilePersistenceMode(hasRemoteRepository = true, hasConfiguredAuthenticatedSession = false),
        )
        assertEquals(
            WebProfilePersistenceMode.OfflineDraft,
            webProfilePersistenceMode(hasRemoteRepository = false, hasConfiguredAuthenticatedSession = true),
        )
    }

    @Test
    fun serializesRemotePatchIncludingExplicitNulls() {
        val payload = mapOf("display_name" to "Ada", "avatar_url" to null).toJsonObject()

        assertEquals("Ada", payload["display_name"]?.jsonPrimitive?.content)
        assertIs<JsonNull>(payload["avatar_url"])
    }

    @Test
    fun preservesRemoteRlsFailureAsAnError() {
        val error = WebProfileRemoteMutationException(
            operation = "web_profile_patch",
            failure = WebPostgrestResult.Failure(WebPostgrestFailureKind.RlsDenied, "postgrest_http_403", 403),
        )

        assertEquals("web_profile_patch:rlsdenied:postgrest_http_403", error.message)
    }
}
