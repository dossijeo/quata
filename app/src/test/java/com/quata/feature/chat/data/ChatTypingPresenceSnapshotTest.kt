package com.quata.feature.chat.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Executes the same reducer used by both productive Phoenix sockets on the JVM test runner. */
class ChatTypingPresenceSnapshotTest {
    @Test
    fun joinsAndLeavesAreFilteredByConversationAndPhoenixReference() {
        val initial = ChatTypingPresenceSnapshot().reduce(
            "presence_state",
            Json.parseToJsonElement("""{"me":{"metas":[{"phx_ref":"m1","conversation_id":"sb:7","typing":true}]},"peer":{"metas":[{"phx_ref":"p7","conversation_id":"sb:7","typing":true},{"phx_ref":"p8","conversation_id":"sb:8","typing":true}]}}"""),
        )
        assertEquals(setOf("peer"), initial.typingProfileIds("sb:7", "me"))
        val diffed = initial.reduce(
            "presence_diff",
            Json.parseToJsonElement("""{"joins":{"next":{"metas":[{"phx_ref":"n7","conversation_id":"sb:7","typing":true}]}},"leaves":{"peer":{"metas":[{"phx_ref":"p7","conversation_id":"sb:7","typing":true}]}}}"""),
        )
        assertEquals(setOf("next"), diffed.typingProfileIds("sb:7", "me"))
        assertEquals(setOf("peer"), diffed.typingProfileIds("sb:8", "me"))
    }
}
