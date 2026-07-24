package com.quata.feature.official.data

import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialPostType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class OfficialRemoteProtocolEdgeCaseTest {
    @Test
    fun mapsFallbackContentAndPortablePostDefaultsWhenRemoteFieldsAreAbsent() {
        val domain = OfficialRemotePost(
            id = "official-blank",
            contentHtml = "<p>Primer párrafo</p><p>Segundo párrafo</p>",
            createdAt = "2026-07-01T10:00:00Z",
        ).toOfficialDomain(
            author = OfficialRemoteProfile(id = "official", phoneLocal = "600123123").toOfficialDomainUser(),
            comments = emptyList(),
            likesCount = 0,
            likedByCurrentUser = false,
            defaultTitle = "Aviso oficial",
        )

        assertEquals("Primer párrafoSegundo párrafo", domain.title)
        assertEquals("Primer párrafoSegundo párrafo", domain.summary)
        assertEquals("2026-07-01T10:00:00Z", domain.createdAt)
        assertEquals(OfficialPostLanguage.Spanish, domain.language)
        assertEquals(OfficialPostType.Announcement, domain.type)
        assertNull(domain.mediaType)
        assertFalse(domain.isLikedByCurrentUser)
        assertEquals("600123123", domain.author.displayName)
    }

    @Test
    fun keepsRepliesLinkedWhenReplyPrecedesItsTargetAndUsesFallbackAuthors() {
        val comments = listOf(
            OfficialRemoteComment(id = "reply", profileId = "missing", body = "[reply:root:Ana] &lt;b&gt;Respuesta&lt;/b&gt;"),
            OfficialRemoteComment(id = "root", profileId = "known", body = "&lt;i&gt;Original&lt;/i&gt;"),
        ).toOfficialDomainComments(
            profilesById = mapOf("known" to OfficialRemoteProfile(id = "known", fallbackName = "Ana")),
            defaultCommentAuthor = "Vecino",
        )

        val reply = comments.first()
        assertEquals("Vecino", reply.authorName)
        assertEquals("root", reply.replyToCommentId)
        assertEquals("Ana", reply.replyToAuthorName)
        assertEquals("<i>Original</i>", reply.replyToMessage)
        assertEquals("<b>Respuesta</b>", reply.message)
    }

    @Test
    fun preservesRemoteMediaOnlyForKnownPortableTypes() {
        val video = OfficialRemotePost(id = "video", mediaType = "video").toOfficialDomain(
            author = OfficialRemoteProfile(id = "official").toOfficialDomainUser(),
            comments = emptyList(),
            likesCount = 0,
            likedByCurrentUser = false,
            defaultTitle = "Aviso",
        )
        val unsupported = OfficialRemotePost(id = "file", mediaType = "pdf").toOfficialDomain(
            author = OfficialRemoteProfile(id = "official").toOfficialDomainUser(),
            comments = emptyList(),
            likesCount = 0,
            likedByCurrentUser = false,
            defaultTitle = "Aviso",
        )

        assertEquals(OfficialMediaType.Video, video.mediaType)
        assertNull(unsupported.mediaType)
    }
}
