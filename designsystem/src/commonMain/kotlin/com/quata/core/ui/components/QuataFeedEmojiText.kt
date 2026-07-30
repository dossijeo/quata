package com.quata.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import quata.designsystem.generated.resources.Res
import quata.designsystem.generated.resources.quata_header_logo_q_subset
import quata.designsystem.generated.resources.quata_feed_emoji_subset
import org.jetbrains.compose.resources.Font

/** Emoji glyphs intentionally shipped with the common feed UI, including their emoji variation. */
object QuataFeedEmoji {
    const val Sos = "\uD83D\uDEA8"
    const val Rank = "\uD83D\uDD25"
    const val Location = "\uD83D\uDCCD"
    const val Note = "\uD83D\uDCDD"
    const val Document = "\uD83D\uDCC4"

    internal val glyphs = listOf(Sos, Rank, Location, Note, Document)
}

/**
 * Applies the bundled Noto Color Emoji subset only to Quata's feed emoji.
 * Other characters keep the caller's normal Compose typography on every target.
 */
@Composable
fun rememberQuataFeedEmojiAnnotatedString(text: String): AnnotatedString {
    val emojiFontFamily = FontFamily(Font(Res.font.quata_feed_emoji_subset))
    return remember(text, emojiFontFamily) {
        buildAnnotatedString {
            append(text)
            var index = 0
            while (index < text.length) {
                val emoji = QuataFeedEmoji.glyphs.firstOrNull { text.startsWith(it, index) }
                if (emoji == null) {
                    index++
                } else {
                    val end = index + emoji.length + if (text.getOrNull(index + emoji.length) == '\uFE0F') 1 else 0
                    addStyle(SpanStyle(fontFamily = emojiFontFamily), index, end)
                    index = end
                }
            }
        }
    }
}

/** The Q plus combining diaeresis used by the authenticated header's fixed logo mark. */
const val QuataHeaderLogoGlyph = "Q\u0308"

/** A font family restricted to the header logo mark; it must not become application typography. */
@Composable
fun quataHeaderLogoFontFamily(): FontFamily = FontFamily(Font(Res.font.quata_header_logo_q_subset))
