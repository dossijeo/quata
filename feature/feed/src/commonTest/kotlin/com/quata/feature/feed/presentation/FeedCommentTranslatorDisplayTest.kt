package com.quata.feature.feed.presentation

import com.quata.core.model.PostComment
import com.quata.core.designsystem.theme.QuataTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FeedCommentTranslatorDisplayTest {
    private val comment = PostComment(
        id = "42",
        authorName = "Gabrielo",
        message = "Mensaje original",
        timestamp = "2026-07-03T09:08:07Z",
        replyToAuthorName = "Ana",
        replyToMessage = "Mensaje citado",
    )

    @Test
    fun translatorPayloadMatchesTheVisibleCommentStructure() {
        assertEquals(
            "Gabrielo - 03/07/2026 09:08\nEn respuesta a Ana\nMensaje citado\nMensaje original",
            feedCommentTranslatorDisplayText(
                comment = comment,
                timestamp = formatCommentTimestamp(comment.timestamp, TimeZone.UTC),
                replyLabel = "En respuesta a Ana",
            ),
        )
    }

    @Test
    fun formatterKeepsTheAndroidVisibleCommentTimestampContract() {
        assertEquals("03/07/2026 09:08", formatCommentTimestamp("3/7/2026, 9:08:07", TimeZone.UTC))
        assertEquals("03/07/2026 07:08", formatCommentTimestamp("2026-07-03T09:08:07+02:00", TimeZone.UTC))
        assertEquals("", formatCommentTimestamp("  ", TimeZone.UTC))
        assertEquals("bad-date", formatCommentTimestamp("bad-date", TimeZone.UTC))
    }

    @Test
    fun defaultPlatformSlotKeepsTheTranslatorTriggerVisibleAfterClick() = runComposeUiTest {
        val slots = FeedScreenPlatformSlots(media = { _, _, _, _, _, _ -> })
        setContent {
            QuataTheme {
                slots.commentsTranslatorTrigger("Traductor Fang", Modifier)
            }
        }
        onNodeWithContentDescription("Traductor Fang")
            .assertContentDescriptionEquals("Traductor Fang")
            .performClick()
        onNodeWithContentDescription("Traductor Fang")
            .assertContentDescriptionEquals("Traductor Fang")
    }
}
