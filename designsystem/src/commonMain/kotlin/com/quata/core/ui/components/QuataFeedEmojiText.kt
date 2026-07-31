package com.quata.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.painterResource
import quata.designsystem.generated.resources.Res
import quata.designsystem.generated.resources.quata_feed_emoji_document
import quata.designsystem.generated.resources.quata_feed_emoji_location
import quata.designsystem.generated.resources.quata_feed_emoji_note
import quata.designsystem.generated.resources.quata_feed_emoji_rank
import quata.designsystem.generated.resources.quata_feed_emoji_sos
import quata.designsystem.generated.resources.quata_header_logo_q_subset

/** Emoji glyphs intentionally shipped with the common feed UI, including their emoji variation. */
object QuataFeedEmoji {
    const val Sos = "\uD83D\uDEA8"
    const val Rank = "\uD83D\uDD25"
    const val Location = "\uD83D\uDCCD"
    const val Note = "\uD83D\uDCDD"
    const val Document = "\uD83D\uDCC4"

    internal val glyphs = listOf(Sos, Rank, Location, Note, Document)
    internal val inlineResourceNames = mapOf(
        Sos to "quata-feed-emoji-sos",
        Rank to "quata-feed-emoji-rank",
        Location to "quata-feed-emoji-location",
        Note to "quata-feed-emoji-note",
        Document to "quata-feed-emoji-document",
    )
}

/** Text and inline painters for the five fixed feed glyphs. */
class QuataFeedEmojiInlineText internal constructor(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
)

/** A catalog emoji and the atlas cell that paints it without platform font coverage. */
internal data class QuataCommunityEmojiInlineEntry(val emoji: String, val sectionKey: String, val index: Int) {
    val inlineId: String = "quata-community-emoji-$sectionKey-$index"
}

/** Text and inline content for the common picker catalog and the five fixed feed glyphs. */
class QuataEmojiInlineText internal constructor(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
)

private val communityEmojiInlineEntries: List<QuataCommunityEmojiInlineEntry> by lazy {
    communityEmojiSections().flatMap { section ->
        section.emojis.mapIndexed { index, emoji -> QuataCommunityEmojiInlineEntry(emoji, section.key, index) }
    }
}
private val communityEmojiInlineEntriesById: Map<String, QuataCommunityEmojiInlineEntry> by lazy {
    communityEmojiInlineEntries.associateBy { it.inlineId }
}

internal val emojiInlineEntriesByGlyph: List<Pair<String, String>> by lazy {
    (QuataFeedEmoji.glyphs.map { it to QuataFeedEmoji.inlineResourceNames.getValue(it) } +
        communityEmojiInlineEntries.map { it.emoji to it.inlineId })
        .distinctBy { it.first }
        .sortedByDescending { it.first.length }
}

private val emojiInlineEntriesByFirstChar: Map<Char, List<Pair<String, String>>> by lazy {
    emojiInlineEntriesByGlyph.groupBy { it.first.first() }
}

internal fun emojiInlineEntryAt(value: String, index: Int): Pair<String, String>? =
    emojiInlineEntriesByFirstChar[value.getOrNull(index)]?.firstOrNull { (emoji, _) -> value.startsWith(emoji, index) }

/** Presentation-only replacement: the Unicode source string and persisted data remain unchanged. */
@Composable
fun rememberQuataEmojiInlineText(value: String): QuataEmojiInlineText {
    val usedInlineIds = remember(value) { quataEmojiInlineIds(value) }
    val inlineContent = buildMap<String, InlineTextContent> {
        usedInlineIds.forEach { inlineId ->
            val fixedResource = QuataFeedEmoji.inlineResourceNames.values.firstOrNull { it == inlineId }
            if (fixedResource != null) put(inlineId, feedEmojiInlineContent(fixedResource))
            else communityEmojiInlineEntriesById[inlineId]?.let { put(inlineId, communityEmojiInlineContent(it)) }
        }
    }
    val text = remember(value) { quataEmojiAnnotatedString(value) }
    return remember(text, inlineContent) { QuataEmojiInlineText(text, inlineContent) }
}

/**
 * Replaces the five feed glyphs with transparent Noto PNG resources.
 *
 * Compose's iOS CPU renderer does not reliably paint COLR v1 glyphs. Inline common resources
 * render deterministically on Android, Wasm and iOS while every other character keeps the
 * caller's typography.
 */
