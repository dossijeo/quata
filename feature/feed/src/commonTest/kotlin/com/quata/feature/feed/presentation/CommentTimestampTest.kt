package com.quata.feature.feed.presentation

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class CommentTimestampTest {
    private val zone = TimeZone.UTC
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-07-30T09:08:07Z")
    }

    @Test
    fun writesTheExactAndroidLocalSourceFormat() {
        assertEquals("30/7/2026, 9:08:07", nowCommentTimestamp(clock, zone))
    }

    @Test
    fun displaysEveryAndroidLocalSqlAndIsoInput() {
        listOf(
            "3/7/2026, 9:08:07",
            "3/7/2026 9:08:07",
            "2026-07-03 09:08:07",
            "2026-07-03T09:08:07",
            "2026-07-03T09:08:07.123",
        ).forEach { value -> assertEquals("03/07/2026 09:08", formatCommentTimestamp(value, zone), value) }
    }

    @Test
    fun convertsOffsetsUsingTheInjectedTimezone() {
        assertEquals("03/07/2026 07:08", formatCommentTimestamp("2026-07-03T09:08:07+02:00", zone))
        assertEquals("03/07/2026 09:08", formatCommentTimestamp("2026-07-03T09:08:07Z", zone))
    }

    @Test
    fun keepsTheAndroidBlankAndUnparseableFallbacks() {
        assertEquals("", formatCommentTimestamp("   ", zone))
        assertEquals("not-a-date", formatCommentTimestamp(" not-a-date ", zone))
        assertEquals("2026-07-03T09:08:07.12", formatCommentTimestamp("2026-07-03T09:08:07.12", zone))
    }
}
