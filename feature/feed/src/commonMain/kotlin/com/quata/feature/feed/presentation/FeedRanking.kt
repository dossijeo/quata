package com.quata.feature.feed.presentation

import com.quata.core.model.Post
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/** Portable equivalent of Android's FeedScreen ranking comparator. */
@OptIn(ExperimentalTime::class)
fun calculateFeedRanking(posts: List<Post>, now: Instant = Clock.System.now(), timeZone: TimeZone = TimeZone.currentSystemDefault()): Map<String, Int> =
    posts.sortedWith(compareByDescending<Post> { it.likesCount }.thenByDescending { feedPublishedAt(it.createdAt, now, timeZone) })
        .mapIndexed { index, post -> post.id to index + 1 }.toMap()

@OptIn(ExperimentalTime::class)
fun feedPublishedAt(value: String, now: Instant, timeZone: TimeZone): LocalDateTime {
    val current = now.toLocalDateTime(timeZone)
    val text = value.trim()
    if (text.equals("ahora", true)) return current
    if (text.equals("ayer", true)) return (now - 1.days).toLocalDateTime(timeZone)
    Regex("^hace\\s+(\\d+)\\s*(min|minuto|minutos|h|hora|horas|d|día|dias|días|sem|semana|semanas)", RegexOption.IGNORE_CASE).find(text)?.let {
        val n = it.groupValues[1].toInt(); val unit = it.groupValues[2].lowercase()
        val duration = when { unit.startsWith("min") -> n.minutes; unit.startsWith("h") || unit.startsWith("hora") -> n.hours; unit.startsWith("sem") -> (n * 7).days; else -> n.days }
        return (now - duration).toLocalDateTime(timeZone)
    }
    return runCatching { Instant.parse(text).toLocalDateTime(timeZone) }
        .recoverCatching {
            Regex("(\\d{1,2})/(\\d{1,2})/(\\d{4}),?\\s+(\\d{1,2}):(\\d{2}):(\\d{2})").matchEntire(text)?.let { m ->
                LocalDateTime(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt())
            } ?: error("feed_date_invalid")
        }
        .recoverCatching { LocalDateTime.parse(text.replace(' ', 'T')) }
        .getOrDefault(current)
}
