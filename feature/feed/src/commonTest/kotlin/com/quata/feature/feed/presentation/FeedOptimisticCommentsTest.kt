package com.quata.feature.feed.presentation

import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedOptimisticCommentsTest {
    @Test
    fun keepsLocalPendingCommentWhenBackendReadIsStale() {
        val stale = post(comments = listOf(remoteComment("seed", "Seed")))
        val existing = stale.copy(comments = stale.comments + localComment("local_1", "Reply", replyToCommentId = "seed"))

        val reconciled = stale.withLocalPendingCommentsFrom(existing)

        assertEquals(listOf("Seed", "Reply"), reconciled.comments.map(PostComment::message))
    }

    @Test
    fun dropsLocalPendingCommentWhenBackendReturnsEquivalentRemoteComment() {
        val remote = post(
            comments = listOf(
                remoteComment("seed", "Seed"),
                remoteComment("remote_1", "Reply", replyToCommentId = "seed"),
            )
        )
        val existing = post(
            comments = listOf(
                remoteComment("seed", "Seed"),
                localComment("local_1", " Reply ", replyToCommentId = "seed"),
            )
        )

        val reconciled = remote.withLocalPendingCommentsFrom(existing)

        assertEquals(listOf("seed", "remote_1"), reconciled.comments.map(PostComment::id))
    }

    private fun post(comments: List<PostComment>) = Post(
        id = "post-1",
        author = User(id = "author", email = "author@example.test", displayName = "Author", neighborhood = "Centro"),
        text = "Post",
        createdAt = "2026-08-17T20:00:00Z",
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
