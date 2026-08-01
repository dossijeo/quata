package com.quata.feature.official.presentation

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
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialReadMoreOption
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable

/**
 * A real capability supplied by a target.  A missing capability is represented by null, never
 * by an empty callback: the root then omits the associated control.
 */
class OfficialEditorPlatformSlots(
    val richTextEditor: @Composable (html: String, onHtmlChanged: (String) -> Unit) -> Unit,
    val imagePicker: (@Composable (onPicked: (OfficialEditorMedia) -> Unit, Modifier) -> Unit)? = null,
    val videoPicker: (@Composable (onPicked: (OfficialEditorMedia) -> Unit, Modifier) -> Unit)? = null,
    val mediaPreview: (@Composable (OfficialEditorMedia, onRemove: () -> Unit, Modifier) -> Unit)? = null,
    /** Releases a platform-owned prepared selection. It is deliberately absent on browser slots. */
    val discardMedia: (suspend (OfficialEditorMedia) -> Unit)? = null,
    val postPreview: (@Composable (OfficialPostDraft, Modifier) -> Unit)? = null,
)

/** A local media selection. [url] must be uploadable by the platform submitter. */
data class OfficialEditorMedia(
    val url: String,
    val type: OfficialMediaType,
    /** Platform-private token; common UI never dereferences it or carries media bytes. */
    val preparedHandle: String? = null,
    val displayName: String? = null,
    val mimeType: String? = null,
)

fun interface OfficialDraftTranslator {
    suspend fun translate(draft: OfficialPostDraft, target: OfficialPostLanguage): Result<OfficialPostDraft>
}

/** Kept pure so all targets can enforce the same publication gate. */
internal fun isOfficialEditorDraftValid(advanced: Boolean, title: String, summary: String, html: String, hasMedia: Boolean): Boolean =
    if (advanced) title.isNotBlank() && (summary.isNotBlank() || html.stripHtmlForOfficialEditor().isNotBlank() || hasMedia)
    else html.stripHtmlForOfficialEditor().isNotBlank()

class OfficialPostEditorStrings(
    val title: String,
    val modeTitle: String,
    val modeDescription: String,
    val main: String,
    val postType: String,
    val media: String,
    val image: String,
    val video: String,
    val body: String,
    val titleLabel: String,
    val summaryLabel: String,
    val linkLabel: String,
    val preview: String,
    val publish: String,
    val publishing: String,
    val translateTitle: String,
    val translateMessage: String,
    val translating: String,
    val generate: String,
    val skip: String,
    val validation: String,
    val success: String,
    val back: String,
) {
    companion object {
        fun forLanguage(languageTag: String?): OfficialPostEditorStrings = when (languageTag?.substringBefore('-')?.lowercase()) {
            "en" -> OfficialPostEditorStrings("Create notice", "Advanced editor", "Add title, summary and link", "Details", "Post type", "Media", "Image", "Video", "Body", "Title", "Summary", "Link", "Preview", "Publish", "Publishing…", "Create translations", "Also create English and French versions?", "Translating…", "Generate", "Only this language", "Add a title and body before publishing.", "Notice published", "Back")
            "fr" -> OfficialPostEditorStrings("Créer un communiqué", "Éditeur avancé", "Ajoutez un titre, un résumé et un lien", "Détails", "Type de communiqué", "Média", "Image", "Vidéo", "Contenu", "Titre", "Résumé", "Lien", "Aperçu", "Publier", "Publication…", "Créer les traductions", "Créer aussi les versions anglaise et française ?", "Traduction…", "Générer", "Cette langue seulement", "Ajoutez un titre et du contenu avant de publier.", "Communiqué publié", "Retour")
            else -> OfficialPostEditorStrings("Crear comunicado", "Editor avanzado", "Añade título, resumen y enlace", "Detalles", "Tipo de comunicado", "Multimedia", "Imagen", "Vídeo", "Contenido", "Título", "Resumen", "Enlace", "Vista previa", "Publicar", "Publicando…", "Crear traducciones", "¿Crear también las versiones en inglés y francés?", "Traduciendo…", "Generar", "Solo este idioma", "Añade título y contenido antes de publicar.", "Comunicado publicado", "Volver")
        }
    }
}

