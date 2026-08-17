package com.quata.feature.official.presentation

import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostType
import kotlin.test.Test
import kotlin.test.assertEquals

class OfficialOptimisticCommentsTest {
    @Test
    fun keepsLocalPendingCommentAndCountWhenBackendReadIsStale() {
        val stale = post(comments = listOf(remoteComment("seed", "Seed")))
        val existing = stale.copy(
            comments = stale.comments + localComment("local_1", "Reply", replyToCommentId = "seed"),
            commentsCount = 2,
        )

        val reconciled = stale.withLocalPendingCommentsFrom(existing)

        assertEquals(listOf("Seed", "Reply"), reconciled.comments.map(PostComment::message))
        assertEquals(2, reconciled.commentsCount)
    }

    @Test
    fun dropsLocalPendingCommentWhenBackendReturnsEquivalentRemoteComment() {
        val remote = post(
            comments = listOf(
                remoteComment("seed", "Seed"),
                remoteComment("remote_1", "Reply", replyToCommentId = "seed"),
            ),
            commentsCount = 2,
        )
        val existing = post(
            comments = listOf(
                remoteComment("seed", "Seed"),
                localComment("local_1", " Reply ", replyToCommentId = "seed"),
            ),
            commentsCount = 2,
        )

        val reconciled = remote.withLocalPendingCommentsFrom(existing)

        assertEquals(listOf("seed", "remote_1"), reconciled.comments.map(PostComment::id))
        assertEquals(2, reconciled.commentsCount)
    }

    private fun post(comments: List<PostComment>, commentsCount: Int = comments.size) = OfficialPostItem(
        id = "official-1",
        author = User(id = "author", email = "official@example.test", displayName = "Official", neighborhood = "Centro"),
        title = "Title",
        summary = "Summary",
        contentHtml = "<p>Body</p>",
        contentPlain = "Body",
        type = OfficialPostType.Announcement,
        createdAt = "2026-08-17T20:00:00Z",
        commentsCount = commentsCount,
        comments = comments,
    )

    private fun remoteComment(id: String, message: String, replyToCommentId: String? = null) = PostComment(
        id = id,
        authorName = "Gabrielu",
        message = message,
        timestamp = "2026-08-17T20:00:00Z",
        replyToAuthorName = replyToCommentId?.let { "Gabrielo" },
        replyToCommentId = replyToCommentId,
    )

    private fun localComment(id: String, message: String, replyToCommentId: String? = null) = PostComment(
        id = id,
        authorName = "You",
        message = message,
        timestamp = "now",
        replyToAuthorName = replyToCommentId?.let { "Gabrielo" },
        replyToCommentId = replyToCommentId,
    )
}
