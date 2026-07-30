package com.quata.feature.feed.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class FeedUserPresenceTest {
    private val a = "00000000-0000-0000-0000-000000000001"
    private val b = "00000000-0000-0000-0000-000000000002"

    @Test fun `state and diff retain only observed profiles`() {
        val state = Json.parseToJsonElement("{\"$a\":[{\"profile_id\":\"$a\"}],\"$b\":[{\"profile_id\":\"$b\"}]}")
        val diff = Json.parseToJsonElement("{\"joins\":{},\"leaves\":{\"$a\":[{\"profile_id\":\"$a\"}]}}")
        val snapshot = FeedPresenceSnapshot().observe(listOf(a)).reduce("presence_state", state)
        assertEquals(setOf(a), snapshot.visibleOnlineProfileIds)
        assertEquals(emptySet(), snapshot.reduce("presence_diff", diff).visibleOnlineProfileIds)
    }

    @Test fun `protocol ignores malformed identifiers and unknown events`() {
        val payload = Json.parseToJsonElement("{\"bad\":[{\"profile_id\":\"nope\"}]}")
        val snapshot = FeedPresenceSnapshot().observe(listOf("bad", a)).reduce("presence_state", payload)
        assertEquals(emptySet(), snapshot.visibleOnlineProfileIds)
        assertEquals(snapshot, snapshot.reduce("broadcast", payload))
    }

    @Test fun `reconnect backoff is bounded`() {
        assertEquals(1_500L, feedPresenceReconnectDelayMillis(0))
        assertEquals(30_000L, feedPresenceReconnectDelayMillis(5))
        assertEquals(30_000L, feedPresenceReconnectDelayMillis(99))
    }

    @Test fun `a foreground re-entry reconnects only with network and session`() {
        assertEquals(false, shouldConnectFeedPresence(false, true, true))
        assertEquals(false, shouldConnectFeedPresence(true, false, true))
        assertEquals(false, shouldConnectFeedPresence(true, true, false))
        assertEquals(true, shouldConnectFeedPresence(true, true, true))
    }

    @Test fun `only the current realtime join reply may track presence`() {
        val ok = Json.parseToJsonElement("{\"status\":\"ok\"}")
        assertEquals(true, isSuccessfulFeedPresenceJoinReply("phx_reply", FeedPresenceTopic, "4", "4", ok))
        assertEquals(false, isSuccessfulFeedPresenceJoinReply("phx_reply", FeedPresenceTopic, "3", "4", ok))
        assertEquals(false, isSuccessfulFeedPresenceJoinReply("phx_reply", "phoenix", "4", "4", ok))
    }

    @Test fun `heartbeat interval matches the realtime protocol`() {
        assertEquals(25_000L, FeedPresenceHeartbeatMillis)
    }
}
