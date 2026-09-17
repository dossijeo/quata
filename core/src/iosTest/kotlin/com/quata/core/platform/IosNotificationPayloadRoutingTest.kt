package com.quata.core.platform

import com.quata.core.navigation.QuataChatDeepLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Adapter regressions only; these do not certify native notification delivery or Reply. */
class IosNotificationPayloadRoutingTest {
    @Test
    fun rootFieldsReachTheChatHost() {
        assertRoute(
            mapOf("conversation_id" to "sb:7", "message_id" to "m-2", "aps" to mapOf("alert" to "test")),
            QuataChatDeepLink("sb:7", "m-2"),
        )
    }

    @Test
    fun eachSupportedNestedEnvelopeStillRoutes() {
        for (envelope in listOf("data", "quata", "payload")) {
            assertRoute(
                mapOf(envelope to mapOf("conversation_id" to "sb:8", "message_id" to "m-3")),
                QuataChatDeepLink("sb:8", "m-3"),
            )
        }
    }

    @Test
    fun rootFieldsOverrideConflictingNestedFields() {
        assertRoute(
            mapOf(
                "data" to mapOf("conversation_id" to "sb:8", "message_id" to "nested"),
                "conversation_id" to "sb:7",
                "message_id" to "root",
            ),
            QuataChatDeepLink("sb:7", "root"),
        )
    }

    @Test
    fun nestedFieldsFillOnlyFieldsMissingAtRoot() {
        assertRoute(
            mapOf("data" to mapOf("conversation_id" to "sb:8", "message_id" to "m-3"), "conversation_id" to "sb:7"),
            QuataChatDeepLink("sb:7", "m-3"),
        )
    }

    @Test
    fun missingOrExplicitlyEmptyRootTargetDoesNotRoute() {
        for (payload in listOf(
            mapOf("aps" to mapOf("alert" to "test")),
            mapOf("data" to mapOf("conversation_id" to "sb:8"), "conversation_id" to ""),
        )) {
            val host = RecordingHost()
            val adapter = IosNotificationDeepLinkAdapter().apply { attachHost(host) }
            assertIs<PlatformResult.Failure>(adapter.handleApnsTap(payload))
            assertNull(host.target)
        }
    }

    private fun assertRoute(payload: Map<*, *>, expected: QuataChatDeepLink) {
        val host = RecordingHost()
        val adapter = IosNotificationDeepLinkAdapter().apply { attachHost(host) }
        assertIs<PlatformResult.Success<Unit>>(adapter.handleApnsTap(payload))
        assertEquals(expected, host.target)
        assertEquals(1, host.calls)
    }

    private class RecordingHost : IosNotificationDeepLinkHost {
        var target: QuataChatDeepLink? = null
        var calls = 0
        override fun openChat(target: QuataChatDeepLink) {
            this.target = target
            calls++
        }
    }
}