@Composable
fun rememberQuataFeedEmojiInlineText(value: String): QuataFeedEmojiInlineText {
    val inlineContent = QuataFeedEmoji.inlineResourceNames.mapValues { (_, resourceName) ->
        InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
            Image(
                painter = painterResource(feedEmojiDrawable(resourceName)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
            )
        }
    }
    val text = remember(value) { quataFeedEmojiAnnotatedString(value) }
    return remember(text, inlineContent) { QuataFeedEmojiInlineText(text, inlineContent) }
}

@Composable
private fun feedEmojiInlineContent(resourceName: String): InlineTextContent =
    InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
        Image(
            painter = painterResource(feedEmojiDrawable(resourceName)),
            contentDescription = null,
            contentScale = ContentScale.Fit,
        )
    }

@Composable
private fun communityEmojiInlineContent(entry: QuataCommunityEmojiInlineEntry): InlineTextContent {
    val atlas = communityEmojiAtlas(entry.sectionKey)
    val image: ImageBitmap = imageResource(atlas.resource)
    return InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
        Canvas(Modifier) {
            val column = entry.index % atlas.columns
            val row = entry.index / atlas.columns
            drawImage(
                image = image,
                srcOffset = IntOffset(column * atlas.cellPx, row * atlas.cellPx),
                srcSize = IntSize(atlas.cellPx, atlas.cellPx),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )
        }
    }
}

internal fun quataFeedEmojiAnnotatedString(value: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < value.length) {
        val emoji = QuataFeedEmoji.glyphs.firstOrNull { value.startsWith(it, index) }
        if (emoji == null) {
            append(value[index++])
        } else {
            val end = index + emoji.length + if (value.getOrNull(index + emoji.length) == '\uFE0F') 1 else 0
            appendInlineContent(QuataFeedEmoji.inlineResourceNames.getValue(emoji), emoji)
            index = end
        }
    }
}

/** Longest-match parsing keeps ZWJ, variation-selector and regional-indicator sequences atomic. */
internal fun quataEmojiAnnotatedString(value: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < value.length) {
        val entry = emojiInlineEntryAt(value, index)
        if (entry == null) append(value[index++]) else {
            appendInlineContent(entry.second, entry.first)
            index += entry.first.length
        }
    }
}

private fun quataEmojiInlineIds(value: String): Set<String> = buildSet {
    var index = 0
    while (index < value.length) {
        val entry = emojiInlineEntryAt(value, index)
        if (entry == null) index++ else { add(entry.second); index += entry.first.length }
    }
}

internal fun quataEmojiInlineIdsInOrder(value: String): List<String> = buildList {
    var index = 0
    while (index < value.length) {
        val entry = emojiInlineEntryAt(value, index)
        if (entry == null) index++ else { add(entry.second); index += entry.first.length }
    }
}

private fun feedEmojiDrawable(resourceName: String): DrawableResource = when (resourceName) {
    "quata-feed-emoji-sos" -> Res.drawable.quata_feed_emoji_sos
    "quata-feed-emoji-rank" -> Res.drawable.quata_feed_emoji_rank
    "quata-feed-emoji-location" -> Res.drawable.quata_feed_emoji_location
    "quata-feed-emoji-note" -> Res.drawable.quata_feed_emoji_note
    "quata-feed-emoji-document" -> Res.drawable.quata_feed_emoji_document
    else -> error("Unknown Quata feed emoji resource: $resourceName")
}

internal fun quataCommunityEmojiEntry(inlineId: String): QuataCommunityEmojiInlineEntry? = communityEmojiInlineEntriesById[inlineId]
internal fun quataFeedEmojiResource(inlineId: String): DrawableResource? =
    QuataFeedEmoji.inlineResourceNames.values.firstOrNull { it == inlineId }?.let(::feedEmojiDrawable)

/** Deterministic fixed-glyph surface for renderers that do not invoke InlineTextContent children. */
@Composable
fun QuataFeedEmojiIcon(emoji: String, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val resourceName = QuataFeedEmoji.inlineResourceNames[emoji] ?: return
    val image: ImageBitmap = imageResource(feedEmojiDrawable(resourceName))
    Canvas(modifier.size(size)) {
        drawImage(image, dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()))
    }
}

/** The Q plus combining diaeresis used by the authenticated header's fixed logo mark. */
const val QuataHeaderLogoGlyph = "Q\u0308"

/** A font family restricted to the header logo mark; it must not become application typography. */
@Composable
fun quataHeaderLogoFontFamily(): FontFamily = FontFamily(Font(Res.font.quata_header_logo_q_subset))
