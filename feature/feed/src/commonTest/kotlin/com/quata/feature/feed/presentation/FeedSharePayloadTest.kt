package com.quata.feature.feed.presentation

import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.core.platform.PlatformResult
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedSharePayloadTest {
    @Test
    fun usesTheSameCanonicalPostLinkAsAndroid() {
        val payload = feedSharePayload(
            post = Post(
                id = "post with / unicode ü",
                author = User(id = "author", email = "author@example.test", displayName = "Author"),
                text = "",
                createdAt = "2026-07-30T12:00:00Z",
            ),
            title = "Compartir publicaci\u00f3n",
        )

        assertEquals("https://egquata.com/#post-post with / unicode ü", payload.text)
        assertEquals("Compartir publicaci\u00f3n", payload.title)
    }

    @Test
    fun spanishShareTitleKeepsItsUtf8Accent() {
        assertEquals("Compartir publicación", FeedScreenStrings().sharePostTitle)
    }

    @Test
    fun onlyUnsupportedAndFailuresSurfaceHonestFeedback() {
        val strings = FeedScreenStrings(shareUnavailable = "unavailable", shareFailed = "failed")

        assertEquals(null, feedShareResultMessage(PlatformResult.Success(Unit), strings))
        assertEquals(null, feedShareResultMessage(PlatformResult.Cancelled, strings))
        assertEquals("unavailable", feedShareResultMessage(PlatformResult.Unsupported, strings))
        assertEquals("failed", feedShareResultMessage(PlatformResult.Failure("network"), strings))
    }
}
