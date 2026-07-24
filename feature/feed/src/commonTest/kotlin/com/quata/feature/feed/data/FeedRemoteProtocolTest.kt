package com.quata.feature.feed.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedRemoteProtocolTest {
    @Test
    fun buildsPostWithFallbackProfileAndCurrentUserLike() {
        val posts = buildFeedDomainPosts(
            posts = listOf(FeedRemotePost(id = "post-1", profileId = "author", body = "Hola &amp; adiÃ³s")),
            comments = emptyList(),
            likes = listOf(FeedRemoteLike(postId = "post-1", profileId = "viewer")),
            profiles = listOf(FeedRemoteProfile(id = "author", fallbackName = "Nombre alternativo", phoneLocal = "123")),
            currentUserId = "viewer",
        )

        assertEquals("Nombre alternativo", posts.single().author.displayName)
        assertEquals("Hola & adiÃ³s", posts.single().text)
        assertEquals(1, posts.single().likesCount)
        assertTrue(posts.single().isLikedByCurrentUser)
    }

    @Test
    fun preservesReplyMetadataWhenBuildingComments() {
        val posts = buildFeedDomainPosts(
            posts = listOf(FeedRemotePost(id = "post-1", profileId = "author")),
            comments = listOf(
                FeedRemoteComment(id = "root", postId = "post-1", profileId = "alice", body = "Mensaje base"),
                FeedRemoteComment(id = "reply", postId = "post-1", profileId = "bob", body = "[reply:root:Alicia] Respuesta"),
            ),
            likes = emptyList(),
            profiles = listOf(
                FeedRemoteProfile(id = "author", displayName = "Autor"),
                FeedRemoteProfile(id = "alice", displayName = "Alicia"),
                FeedRemoteProfile(id = "bob", displayName = "Beto"),
            ),
            currentUserId = "other",
        )

        val reply = posts.single().comments.single { it.id == "reply" }
        assertEquals("root", reply.replyToCommentId)
        assertEquals("Mensaje base", reply.replyToMessage)
        assertEquals("Alicia", reply.replyToAuthorName)
        assertFalse(posts.single().isLikedByCurrentUser)
    }

    @Test
    fun gathersDistinctParticipantIdsFromEveryFeedEntity() {
        assertEquals(
            listOf("author", "commenter", "liker"),
            feedRemoteProfileIds(
                posts = listOf(FeedRemotePost(id = "post", profileId = "author")),
                comments = listOf(FeedRemoteComment(id = "comment", profileId = "commenter")),
                likes = listOf(FeedRemoteLike(profileId = "liker")),
            ),
        )
    }
}
