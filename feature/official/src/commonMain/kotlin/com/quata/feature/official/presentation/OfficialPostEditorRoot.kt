package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.quata.core.language.QuataDetectedLanguage
import com.quata.core.language.TextLanguageIdentifier
import com.quata.core.model.User
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialReadMoreOption
import kotlinx.coroutines.launch

const val OfficialEditorRootTestTag = "official-editor-common-root"
const val OfficialEditorModeSelectorTestTag = "official-editor-mode-selector"
const val OfficialEditorMainSectionTestTag = "official-editor-main-section"
const val OfficialEditorMediaSectionTestTag = "official-editor-media-section"
const val OfficialEditorImagePickerTestTag = "official-editor-pick-image"
const val OfficialEditorVideoPickerTestTag = "official-editor-pick-video"
const val OfficialEditorBodySectionTestTag = "official-editor-body-section"
const val OfficialEditorBodyActionTestTag = "official-editor-body-action"
const val OfficialEditorMediaPreviewTestTag = "official-editor-media-preview"
const val OfficialEditorPreviewTestTag = "official-editor-preview"
const val OfficialEditorFeedbackTestTag = "official-editor-feedback"
const val OfficialEditorPublishTestTag = "official-editor-publish"

class OfficialPostEditorE2eActions(
    val setAdvancedMode: () -> Unit,
    val setTitle: (String) -> Unit,
    val setSummary: (String) -> Unit,
    val setBodyHtml: (String) -> Unit,
    val publish: () -> Unit,
    val skipTranslation: () -> Boolean,
    val state: () -> String,
)

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
            title = "Créer une publication officielle",
            quickModeTitle = "Mode rapide",
            quickModeDescription = "Écris le texte complet ; Quata extrait le titre et le résumé.",
            advancedModeTitle = "Mode avancé",
            advancedModeDescription = "Contrôle le titre, le résumé, le texte du lien et l'URL d'information.",
            mainSection = "Détails de la publication",
            mediaSection = "Photo ou vidéo",
            bodyQuick = "Description",
            readMoreSection = "Lire plus",
            editBodyQuick = "Modifier la description",
            editBodyAdvanced = "Modifier la description longue",
            titleLabel = "Titre",
            summaryLabel = "Résumé court",
            linkLabel = "Lien d'information",
            preview = "Aperçu",
            publish = "Publier",
            publishing = "Publication...",
            unavailable = "La publication officielle n'est pas encore disponible sur cette plateforme.",
            validation = "Ajoute du texte ou un média avant de publier.",
            close = feedStrings.close,
            defaultTitle = "Compte officiel",
            translationTitle = "Générer des traductions ?",
            translationMessage = { source, targets ->
                "${source.editorLanguageName("fr").replaceFirstChar { it.uppercase() }} détecté. Générer aussi ${targets.joinToString(", ") { it.editorLanguageName("fr") }} ?"
            },
            translationProgress = "Génération des traductions...",
            translationConfirm = "Générer",
            translationSkip = "Publier seulement cette langue",
            translationFailed = "Impossible de générer les traductions",
            typeLabel = feedStrings::typeLabel,
            readMoreLabel = feedStrings::readMoreLabel,
        )
        else -> OfficialPostEditorStrings(
            title = "Crear publicación oficial",
            quickModeTitle = "Modo rápido",
            quickModeDescription = "Escribe el texto completo; Quata extraerá el título y el resumen.",
            advancedModeTitle = "Modo avanzado",
            advancedModeDescription = "Controla título, resumen, texto del enlace y URL informativa.",
            mainSection = "Datos de la publicación",
            mediaSection = "Foto o vídeo",
            bodyQuick = "Descripción",
            readMoreSection = "Leer más",
            editBodyQuick = "Editar descripción",
            editBodyAdvanced = "Editar descripción larga",
            titleLabel = "Título",
            summaryLabel = "Resumen corto",
            linkLabel = "Enlace informativo",
            preview = "Vista previa",
            publish = "Publicar",
            publishing = "Publicando...",
            unavailable = "La publicación oficial aún no está disponible en esta plataforma.",
            validation = "Añade texto o contenido multimedia antes de publicar.",
            close = feedStrings.close,
            defaultTitle = "Cuenta oficial",
            translationTitle = "¿Generar traducciones?",
            translationMessage = { source, targets ->
                "Se ha detectado ${source.editorLanguageName("es")}. ¿Generar también ${targets.joinToString(", ") { it.editorLanguageName("es") }}?"
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

data class OfficialPostEditorLanguageDetection(
    val publicationLanguage: OfficialPostLanguage,
    val translationSourceLanguage: OfficialPostLanguage?,
)

fun fallbackOfficialPostEditorLanguageDetection(
    language: OfficialPostLanguage,
): OfficialPostEditorLanguageDetection = OfficialPostEditorLanguageDetection(
    publicationLanguage = language,
    translationSourceLanguage = null,
)

fun officialPostEditorDetectionText(draft: OfficialPostDraft): String = buildString {
    appendLine(draft.title)
    appendLine(draft.summary)
    append(draft.contentHtml.stripHtmlForOfficialEditor())
}.trim()

suspend fun detectOfficialPostLanguage(
    identifier: TextLanguageIdentifier,
    draft: OfficialPostDraft,
    fallback: OfficialPostLanguage,
): OfficialPostEditorLanguageDetection {
    val language = when (identifier.detect(officialPostEditorDetectionText(draft)).language) {
        QuataDetectedLanguage.Spanish -> OfficialPostLanguage.Spanish
        QuataDetectedLanguage.English -> OfficialPostLanguage.English
        QuataDetectedLanguage.French -> OfficialPostLanguage.French
        QuataDetectedLanguage.Fang,
        QuataDetectedLanguage.Unknown -> null
    }
    return OfficialPostEditorLanguageDetection(
        publicationLanguage = language ?: fallback,
        translationSourceLanguage = language,
    )
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
    detectLanguage: suspend (OfficialPostDraft) -> OfficialPostEditorLanguageDetection = {
        OfficialPostEditorLanguageDetection(
            publicationLanguage = language,
            translationSourceLanguage = language,
        )
    },
    translator: OfficialPostEditorTranslator? = null,
    newTranslationGroupId: () -> String,
    e2eBridgeInstaller: ((OfficialPostEditorE2eActions) -> (() -> Unit))? = null,
) {
    var draftState by rememberSaveable(stateSaver = OfficialEditorDraftStateSaver) {
        mutableStateOf(OfficialEditorDraftState())
    }
    var readMoreMenuOpen by rememberSaveable { mutableStateOf(false) }
    var typeMenuOpen by rememberSaveable { mutableStateOf(false) }
    var pendingTranslation by remember { mutableStateOf<OfficialPendingTranslation?>(null) }
    var localFeedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun requestPublication() {
        if (!canPublish) {
            localFeedback = strings.unavailable
            return
        }
        if (!draftState.canPublish()) {
            localFeedback = strings.validation
            return
        }
        localFeedback = null
        val draft = draftState.buildDraft(defaultTitle = strings.defaultTitle, language = language)
        scope.launch {
            val detection = runCatching { detectLanguage(draft) }
                .getOrDefault(fallbackOfficialPostEditorLanguageDetection(language))
            val sourceLanguage = detection.translationSourceLanguage
            if (translator == null || sourceLanguage == null) {
                onSubmit(listOf(draft.copy(language = detection.publicationLanguage)))
            } else {
                pendingTranslation = draft.pendingOfficialTranslations(sourceLanguage)
            }
        }
    }

    fun skipPendingTranslation(): Boolean {
        val pending = pendingTranslation ?: return false
        val groupId = newTranslationGroupId()
        onSubmit(listOf(pending.draft.copy(translationGroupId = groupId)))
        pendingTranslation = null
        return true
    }

    val latestE2ePublish by rememberUpdatedState(newValue = { requestPublication() })
    val latestE2eSkipTranslation by rememberUpdatedState(newValue = { skipPendingTranslation() })
    val latestE2eState by rememberUpdatedState(
        newValue = {
            officialEditorE2eStateJson(
                canPublish = canPublish,
                isPublishing = isPublishing,
                bodyLength = draftState.contentHtml.length,
                title = draftState.title,
                summary = draftState.summary,
                feedback = error ?: localFeedback ?: if (!canPublish) strings.unavailable else "",
                pendingTranslation = pendingTranslation != null,
            )
        },
    )

    DisposableEffect(e2eBridgeInstaller) {
        val uninstall = e2eBridgeInstaller?.invoke(
            OfficialPostEditorE2eActions(
                setAdvancedMode = { draftState = draftState.withMode(true) },
                setTitle = { value -> draftState = draftState.copy(title = value) },
                setSummary = { value -> draftState = draftState.copy(summary = value) },
                setBodyHtml = { value -> draftState = draftState.copy(contentHtml = value) },
                publish = { latestE2ePublish() },
                skipTranslation = { latestE2eSkipTranslation() },
                state = { latestE2eState() },
            ),
        )
        onDispose { uninstall?.invoke() }
    }

    OfficialEditorScreenContent(
        padding = padding,
        title = strings.title,
        modifier = modifier.testTag(OfficialEditorRootTestTag),
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
                    modifier = Modifier.testTag(OfficialEditorModeSelectorTestTag),
                )
            },
            mainSection = {
                OfficialEditorSectionCardContent(modifier = Modifier.testTag(OfficialEditorMainSectionTestTag)) {
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
                            pickerModifier.testTag(OfficialEditorImagePickerTestTag),
                        )
                    },
                    videoPicker = { pickerModifier ->
                        slots.videoPicker(
                            { media -> draftState = draftState.withMedia(media.type, media.url) },
                            pickerModifier.testTag(OfficialEditorVideoPickerTestTag),
                        )
                    },
                    modifier = Modifier.testTag(OfficialEditorMediaSectionTestTag),
                    preview = {
                        val selectedMediaType = draftState.mediaType
                        if (draftState.mediaUrl.isNotBlank() && selectedMediaType != null) {
                            slots.mediaPreview(
                                OfficialEditorMedia(draftState.mediaUrl, selectedMediaType),
                                { media -> draftState = draftState.withMedia(media.type, media.url) },
                                { draftState = draftState.withoutMedia() },
                                Modifier.fillMaxWidth().testTag(OfficialEditorMediaPreviewTestTag),
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
                            Modifier.fillMaxWidth().testTag(OfficialEditorBodyActionTestTag),
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
                    modifier = Modifier.testTag(OfficialEditorBodySectionTestTag),
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
                    Modifier.fillMaxWidth().testTag(OfficialEditorPreviewTestTag),
                )
            },
            feedback = {
                val message = error ?: localFeedback ?: if (!canPublish) strings.unavailable else null
                if (message != null) {
                    Text(
                        message,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag(OfficialEditorFeedbackTestTag),
                    )
                }
            },
            publishAction = {
                OfficialPublishButtonContent(
                    enabled = true,
                    isPublishing = isPublishing,
                    publishLabel = strings.publish,
                    publishingLabel = strings.publishing,
                    onClick = {
                        focusManager.clearFocus(force = true)
                        requestPublication()
                    },
                    modifier = Modifier.fillMaxWidth().testTag(OfficialEditorPublishTestTag),
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
                skipPendingTranslation()
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

private fun officialEditorE2eStateJson(
    canPublish: Boolean,
    isPublishing: Boolean,
    bodyLength: Int,
    title: String,
    summary: String,
    feedback: String,
    pendingTranslation: Boolean,
): String = buildString {
    append('{')
    append("\"canPublish\":").append(canPublish).append(',')
    append("\"isPublishing\":").append(isPublishing).append(',')
    append("\"bodyLength\":").append(bodyLength).append(',')
    append("\"title\":").append(title.officialEditorE2eJsonString()).append(',')
    append("\"summary\":").append(summary.officialEditorE2eJsonString()).append(',')
    append("\"feedback\":").append(feedback.officialEditorE2eJsonString()).append(',')
    append("\"pendingTranslation\":").append(pendingTranslation)
    append('}')
}

private fun String.officialEditorE2eJsonString(): String = buildString {
    append('"')
    for (char in this@officialEditorE2eJsonString) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
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
