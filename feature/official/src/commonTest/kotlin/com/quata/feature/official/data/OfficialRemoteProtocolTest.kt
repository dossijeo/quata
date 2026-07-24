package com.quata.feature.official.data

import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialPostType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficialRemoteProtocolTest {
    @Test
    fun buildsOfficialPostWithPortableContentAndInteractionMetadata() {
        val posts = buildOfficialDomainPosts(
            posts = listOf(
                OfficialRemotePost(
                    id = "official-1",
                    profileId = "author",
                    contentHtml = "<p>Hola &amp; bienvenida</p>",
                    language = "en",
                    postType = "news",
                    mediaType = "image",
                    publishedAt = "2026-07-24T10:00:00Z",
                ),
            ),
            comments = listOf(
                OfficialRemoteComment(id = "root", postId = "official-1", profileId = "reader", body = "Base"),
                OfficialRemoteComment(id = "reply", postId = "official-1", profileId = "reader", body = "[reply:root:Lectura] Respuesta"),
            ),
            likes = listOf(OfficialRemoteLike(postId = "official-1", profileId = "viewer")),
            profiles = listOf(
                OfficialRemoteProfile(id = "author", fallbackName = "Administración", isOfficial = true),
                OfficialRemoteProfile(id = "reader", displayName = "Lectura"),
            ),
            currentUserId = "viewer",
            defaultTitle = "Aviso oficial",
            defaultCommentAuthor = "Usuario",
        )

        val post = posts.single()
        assertEquals("Hola & bienvenida", post.title)
        assertEquals("Hola & bienvenida", post.contentPlain)
        assertEquals("Administración", post.author.displayName)
        assertEquals(OfficialPostLanguage.English, post.language)
        assertEquals(OfficialPostType.News, post.type)
        assertEquals(OfficialMediaType.Image, post.mediaType)
        assertEquals(1, post.likesCount)
        assertTrue(post.isLikedByCurrentUser)
        assertEquals("root", post.comments.single { it.id == "reply" }.replyToCommentId)
        assertEquals("Base", post.comments.single { it.id == "reply" }.replyToMessage)
    }

    @Test
    fun gathersDistinctProfileIdsAcrossPostEntities() {
        assertEquals(
            listOf("author", "reader", "viewer"),
            officialRemoteProfileIds(
                posts = listOf(OfficialRemotePost(id = "post", profileId = "author")),
                comments = listOf(OfficialRemoteComment(id = "comment", profileId = "reader")),
                likes = listOf(OfficialRemoteLike(profileId = "viewer")),
            ),
        )
    }
}