/**
 * State-owning common editor used by Android, Wasm and iOS hosts. Submission is transactional
 * from the root's point of view: it resets only after the host confirms a successful insert.
 */
@Composable
fun OfficialPostEditorScreenHost(
    padding: PaddingValues,
    language: OfficialPostLanguage,
    strings: OfficialPostEditorStrings,
    slots: OfficialEditorPlatformSlots,
    onSubmit: suspend (List<OfficialPostDraft>) -> Result<String?>,
    onPublished: (String?) -> Unit,
    onBack: () -> Unit,
    newTranslationGroupId: () -> String,
    translator: OfficialDraftTranslator? = null,
    modifier: Modifier = Modifier,
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf(OfficialPostType.Announcement) }
    var title by rememberSaveable { mutableStateOf("") }
    var summary by rememberSaveable { mutableStateOf("") }
    var html by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var readMore by rememberSaveable { mutableStateOf(OfficialReadMoreOption.ReadMore) }
    var media by remember { mutableStateOf<OfficialEditorMedia?>(null) }
    var typeExpanded by remember { mutableStateOf(false) }
    var readMoreExpanded by remember { mutableStateOf(false) }
    var translationPrompt by remember { mutableStateOf(false) }
    var translating by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun draft(target: OfficialPostLanguage = language, groupId: String? = null): OfficialPostDraft {
        val blocks = html.extractOfficialEditorBlocks()
        val effectiveTitle = if (advanced) title.trim() else blocks.firstOrNull().orEmpty().take(120)
        val effectiveSummary = if (advanced) summary.trim() else blocks.drop(1).joinToString(" ").take(280)
        return OfficialPostDraft(
        title = effectiveTitle, summary = effectiveSummary, contentHtml = html,
        readMoreLabel = if (advanced) readMore.shortcode else OfficialReadMoreOption.ReadMore.shortcode,
        language = target, translationGroupId = groupId, type = type,
        mediaUrl = media?.preparedHandle ?: media?.url, mediaType = media?.type, linkUrl = if (advanced) link.trim().ifBlank { null } else null,
    ) }
    fun valid() = isOfficialEditorDraftValid(advanced, title, summary, html, media != null)
    fun reset() {
        advanced = false; type = OfficialPostType.Announcement; title = ""; summary = ""; html = ""; link = ""
        media?.let { selected -> slots.discardMedia?.let { discard -> scope.launch { discard(selected) } } }
        readMore = OfficialReadMoreOption.ReadMore; media = null; feedback = strings.success
    }
    fun publish(drafts: List<OfficialPostDraft>) = scope.launch {
        publishing = true
        onSubmit(drafts).onSuccess { id -> reset(); onPublished(id) }.onFailure { feedback = it.message ?: "Publication failed" }
        publishing = false
    }

    OfficialEditorScreenContent(padding = padding, title = strings.title, modifier = modifier) {
        OfficialEditorFormContent(
            modeSelector = { OfficialEditorModeSelectorContent(title = strings.modeTitle, description = strings.modeDescription, isAdvanced = advanced, onAdvancedChange = { advanced = it }) },
            mainSection = {
                OfficialEditorSectionCardContent {
                    OfficialEditorSectionTitleContent(strings.main)
                    OfficialEditorDropdownFieldContent(
                        selectedLabel = type.remoteValue, options = OfficialPostType.entries.map { OfficialEditorSelectionOption(it.name, it.remoteValue) },
                        expanded = typeExpanded, onExpandedChange = { typeExpanded = it }, onOptionSelected = { type = OfficialPostType.valueOf(it) },
                    )
                    if (advanced) OfficialAdvancedTextFieldsContent(title = title, summary = summary, titleLabel = strings.titleLabel, summaryLabel = strings.summaryLabel, onTitleChange = { title = it }, onSummaryChange = { summary = it })
                }
            },
            mediaSection = {
                if (slots.imagePicker != null || slots.videoPicker != null) OfficialEditorMediaSectionContent(
                    title = strings.media,
                    imagePicker = slots.imagePicker?.let { picker -> { modifier -> picker({ picked -> media?.takeIf { it != picked }?.let { old -> slots.discardMedia?.let { discard -> scope.launch { discard(old) } } }; media = picked }, modifier) } },
                    videoPicker = slots.videoPicker?.let { picker -> { modifier -> picker({ picked -> media?.takeIf { it != picked }?.let { old -> slots.discardMedia?.let { discard -> scope.launch { discard(old) } } }; media = picked }, modifier) } },
                    preview = media?.let { selected -> slots.mediaPreview?.let { preview -> { preview(selected, { slots.discardMedia?.let { discard -> scope.launch { discard(selected) } }; media = null }, Modifier.fillMaxWidth()) } } },
                )
            },
            bodySection = {
                OfficialEditorBodySectionContent(
                    title = strings.body,
                    readMoreControl = if (advanced) {{ OfficialEditorDropdownFieldContent(readMore.shortcode, OfficialReadMoreOption.entries.map { OfficialEditorSelectionOption(it.name, it.shortcode) }, readMoreExpanded, { readMoreExpanded = it }, { readMore = OfficialReadMoreOption.valueOf(it) }) }} else null,
                    editorAction = {
                        if (advanced) slots.richTextEditor(html) { html = it }
                        else slots.richTextEditor(html) { html = it }
                    },
                    linkControl = if (advanced) {{ OfficialEditorLinkFieldContent(value = link, label = strings.linkLabel, onValueChange = { link = it }) }} else null,
                )
            },
            previewSection = {
                slots.postPreview?.let { preview ->
                    OfficialEditorSectionTitleContent(strings.preview)
                    preview(draft(), Modifier.fillMaxWidth())
                }
            },
            feedback = { feedback?.let { Text(it) } },
            publishAction = {
                OfficialPublishButtonContent(enabled = valid() && !publishing, isPublishing = publishing, publishLabel = strings.publish, publishingLabel = strings.publishing, onClick = {
                    if (!valid()) feedback = strings.validation
                    else if (translator != null) translationPrompt = true
                    else publish(listOf(draft()))
                })
            },
        )
        androidx.compose.material3.TextButton(onClick = {
            media?.let { selected ->
                slots.discardMedia?.let { discard ->
                    scope.launch(NonCancellable, start = CoroutineStart.UNDISPATCHED) { discard(selected) }
                }
            }
            media = null
            onBack()
        }) { Text(strings.back) }
    }
    if (translationPrompt) OfficialTranslationPromptContent(
        title = strings.translateTitle, message = strings.translateMessage, progressLabel = strings.translating,
        confirmLabel = strings.generate, skipLabel = strings.skip, isTranslating = translating,
        loader = { OfficialTranslationLoaderContent() }, onDismiss = { if (!translating) translationPrompt = false },
        onSkip = { translationPrompt = false; publish(listOf(draft())) },
        onGenerate = {
            val service = translator ?: return@OfficialTranslationPromptContent
            scope.launch {
                translating = true
                val group = newTranslationGroupId()
                val source = draft(groupId = group)
                val translations = OfficialPostLanguage.entries.filter { it != language }.map { target -> service.translate(source, target).getOrElse { feedback = it.message ?: "Translation failed"; translating = false; return@launch } }
                translating = false; translationPrompt = false; publish(listOf(source) + translations.map { it.copy(translationGroupId = group) })
            }
        },
    )
}
