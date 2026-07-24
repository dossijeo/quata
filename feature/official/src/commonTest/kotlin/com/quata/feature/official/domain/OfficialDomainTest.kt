package com.quata.feature.official.domain

import com.quata.core.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfficialDomainTest {
    @Test
    fun ranksPostsByLikesThenNewestPublication() {
        val author = User(id = "official", email = "", displayName = "Official", isOfficial = true)
        val ranked = calculateOfficialPostRanking(
            listOf(
                officialPost(id = "older", author = author, likes = 4, createdAt = "2026-01-01T08:00:00Z"),
                officialPost(id = "most-liked", author = author, likes = 5, createdAt = "2025-01-01T08:00:00Z"),
                officialPost(id = "newer", author = author, likes = 4, createdAt = "2026-01-02T08:00:00Z"),
            ),
        )

        assertEquals(1, ranked.getValue("most-liked").position)
        assertEquals(2, ranked.getValue("newer").position)
        assertEquals(3, ranked.getValue("older").position)
        assertEquals(4, ranked.getValue("newer").likes)
    }

    @Test
    fun normalizesStoredReadMoreOptionsAndFallsBackForUnknownValues() {
        assertEquals(OfficialReadMoreOption.Details, OfficialReadMoreOption.fromStored("  DETAILS  "))
        assertEquals(OfficialReadMoreOption.ReadMore, OfficialReadMoreOption.fromStored("read_more"))
        assertNull(OfficialReadMoreOption.fromStored("read more"))
        assertNull(OfficialReadMoreOption.fromStored(null))
    }

    @Test
    fun mapsRemoteEnumsCaseInsensitivelyAndUsesPortableDefaults() {
        assertEquals(OfficialPostLanguage.French, OfficialPostLanguage.fromRemote("FR"))
        assertEquals(OfficialPostLanguage.Spanish, OfficialPostLanguage.fromAppLanguage("de"))
        assertEquals(OfficialPostType.Urgent, OfficialPostType.fromRemote("urgent"))
        assertEquals(OfficialPostType.Announcement, OfficialPostType.fromRemote("unknown"))
        assertEquals(OfficialMediaType.Video, OfficialMediaType.fromRemote("video"))
        assertNull(OfficialMediaType.fromRemote("document"))
    }

    private fun officialPost(
        id: String,
        author: User,
        likes: Int,
        createdAt: String,
    ) = OfficialPostItem(
        id = id,
        author = author,
        title = id,
        summary = "",
        contentHtml = "",
        contentPlain = "",
        type = OfficialPostType.Announcement,
        createdAt = createdAt,
        likesCount = likes,
    )
}
