package com.quata.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.Font
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

private fun feedEmojiDrawable(resourceName: String): DrawableResource = when (resourceName) {
    "quata-feed-emoji-sos" -> Res.drawable.quata_feed_emoji_sos
    "quata-feed-emoji-rank" -> Res.drawable.quata_feed_emoji_rank
    "quata-feed-emoji-location" -> Res.drawable.quata_feed_emoji_location
    "quata-feed-emoji-note" -> Res.drawable.quata_feed_emoji_note
    "quata-feed-emoji-document" -> Res.drawable.quata_feed_emoji_document
    else -> error("Unknown Quata feed emoji resource: $resourceName")
}

/** The Q plus combining diaeresis used by the authenticated header's fixed logo mark. */
const val QuataHeaderLogoGlyph = "Q\u0308"

/** A font family restricted to the header logo mark; it must not become application typography. */
@Composable
fun quataHeaderLogoFontFamily(): FontFamily = FontFamily(Font(Res.font.quata_header_logo_q_subset))
