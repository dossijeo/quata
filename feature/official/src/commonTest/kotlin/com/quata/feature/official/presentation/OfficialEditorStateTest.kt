package com.quata.feature.official.presentation

import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialReadMoreOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfficialEditorStateTest {
    @Test
    fun quickModeBuildsDraftFromRichTextBlocks() {
        val state = OfficialEditorDraftState(
            contentHtml = "<h1>Road works</h1><p>Main street closes tonight.</p>",
            linkUrl = "https://example.test/ignored",
            mediaType = OfficialMediaType.Image,
            mediaUrl = "file:///tmp/notice.jpg",
            postType = OfficialPostType.News,
        )

        val draft = state.buildDraft(defaultTitle = "Untitled", language = OfficialPostLanguage.English)

        assertTrue(state.canPublish())
        assertEquals("Road works", draft.title)
        assertEquals("Main street closes tonight.", draft.summary)
        assertEquals(OfficialReadMoreOption.ReadMore.shortcode, draft.readMoreLabel)
        assertNull(draft.linkUrl)
        assertEquals(OfficialMediaType.Image, draft.mediaType)
        assertEquals("file:///tmp/notice.jpg", draft.mediaUrl)
        assertEquals(OfficialPostType.News, draft.type)
    }

    @Test
    fun advancedModeRequiresTitleAndAllowsMediaOnlyBody() {
        val state = OfficialEditorDraftState(
            mode = OfficialEditorMode.Advanced,
            title = "Storm alert",
            summary = "",
            contentHtml = "",
            readMoreOption = OfficialReadMoreOption.Details,
            linkUrl = " https://quata.test/alert ",
            mediaType = OfficialMediaType.Video,
            mediaUrl = "file:///tmp/alert.mp4",
            postType = OfficialPostType.Urgent,
        )

        val draft = state.buildDraft(defaultTitle = "Untitled", language = OfficialPostLanguage.Spanish)

        assertTrue(state.canPublish())
        assertEquals("Storm alert", draft.title)
        assertEquals("", draft.summary)
        assertEquals(OfficialReadMoreOption.Details.shortcode, draft.readMoreLabel)
        assertEquals("https://quata.test/alert", draft.linkUrl)
        assertEquals(OfficialMediaType.Video, draft.mediaType)
        assertEquals(OfficialPostType.Urgent, draft.type)
    }

    @Test
    fun emptyQuickModeCannotPublishAndFallsBackToDefaultTitle() {
        val state = OfficialEditorDraftState(contentHtml = "   ")
        val draft = state.buildDraft(defaultTitle = "Untitled", language = OfficialPostLanguage.French)

        assertFalse(state.canPublish())
        assertEquals("Untitled", draft.title)
        assertEquals("", draft.summary)
        assertNull(draft.mediaUrl)
        assertNull(draft.mediaType)
    }

    @Test
    fun pendingTranslationsExcludeSourceLanguageAndStampDraft() {
        val draft = OfficialEditorDraftState(contentHtml = "<p>Bonjour</p>")
            .buildDraft(defaultTitle = "Untitled", language = OfficialPostLanguage.French)

        val pending = draft.pendingOfficialTranslations(OfficialPostLanguage.French)

        assertEquals(OfficialPostLanguage.French, pending.draft.language)
        assertEquals(OfficialPostLanguage.French, pending.sourceLanguage)
        assertEquals(
            listOf(OfficialPostLanguage.Spanish, OfficialPostLanguage.English),
            pending.targetLanguages,
        )
        assertFalse(pending.isTranslating)
    }
}
