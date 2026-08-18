package com.quata.core.ui.components

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class QuataFeedEmojiTextContractTest {
    @Test
    fun commonGlyphContractsRetainTheFiveFeedCodePoints() {
        assertEquals("\uD83D\uDEA8", QuataFeedEmoji.Sos)
        assertEquals("\uD83D\uDD25", QuataFeedEmoji.Rank)
        assertEquals("\uD83D\uDCCD", QuataFeedEmoji.Location)
        assertEquals("\uD83D\uDCDD", QuataFeedEmoji.Note)
        assertEquals("\uD83D\uDCC4", QuataFeedEmoji.Document)
        assertEquals("Q\u0308", QuataHeaderLogoGlyph)
    }

    @Test
    fun everyFeedGlyphHasAStableInlineResourceKey() {
        assertEquals(
            setOf(
                "quata-feed-emoji-sos",
                "quata-feed-emoji-rank",
                "quata-feed-emoji-location",
                "quata-feed-emoji-note",
                "quata-feed-emoji-document",
            ),
            QuataFeedEmoji.inlineResourceNames.values.toSet(),
        )
        assertEquals(QuataFeedEmoji.glyphs.toSet(), QuataFeedEmoji.inlineResourceNames.keys)
    }

    @Test
    fun annotatedTextUsesInlineContentForEveryFeedGlyph() {
        val text = quataFeedEmojiAnnotatedString(QuataFeedEmoji.glyphs.joinToString(" "))
        assertEquals(
            QuataFeedEmoji.inlineResourceNames.values.toSet(),
            text.getStringAnnotations(0, text.length).map { it.item }.toSet(),
        )
    }

    @Test
    fun linkifiedEmojiTextKeepsUrlsClickableWhileReplacingEmojiWithInlineContent() {
        val text = quataEmojiLinkifiedAnnotatedString(
            "${QuataFeedEmoji.Rank} Mira www.quata.test/chat ${QuataFeedEmoji.Note}.",
            androidx.compose.ui.graphics.Color.Blue,
        )

        assertEquals(
            setOf("quata-feed-emoji-rank", "quata-feed-emoji-note"),
            text.getStringAnnotations(0, text.length)
                .filter { it.tag != QuataLinkAnnotationTag }
                .map { it.item }
                .toSet(),
        )
        assertContains(
            text.getStringAnnotations(QuataLinkAnnotationTag, 0, text.length).map { it.item },
            "https://www.quata.test/chat",
        )
        assertEquals("${QuataFeedEmoji.Rank} Mira www.quata.test/chat ${QuataFeedEmoji.Note}.", text.text)
    }
}
