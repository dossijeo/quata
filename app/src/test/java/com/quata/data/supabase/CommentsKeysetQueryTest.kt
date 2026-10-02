package com.quata.data.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class CommentsKeysetQueryTest {
    @Test
    fun officialPagesAndInvalidationTriggersExcludeSoftDeletedRows() {
        val page = commentsKeysetQuery(
            select = "id,official_post_id,created_at",
            postIdColumn = "official_post_id",
            postIds = listOf("post-b", "post-a", "post-b"),
            afterIdExclusive = "comment-500",
            limit = 500,
            excludeSoftDeleted = true,
        )
        val trigger = commentsKeysetQuery(
            select = "id",
            postIdColumn = "official_post_id",
            postIds = listOf("post-a"),
            afterIdExclusive = null,
            limit = 1,
            excludeSoftDeleted = true,
        )

        assertEquals("is.null", page["deleted_at"])
        assertEquals("is.null", trigger["deleted_at"])
        assertEquals("gt.comment-500", page["id"])
        assertEquals("id.asc", page["order"])
        assertEquals("500", page["limit"])
        assertEquals("in.(post-b,post-a)", page["official_post_id"])
    }

    @Test
    fun communityPagesDoNotReferenceAColumnAbsentFromTheirSchema() {
        val query = commentsKeysetQuery(
            select = "id,post_id,created_at",
            postIdColumn = "post_id",
            postIds = listOf("post-a"),
            afterIdExclusive = null,
            limit = 500,
            excludeSoftDeleted = false,
        )

        assertNull(query["deleted_at"])
        assertEquals("in.(post-a)", query["post_id"])
    }

    @Test
    fun rejectsEmptyOrAmbiguousQueries() {
        expectIllegalArgument {
            commentsKeysetQuery("id", "post_id", emptyList(), null, 500, false)
        }
        expectIllegalArgument {
            commentsKeysetQuery("id", "post_id", listOf("post-a"), null, 0, false)
        }
        expectIllegalArgument {
            commentsKeysetQuery("id", "profile_id", listOf("post-a"), null, 500, false)
        }
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
