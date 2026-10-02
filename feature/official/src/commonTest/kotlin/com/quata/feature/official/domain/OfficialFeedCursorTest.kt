package com.quata.feature.official.domain

import com.quata.core.model.User
import kotlin.test.Test
import kotlin.test.assertEquals

class OfficialFeedCursorTest {
    @Test
    fun cursorPreservesServerTotalOrderTuple() {
        val post = OfficialPostItem(
            id = "00000000-0000-0000-0000-000000000105",
            author = User("author", "", "Official"),
            title = "Title",
            summary = "Summary",
            contentHtml = "<p>Body</p>",
            contentPlain = "Body",
            createdAt = "2026-10-02T09:00:00Z",
            publishedAt = "2026-10-02T09:00:00Z",
            sourceCreatedAt = "2026-10-02T08:00:00Z",
        )

        assertEquals(
            OfficialFeedCursor(
                sortAt = "2026-10-02T09:00:00Z",
                createdAt = "2026-10-02T08:00:00Z",
                postId = "00000000-0000-0000-0000-000000000105",
            ),
            post.feedCursor(),
        )
    }

    @Test
    fun cursorFallsBackToSourceCreationTimeWhenPublishedTimeIsBlank() {
        val post = OfficialPostItem(
            id = "post-1",
            author = User("author", "", "Official"),
            title = "Title",
            summary = "Summary",
            contentHtml = "<p>Body</p>",
            contentPlain = "Body",
            createdAt = "display-value",
            publishedAt = "",
            sourceCreatedAt = "2026-10-02T08:00:00Z",
        )

        assertEquals("2026-10-02T08:00:00Z", post.feedCursor().sortAt)
    }
}
