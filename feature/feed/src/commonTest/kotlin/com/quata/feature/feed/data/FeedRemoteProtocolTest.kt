package com.quata.feature.feed.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FeedRemoteProtocolTest {
    @Test
    fun buildsPostWithFallbackProfileAndCurrentUserLike() {
        val posts = buildFeedDomainPosts(
            posts = listOf(FeedRemotePost(id = "post-1", profileId = "author", body = "Hola &amp; adi\u00f3s")),
            comments = emptyList(),
            likes = listOf(FeedRemoteLike(postId = "post-1", profileId = "viewer")),
            profiles = listOf(FeedRemoteProfile(id = "author", fallbackName = "Nombre alternativo", phoneLocal = "123")),
            currentUserId = "viewer",
        )

        assertEquals("Nombre alternativo", posts.single().author.displayName)
        assertEquals("Hola & adi\u00f3s", posts.single().text)
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

    @Test
    fun mapsPortableWireFieldsWithoutDependingOnJsonRuntime() {
        val post = feedRemotePostFromFields(
            mapOf(
                "id" to "post-1",
                "profile_id" to "author-1",
                "body" to "Hola",
                "created_at" to "2026-07-25T10:00:00Z",
            )::get,
        )
        val comment = feedRemoteCommentFromFields(
            mapOf("id" to "comment-1", "post_id" to "post-1", "body" to "Respuesta")::get,
        )
        val like = feedRemoteLikeFromFields(mapOf("post_id" to "post-1", "profile_id" to "viewer")::get)
        val profile = feedRemoteProfileFromFields(
            field = mapOf("id" to "author-1", "display_name" to "Autora", "avatar" to "fallback.png")::get,
            booleanField = { name -> name == "is_official" },
        )

        assertEquals("post-1", post.id)
        assertEquals("author-1", post.profileId)
        assertEquals("comment-1", comment.id)
        assertEquals("post-1", like.postId)
        assertEquals("Autora", profile.displayName)
        assertTrue(profile.isOfficial)
        assertFalse(profile.isAdmin)
    }

    @Test
    fun rejectsWireModelsWithoutRequiredId() {
        val failure = assertFailsWith<IllegalStateException> {
            feedRemotePostFromFields(emptyMap<String, String?>()::get)
        }

        assertEquals("feed_remote_response_missing_id", failure.message)
    }

    @Test
    fun allowsNativeTransportToPreserveItsMissingIdContract() {
        val failure = assertFailsWith<IllegalStateException> {
            feedRemotePostFromFields(
                field = emptyMap<String, String?>()::get,
                missingIdError = { IllegalStateException("web_feed_response_missing_id") },
            )
        }

        assertEquals("web_feed_response_missing_id", failure.message)
    }
}
