package com.quata.feature.official.data

import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialPostType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficialRemoteProtocolTest {
    @Test
    fun translationReadPlanMatchesAndroidLocalePolicyAndPreservesVariants() {
        assertEquals(mapOf("language" to "eq.es"), officialTranslationReadPlan("es-ES", 20).filters)
        assertEquals(20, officialTranslationReadPlan("es", 20).fetchLimit)
        assertEquals(mapOf("or" to "(language.eq.fr,language.eq.es)"), officialTranslationReadPlan("fr-FR", 20).filters)
        assertEquals(40, officialTranslationReadPlan("fr", 20).fetchLimit)
        assertEquals(mapOf("language" to "eq.es"), officialTranslationReadPlan("x);drop", 20).filters)
        assertEquals(emptyMap(), officialTranslationReadPlan("fr", 20, postId = "exact").filters)
        assertEquals(1, officialTranslationReadPlan("fr", 20, postId = "exact").fetchLimit)
    }

    @Test
    fun selectsRequestedTranslationThenSpanishFallbackPerGroup() {
        val selected = listOf(
            OfficialRemotePost(id = "es", translationGroupId = "group", language = "es", publishedAt = "2026-01-01"),
            OfficialRemotePost(id = "fr", translationGroupId = "group", language = "fr", publishedAt = "2026-01-02"),
            OfficialRemotePost(id = "standalone", language = "en", publishedAt = "2026-01-03"),
        ).selectOfficialTranslations("fr-FR")

        assertEquals(listOf("standalone", "fr"), selected.map(OfficialRemotePost::id))
        assertEquals("es", listOf(
            OfficialRemotePost(id = "es", translationGroupId = "group", language = "es"),
            OfficialRemotePost(id = "en", translationGroupId = "group", language = "en"),
        ).selectOfficialTranslations("de").single().id)
    }

    @Test
    fun mapsSharedWireScalarsWithoutTakingOverPlatformDecodingOrErrors() {
        val fields = OfficialRemoteWireFields.from(
            OfficialRemoteWireSchema.postScalarKeys + OfficialRemoteWireSchema.commentScalarKeys + OfficialRemoteWireSchema.profileScalarKeys,
            mapOf(
                "profile_id" to "author",
                "title" to "Notice",
                "official_post_id" to "post-1",
                "display_name" to "Council",
            )::get,
        )

        assertEquals("author", officialRemotePostFromWire("post-1", fields, isLive = true).profileId)
        assertEquals("Notice", officialRemotePostFromWire("post-1", fields, isLive = true).title)
        assertEquals("post-1", officialRemoteCommentFromWire("comment-1", fields).postId)
        assertEquals("post-1", officialRemoteLikeFromWire(fields).postId)
        assertEquals("Council", officialRemoteProfileFromWire("author", fields, true, true).displayName)
    }

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
