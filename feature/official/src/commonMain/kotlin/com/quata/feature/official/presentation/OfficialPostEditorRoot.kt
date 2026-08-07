package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.quata.core.model.User
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialReadMoreOption
import kotlinx.coroutines.launch

data class OfficialEditorMedia(
    val url: String,
    val type: OfficialMediaType,
)

data class OfficialPostEditorPreviewState(
    val author: User?,
    val title: String,
    val summary: String,
    val contentHtml: String,
    val readMoreLabel: String,
    val postType: OfficialPostType,
    val mediaUrl: String,
    val mediaType: OfficialMediaType?,
    val linkUrl: String,
)

class OfficialPostEditorStrings(
    val title: String,
    val quickModeTitle: String,
    val quickModeDescription: String,
    val advancedModeTitle: String,
    val advancedModeDescription: String,
    val mainSection: String,
    val mediaSection: String,
    val bodyQuick: String,
    val readMoreSection: String,
    val editBodyQuick: String,
    val editBodyAdvanced: String,
    val titleLabel: String,
    val summaryLabel: String,
    val linkLabel: String,
    val preview: String,
    val publish: String,
    val publishing: String,
    val unavailable: String,
    val validation: String,
    val close: String,
    val defaultTitle: String,
    val translationTitle: String,
    val translationMessage: (OfficialPostLanguage, List<OfficialPostLanguage>) -> String,
    val translationProgress: String,
    val translationConfirm: String,
    val translationSkip: String,
    val translationFailed: String,
    val typeLabel: (OfficialPostType) -> String,
    val readMoreLabel: (String) -> String,
)

fun defaultOfficialPostEditorStrings(languageTag: String?): OfficialPostEditorStrings {
    val feedStrings = defaultOfficialFeedScreenStrings(languageTag)
    return when (languageTag?.substringBefore('-')?.lowercase()) {
        "en" -> OfficialPostEditorStrings(
            title = "Create official post",
            quickModeTitle = "Quick mode",
            quickModeDescription = "Write the full text; Quata will extract the title and summary.",
            advancedModeTitle = "Advanced mode",
            advancedModeDescription = "Control title, summary, link text and information URL.",
            mainSection = "Publication details",
            mediaSection = "Photo or video",
            bodyQuick = "Description",
            readMoreSection = "Read more",
            editBodyQuick = "Edit description",
            editBodyAdvanced = "Edit long description",
            titleLabel = "Title",
            summaryLabel = "Short summary",
            linkLabel = "Information link",
            preview = "Preview",
            publish = "Publish",
            publishing = "Publishing...",
            unavailable = "Official publishing is not available on this platform yet.",
            validation = "Add text or media before publishing.",
            close = feedStrings.close,
            defaultTitle = "Official account",
            translationTitle = "Generate translations?",
            translationMessage = { source, targets ->
                "Detected ${source.editorLanguageName("en")}. Generate ${targets.joinToString(", ") { it.editorLanguageName("en") }} too?"
            },
            translationProgress = "Generating translations...",
            translationConfirm = "Generate",
            translationSkip = "Publish only this language",
            translationFailed = "Could not generate translations",
            typeLabel = feedStrings::typeLabel,
            readMoreLabel = feedStrings::readMoreLabel,
        )
        "fr" -> OfficialPostEditorStrings(
            title = "Creer une publication officielle",
            quickModeTitle = "Mode rapide",
            quickModeDescription = "Ecris le texte complet ; Quata extrait le titre et le resume.",
            advancedModeTitle = "Mode avance",
            advancedModeDescription = "Controle le titre, le resume, le texte du lien et l URL d'information.",
            mainSection = "Details de la publication",
            mediaSection = "Photo ou video",
            bodyQuick = "Description",
            readMoreSection = "Lire plus",
            editBodyQuick = "Modifier la description",
            editBodyAdvanced = "Modifier la description longue",
            titleLabel = "Titre",
            summaryLabel = "Resume court",
            linkLabel = "Lien d'information",
            preview = "Apercu",
            publish = "Publier",
            publishing = "Publication...",
            unavailable = "La publication officielle n'est pas encore disponible sur cette plateforme.",
            validation = "Ajoute du texte ou un media avant de publier.",
            close = feedStrings.close,
            defaultTitle = "Compte officiel",
            translationTitle = "Generer des traductions ?",
            translationMessage = { source, targets ->
                "${source.editorLanguageName("fr").replaceFirstChar { it.uppercase() }} detecte. Generer aussi ${targets.joinToString(", ") { it.editorLanguageName("fr") }} ?"
            },
            translationProgress = "Generation des traductions...",
            translationConfirm = "Generer",
            translationSkip = "Publier seulement cette langue",
            translationFailed = "Impossible de generer les traductions",
            typeLabel = feedStrings::typeLabel,
            readMoreLabel = feedStrings::readMoreLabel,
        )
        else -> OfficialPostEditorStrings(
            title = "Crear publicacion oficial",
            quickModeTitle = "Modo rapido",
            quickModeDescription = "Escribe el texto completo; Quata extraera el titulo y el resumen.",
            advancedModeTitle = "Modo avanzado",
            advancedModeDescription = "Controla titulo, resumen, texto del enlace y URL informativa.",
            mainSection = "Datos de la publicacion",
            mediaSection = "Foto o video",
            bodyQuick = "Descripcion",
            readMoreSection = "Leer mas",
            editBodyQuick = "Editar descripcion",
            editBodyAdvanced = "Editar descripcion larga",
            titleLabel = "Titulo",
            summaryLabel = "Resumen corto",
            linkLabel = "Enlace informativo",
            preview = "Vista previa",
            publish = "Publicar",
            publishing = "Publicando...",
            unavailable = "La publicacion oficial aun no esta disponible en esta plataforma.",
            validation = "Anade texto o contenido multimedia antes de publicar.",
            close = feedStrings.close,
            defaultTitle = "Cuenta oficial",
            translationTitle = "Generar traducciones?",
            translationMessage = { source, targets ->
                "Se ha detectado ${source.editorLanguageName("es")}. Generar tambien ${targets.joinToString(", ") { it.editorLanguageName("es") }}?"
            },
            translationProgress = "Generando traducciones...",
            translationConfirm = "Generar",
            translationSkip = "Publicar solo este idioma",
            translationFailed = "No se pudieron generar las traducciones",
            typeLabel = feedStrings::typeLabel,
            readMoreLabel = feedStrings::readMoreLabel,
        )
    }
}

