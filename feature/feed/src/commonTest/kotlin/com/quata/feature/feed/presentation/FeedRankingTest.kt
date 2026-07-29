package com.quata.feature.feed.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.time.ExperimentalTime
import com.quata.core.model.Post
import com.quata.core.model.User

@OptIn(ExperimentalTime::class)
class FeedRankingTest {
    private val now = Instant.parse("2026-07-30T12:00:00Z")
    private val zone = TimeZone.UTC

    @Test fun parsesRelativeAndSpanishLocalDates() {
        assertEquals(12, feedPublishedAt("Ahora", now, zone).hour)
        assertEquals(29, feedPublishedAt("Ayer", now, zone).day)
        assertEquals(11, feedPublishedAt("hace 1 h sufijo", now, zone).hour)
        assertEquals(55, feedPublishedAt("hace 5 minutos", now, zone).minute)
        assertEquals(29, feedPublishedAt("hace 1 día", now, zone).day)
        assertEquals(23, feedPublishedAt("hace 1 sem", now, zone).day)
        feedPublishedAt("30/7/2026, 10:11:12", now, zone).also { assertEquals(30, it.day); assertEquals(10, it.hour); assertEquals(11, it.minute); assertEquals(12, it.second) }
        assertEquals(10, feedPublishedAt("30/7/2026 10:11:12", now, zone).hour)
    }

    @Test fun invalidAndPrefixMismatchFallBackToNow() {
        assertEquals(12, feedPublishedAt("antes hace 1 h", now, zone).hour)
        assertEquals(12, feedPublishedAt("invalid", now, zone).hour)
    }

    @Test fun parsesSqlIsoMillisecondsAndOffsets() {
        assertEquals(10, feedPublishedAt("2026-07-30 10:11:12", now, zone).hour)
        assertEquals(10, feedPublishedAt("2026-07-30T10:11:12.123", now, zone).hour)
        assertEquals(10, feedPublishedAt("2026-07-30T12:11:12+02:00", now, zone).hour)
    }

    @Test fun ranksLikesThenNewestPublishedAt() {
        val author = User(id = "u", email = "u@example.test", displayName = "U")
        val posts = listOf(
            Post("old", author, "", createdAt = "2026-07-29T10:00:00Z", likesCount = 2),
            Post("new", author, "", createdAt = "2026-07-30T10:00:00Z", likesCount = 2),
            Post("liked", author, "", createdAt = "2026-07-20T10:00:00Z", likesCount = 3),
        )
        val ranks = calculateFeedRanking(posts, now, zone)
        assertEquals(1, ranks["liked"]); assertEquals(2, ranks["new"]); assertEquals(3, ranks["old"])
    }
}
