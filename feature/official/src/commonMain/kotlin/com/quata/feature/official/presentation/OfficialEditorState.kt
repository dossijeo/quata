package com.quata.feature.official.presentation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialReadMoreOption

enum class OfficialEditorMode {
    Quick,
    Advanced,
}

data class OfficialEditorDraftState(
    val mode: OfficialEditorMode = OfficialEditorMode.Quick,
    val title: String = "",
    val summary: String = "",
    val contentHtml: String = "",
    val readMoreOption: OfficialReadMoreOption = OfficialReadMoreOption.ReadMore,
    val linkUrl: String = "",
    val mediaUrl: String = "",
    val mediaType: OfficialMediaType? = null,
    val postType: OfficialPostType = OfficialPostType.Announcement,
) {
    val contentPlain: String
        get() = contentHtml.stripHtmlForOfficialEditor()

    val quickTextBlocks: List<String>
        get() = contentHtml.extractOfficialEditorBlocks()

    val effectiveTitle: String
        get() = if (mode == OfficialEditorMode.Quick) quickTextBlocks.firstOrNull().orEmpty() else title.trim()

    val effectiveSummary: String
        get() = if (mode == OfficialEditorMode.Quick) {
            quickTextBlocks.drop(1).joinToString(" ").ellipsizeOfficialSummary(140)
        } else {
            summary.trim()
        }

    val effectiveReadMoreCode: String
        get() = if (mode == OfficialEditorMode.Quick) {
            OfficialReadMoreOption.ReadMore.shortcode
        } else {
            readMoreOption.shortcode
        }

    val effectiveLinkUrl: String
        get() = if (mode == OfficialEditorMode.Quick) "" else linkUrl.trim()

    fun canPublish(): Boolean =
        if (mode == OfficialEditorMode.Quick) {
            contentPlain.isNotBlank()
        } else {
            title.isNotBlank() && (
                summary.isNotBlank() ||
                    contentPlain.isNotBlank() ||
                    (mediaType != null && mediaUrl.isNotBlank())
                )
        }

    fun withMode(isAdvanced: Boolean): OfficialEditorDraftState =
        copy(mode = if (isAdvanced) OfficialEditorMode.Advanced else OfficialEditorMode.Quick)

    fun withMedia(type: OfficialMediaType, url: String): OfficialEditorDraftState =
        copy(mediaType = type, mediaUrl = url)

    fun withoutMedia(): OfficialEditorDraftState = copy(mediaType = null, mediaUrl = "")

    fun buildDraft(defaultTitle: String, language: OfficialPostLanguage): OfficialPostDraft =
        OfficialPostDraft(
            title = effectiveTitle.ifBlank { defaultTitle },
            summary = effectiveSummary,
            contentHtml = contentHtml,
            readMoreLabel = effectiveReadMoreCode,
            language = language,
            type = postType,
            mediaUrl = mediaUrl.takeIf { mediaType != null && it.isNotBlank() },
            mediaType = mediaType?.takeIf { mediaUrl.isNotBlank() },
            linkUrl = effectiveLinkUrl.takeIf { it.isNotBlank() },
            isLive = false,
        )
}

val OfficialEditorDraftStateSaver: Saver<OfficialEditorDraftState, Any> = listSaver(
    save = { state ->
        listOf(
            state.mode.name,
            state.title,
            state.summary,
            state.contentHtml,
            state.readMoreOption.name,
            state.linkUrl,
            state.mediaUrl,
            state.mediaType?.name.orEmpty(),
            state.postType.name,
        )
    },
    restore = { values ->
        OfficialEditorDraftState(
            mode = values.enumValueAt(0, OfficialEditorMode.Quick),
            title = values.stringValueAt(1),
            summary = values.stringValueAt(2),
            contentHtml = values.stringValueAt(3),
            readMoreOption = values.enumValueAt(4, OfficialReadMoreOption.ReadMore),
            linkUrl = values.stringValueAt(5),
            mediaUrl = values.stringValueAt(6),
            mediaType = values.stringValueAt(7)
                .takeIf(String::isNotBlank)
                ?.let { runCatching { OfficialMediaType.valueOf(it) }.getOrNull() },
            postType = values.enumValueAt(8, OfficialPostType.Announcement),
        )
    },
)

private fun List<Any?>.stringValueAt(index: Int): String = getOrNull(index) as? String ?: ""

private inline fun <reified T : Enum<T>> List<Any?>.enumValueAt(index: Int, fallback: T): T =
    stringValueAt(index).takeIf(String::isNotBlank)?.let {
        runCatching { enumValueOf<T>(it) }.getOrNull()
    } ?: fallback

data class OfficialPendingTranslation(
    val draft: OfficialPostDraft,
    val sourceLanguage: OfficialPostLanguage,
    val targetLanguages: List<OfficialPostLanguage>,
    val isTranslating: Boolean = false,
)

fun OfficialPostDraft.pendingOfficialTranslations(
    sourceLanguage: OfficialPostLanguage,
): OfficialPendingTranslation = OfficialPendingTranslation(
    draft = copy(language = sourceLanguage),
    sourceLanguage = sourceLanguage,
    targetLanguages = OfficialPostLanguage.entries.filterNot { it == sourceLanguage },
)