private fun OfficialPostLanguage.editorLanguageName(language: String): String = when (language) {
    "en" -> when (this) {
        OfficialPostLanguage.Spanish -> "Spanish"
        OfficialPostLanguage.English -> "English"
        OfficialPostLanguage.French -> "French"
    }
    "fr" -> when (this) {
        OfficialPostLanguage.Spanish -> "espagnol"
        OfficialPostLanguage.English -> "anglais"
        OfficialPostLanguage.French -> "francais"
    }
    else -> when (this) {
        OfficialPostLanguage.Spanish -> "espanol"
        OfficialPostLanguage.English -> "ingles"
        OfficialPostLanguage.French -> "frances"
    }
}

class OfficialPostEditorPlatformSlots(
    val bodyEditorAction: @Composable (
        html: String,
        title: String,
        onHtmlChange: (String) -> Unit,
        Modifier,
    ) -> Unit,
    val imagePicker: @Composable (onPicked: (OfficialEditorMedia) -> Unit, Modifier) -> Unit,
    val videoPicker: @Composable (onPicked: (OfficialEditorMedia) -> Unit, Modifier) -> Unit,
    val mediaPreview: @Composable (
        media: OfficialEditorMedia,
        onPicked: (OfficialEditorMedia) -> Unit,
        onRemove: () -> Unit,
        Modifier,
    ) -> Unit,
    val preview: @Composable (OfficialPostEditorPreviewState, Modifier) -> Unit,
    val translationLoader: @Composable () -> Unit = { OfficialTranslationLoaderContent() },
)

fun interface OfficialPostEditorTranslator {
    suspend fun translate(
        draft: OfficialPostDraft,
        source: OfficialPostLanguage,
        target: OfficialPostLanguage,
        groupId: String,
    ): OfficialPostDraft
}

