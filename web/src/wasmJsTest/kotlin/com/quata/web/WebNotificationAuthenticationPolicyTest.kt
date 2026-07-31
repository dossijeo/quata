package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals

class WebNotificationAuthenticationPolicyTest {
    @Test
    fun clickQueuesItsConversationWhileSwipeOnlyOpensTheCurrentRoutePrompt() {
        val events = mutableListOf<String>()
        val policy = WebNotificationAuthenticationPolicy(
            onConversationAuthenticationRequired = { events += "chat:$it" },
            onDismissAuthenticationRequired = { events += "prompt:current-route" },
        )

        policy.requestForClick("conversation-7")
        policy.requestForDismiss()

        assertEquals(
            listOf("chat:conversation-7", "prompt:current-route"),
            events,
        )
    }
}