@Composable
fun OfficialPostEditorRoot(
    padding: PaddingValues,
    currentUser: User?,
    isPublishing: Boolean,
    error: String?,
    strings: OfficialPostEditorStrings,
    slots: OfficialPostEditorPlatformSlots,
    language: OfficialPostLanguage,
    canPublish: Boolean,
    onSubmit: (List<OfficialPostDraft>) -> Unit,
    modifier: Modifier = Modifier,
    detectLanguage: suspend (OfficialPostDraft) -> OfficialPostLanguage = { language },
    translator: OfficialPostEditorTranslator? = null,
    newTranslationGroupId: () -> String,
) {
    var draftState by rememberSaveable(stateSaver = OfficialEditorDraftStateSaver) {
        mutableStateOf(OfficialEditorDraftState())
    }
    var readMoreMenuOpen by rememberSaveable { mutableStateOf(false) }
    var typeMenuOpen by rememberSaveable { mutableStateOf(false) }
    var pendingTranslation by remember { mutableStateOf<OfficialPendingTranslation?>(null) }
    var localFeedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun canSubmitDraft(): Boolean = canPublish && draftState.canPublish()

    fun requestPublication() {
        if (!canPublish) {
            localFeedback = strings.unavailable
            return
        }
        if (!draftState.canPublish()) {
            localFeedback = strings.validation
            return
        }
        val draft = draftState.buildDraft(defaultTitle = strings.defaultTitle, language = language)
        scope.launch {
            val sourceLanguage = runCatching { detectLanguage(draft) }.getOrDefault(language)
            if (translator == null) {
                onSubmit(listOf(draft.copy(language = sourceLanguage)))
            } else {
                pendingTranslation = draft.pendingOfficialTranslations(sourceLanguage)
            }
        }
    }

    OfficialEditorScreenContent(
        padding = padding,
        title = strings.title,
        modifier = modifier,
    ) {
        OfficialEditorFormContent(
            modeSelector = {
                OfficialEditorModeSelectorContent(
                    title = if (draftState.mode == OfficialEditorMode.Quick) {
                        strings.quickModeTitle
                    } else {
                        strings.advancedModeTitle
                    },
                    description = if (draftState.mode == OfficialEditorMode.Quick) {
                        strings.quickModeDescription
                    } else {
                        strings.advancedModeDescription
                    },
                    isAdvanced = draftState.mode == OfficialEditorMode.Advanced,
                    onAdvancedChange = { draftState = draftState.withMode(it) },
                )
            },
            mainSection = {
                OfficialEditorSectionCardContent {
                    OfficialEditorSectionTitleContent(strings.mainSection)
                    OfficialEditorDropdownFieldContent(
                        selectedLabel = strings.typeLabel(draftState.postType),
                        options = OfficialPostType.entries.map { type ->
                            OfficialEditorSelectionOption(type.name, strings.typeLabel(type))
                        },
                        expanded = typeMenuOpen,
                        onExpandedChange = { typeMenuOpen = it },
                        onOptionSelected = { selectedId ->
                            draftState = draftState.copy(
                                postType = OfficialPostType.entries.first { it.name == selectedId },
                            )
                        },
                    )
                    if (draftState.mode == OfficialEditorMode.Advanced) {
                        OfficialAdvancedTextFieldsContent(
                            title = draftState.title,
                            summary = draftState.summary,
                            titleLabel = strings.titleLabel,
                            summaryLabel = strings.summaryLabel,
                            onTitleChange = { draftState = draftState.copy(title = it) },
                            onSummaryChange = { draftState = draftState.copy(summary = it) },
                        )
                    }
                }
            },
            mediaSection = {
                OfficialEditorMediaSectionContent(
                    title = strings.mediaSection,
                    imagePicker = { pickerModifier ->
                        slots.imagePicker(
                            { media -> draftState = draftState.withMedia(media.type, media.url) },
                            pickerModifier,
                        )
                    },
                    videoPicker = { pickerModifier ->
                        slots.videoPicker(
                            { media -> draftState = draftState.withMedia(media.type, media.url) },
                            pickerModifier,
                        )
                    },
                    preview = {
                        val selectedMediaType = draftState.mediaType
                        if (draftState.mediaUrl.isNotBlank() && selectedMediaType != null) {
                            slots.mediaPreview(
                                OfficialEditorMedia(draftState.mediaUrl, selectedMediaType),
                                { media -> draftState = draftState.withMedia(media.type, media.url) },
                                { draftState = draftState.withoutMedia() },
                                Modifier.fillMaxWidth(),
                            )
                        }
                    },
                )
            },
            bodySection = {
                OfficialEditorBodySectionContent(
                    title = if (draftState.mode == OfficialEditorMode.Quick) {
                        strings.bodyQuick
                    } else {
                        strings.readMoreSection
                    },
                    readMoreControl = if (draftState.mode == OfficialEditorMode.Advanced) {
                        {
                            OfficialEditorDropdownFieldContent(
                                selectedLabel = strings.readMoreLabel(draftState.readMoreOption.shortcode),
                                options = OfficialReadMoreOption.entries.map { option ->
                                    OfficialEditorSelectionOption(option.name, strings.readMoreLabel(option.shortcode))
                                },
                                expanded = readMoreMenuOpen,
                                onExpandedChange = { readMoreMenuOpen = it },
                                onOptionSelected = { selectedId ->
                                    draftState = draftState.copy(
                                        readMoreOption = OfficialReadMoreOption.entries.first { it.name == selectedId },
                                    )
                                },
                            )
                        }
                    } else {
                        null
                    },
                    editorAction = {
                        slots.bodyEditorAction(
                            draftState.contentHtml,
                            if (draftState.mode == OfficialEditorMode.Quick) {
                                strings.editBodyQuick
                            } else {
                                strings.editBodyAdvanced
                            },
                            { draftState = draftState.copy(contentHtml = it) },
                            Modifier.fillMaxWidth(),
                        )
                    },
                    linkControl = if (draftState.mode == OfficialEditorMode.Advanced) {
                        {
                            OfficialEditorLinkFieldContent(
                                value = draftState.linkUrl,
                                onValueChange = { draftState = draftState.copy(linkUrl = it) },
                                label = strings.linkLabel,
                            )
                        }
                    } else {
                        null
                    },
                )
            },
            previewSection = {
                OfficialEditorSectionTitleContent(strings.preview)
                slots.preview(
                    OfficialPostEditorPreviewState(
                        author = currentUser,
                        title = draftState.effectiveTitle,
                        summary = draftState.effectiveSummary,
                        contentHtml = draftState.contentHtml,
                        readMoreLabel = draftState.effectiveReadMoreCode,
                        postType = draftState.postType,
                        mediaUrl = draftState.mediaUrl,
                        mediaType = draftState.mediaType,
                        linkUrl = draftState.effectiveLinkUrl,
                    ),
                    Modifier.fillMaxWidth(),
                )
            },
            feedback = {
                val message = error ?: localFeedback ?: if (!canPublish) strings.unavailable else null
                if (message != null) {
                    Text(message, fontWeight = FontWeight.Bold)
                }
            },
            publishAction = {
                OfficialPublishButtonContent(
                    enabled = canSubmitDraft(),
                    isPublishing = isPublishing,
                    publishLabel = strings.publish,
                    publishingLabel = strings.publishing,
                    onClick = { requestPublication() },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }

    pendingTranslation?.let { pending ->
        OfficialTranslationPromptContent(
            title = strings.translationTitle,
            message = strings.translationMessage(pending.sourceLanguage, pending.targetLanguages),
            progressLabel = strings.translationProgress,
            confirmLabel = strings.translationConfirm,
            skipLabel = strings.translationSkip,
            isTranslating = pending.isTranslating,
            loader = slots.translationLoader,
            onDismiss = {
                if (!pending.isTranslating) pendingTranslation = null
            },
            onSkip = {
                val groupId = newTranslationGroupId()
                onSubmit(listOf(pending.draft.copy(translationGroupId = groupId)))
                pendingTranslation = null
            },
            onGenerate = {
                val service = translator ?: return@OfficialTranslationPromptContent
                pendingTranslation = pending.copy(isTranslating = true)
                scope.launch {
                    val groupId = newTranslationGroupId()
                    val sourceDraft = pending.draft.copy(translationGroupId = groupId)
                    val translatedDrafts = runCatching {
                        pending.targetLanguages.map { target ->
                            service.translate(sourceDraft, pending.sourceLanguage, target, groupId)
                        }
                    }.getOrElse { failure ->
                        localFeedback = failure.message
                            ?.takeIf(String::isNotBlank)
                            ?.let { "${strings.translationFailed}: $it" }
                            ?: strings.translationFailed
                        pendingTranslation = pending.copy(isTranslating = false)
                        return@launch
                    }
                    onSubmit(listOf(sourceDraft) + translatedDrafts)
                    pendingTranslation = null
                }
            },
        )
    }
}

fun officialPostEditorPreviewItem(
    state: OfficialPostEditorPreviewState,
    fallbackAuthorLabel: String,
    defaultTitle: String,
    summaryFallback: String,
    createdAt: String,
): OfficialPostItem {
    val authorName = state.author?.displayName?.takeIf { it.isNotBlank() } ?: fallbackAuthorLabel
    val authorSubtitle = state.author?.neighborhood?.takeIf { it.isNotBlank() } ?: fallbackAuthorLabel
    val safeTitle = state.title.ifBlank { defaultTitle }
    val safeSummary = state.summary.ifBlank { summaryFallback }
    val longTextPlain = state.contentHtml.stripHtmlForOfficialEditor().ifBlank { safeSummary }
    val safeContentHtml = state.contentHtml.takeIf { it.stripHtmlForOfficialEditor().isNotBlank() }
        ?: "<p>${safeSummary.escapePreviewHtml()}</p>"
    return OfficialPostItem(
        id = "official_preview",
        author = (state.author ?: User(
            id = "official_preview_author",
            email = "",
            displayName = authorName,
            neighborhood = authorSubtitle,
            isOfficial = true,
        )).copy(
            displayName = authorName,
            neighborhood = authorSubtitle,
            isOfficial = true,
        ),
        title = safeTitle,
        summary = safeSummary,
        contentHtml = safeContentHtml,
        contentPlain = longTextPlain,
        readMoreLabel = state.readMoreLabel,
        type = state.postType,
        mediaUrl = state.mediaUrl.takeIf { state.mediaType != null && it.isNotBlank() },
        mediaType = state.mediaType?.takeIf { state.mediaUrl.isNotBlank() },
        linkUrl = state.linkUrl.takeIf { it.isNotBlank() },
        isLive = false,
        createdAt = createdAt,
        likesCount = 0,
        commentsCount = 0,
    )
}
