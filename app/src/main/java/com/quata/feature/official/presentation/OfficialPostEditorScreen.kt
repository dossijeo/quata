package com.quata.feature.official.presentation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.language.QuataLanguageIdentifier
import com.quata.core.language.TextLanguageIdentifier
import com.quata.core.localization.QuataLanguageManager
import com.quata.core.model.User
import com.quata.core.text.decodeHtmlEntities
import com.quata.core.ui.components.QuataEditorScaffold
import com.quata.core.ui.components.QuataEditorToolButton
import com.quata.core.ui.richtext.QuataRichTextEditorBox
import com.quata.core.ui.richtext.QuataRichTextRenderer
import com.quata.core.translation.QuataDeepLLanguage
import com.quata.core.translation.QuataOfficialDeepLTranslator
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialReadMoreOption
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialRepository
import com.quata.feature.postcomposer.imageeditor.QuataImageEditorDialog
import com.quata.feature.postcomposer.videoeditor.QuataVideoEditorDialog
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun OfficialPostEditorRoute(
    padding: PaddingValues,
    repository: OfficialRepository,
    onPublished: (String?) -> Unit,
    onFullscreenEditorVisibilityChange: (Boolean) -> Unit = {},
    viewModel: OfficialFeedAndroidViewModel = viewModel(factory = OfficialFeedAndroidViewModel.factory(repository))
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.message) {
        if (state.message != null) {
            val createdPostId = state.createdPostId
            viewModel.onEvent(OfficialFeedUiEvent.ClearMessage)
            onPublished(createdPostId)
        }
    }

    OfficialPostEditorScreen(
        padding = padding,
        currentUser = state.currentUser,
        isPublishing = state.isPublishing,
        error = state.error,
        onSubmit = { drafts -> viewModel.onEvent(OfficialFeedUiEvent.CreatePosts(drafts)) },
        onFullscreenEditorVisibilityChange = onFullscreenEditorVisibilityChange
    )
}

@Composable
fun OfficialPostEditorScreen(
    padding: PaddingValues,
    currentUser: User?,
    isPublishing: Boolean,
    error: String?,
    onSubmit: (List<OfficialPostDraft>) -> Unit,
    onFullscreenEditorVisibilityChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var isLongEditorOpen by rememberSaveable { mutableStateOf(false) }
    var longEditorHtml by rememberSaveable { mutableStateOf("") }
    var longEditorTitle by rememberSaveable { mutableStateOf("") }
    var imageEditorUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var videoEditorUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingBodySave by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var pendingImagePicked by remember { mutableStateOf<((OfficialEditorMedia) -> Unit)?>(null) }
    var pendingVideoPicked by remember { mutableStateOf<((OfficialEditorMedia) -> Unit)?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        imageEditorUri = uri
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        videoEditorUri = uri
    }

    LaunchedEffect(imageEditorUri, videoEditorUri, isLongEditorOpen) {
        onFullscreenEditorVisibilityChange(imageEditorUri != null || videoEditorUri != null || isLongEditorOpen)
    }

    BackHandler(enabled = isLongEditorOpen) {
        isLongEditorOpen = false
    }

    if (isLongEditorOpen) {
        OfficialLongContentEditor(
            html = longEditorHtml,
            title = longEditorTitle,
            onHtmlChange = { longEditorHtml = it },
            onBack = { isLongEditorOpen = false },
            onSave = {
                pendingBodySave?.invoke(longEditorHtml)
                pendingBodySave = null
                isLongEditorOpen = false
            }
        )
    } else {
        val spanishName = stringResource(R.string.official_language_spanish)
        val englishName = stringResource(R.string.official_language_english)
        val frenchName = stringResource(R.string.official_language_french)
        val translationTemplate = stringResource(R.string.official_translation_message, "\u0000", "\u0001")
        fun languageName(language: OfficialPostLanguage): String = when (language) {
            OfficialPostLanguage.Spanish -> spanishName
            OfficialPostLanguage.English -> englishName
            OfficialPostLanguage.French -> frenchName
        }
        OfficialPostEditorRoot(
            padding = padding,
            currentUser = currentUser,
            isPublishing = isPublishing,
            error = error,
            language = currentOfficialPostLanguage(),
            canPublish = currentUser?.isOfficial == true,
            onSubmit = onSubmit,
            newTranslationGroupId = { UUID.randomUUID().toString() },
            detectLanguage = { draft ->
                detectOfficialPostLanguage(
                    identifier = TextLanguageIdentifier { text -> QuataLanguageIdentifier.detect(context, text) },
                    draft = draft,
                    fallback = currentOfficialPostLanguage(),
                )
            },
            translator = OfficialPostEditorTranslator { draft, source, target, groupId ->
                translateOfficialDraft(context, draft, source, target, groupId)
            },
            strings = OfficialPostEditorStrings(
                title = stringResource(R.string.official_create),
                quickModeTitle = stringResource(R.string.official_form_mode_quick),
                quickModeDescription = stringResource(R.string.official_form_mode_description_quick),
                advancedModeTitle = stringResource(R.string.official_form_mode_advanced),
                advancedModeDescription = stringResource(R.string.official_form_mode_description_advanced),
                mainSection = stringResource(R.string.official_form_main_section),
                mediaSection = stringResource(R.string.official_form_media_section),
                bodyQuick = stringResource(R.string.official_form_body_quick),
                readMoreSection = stringResource(R.string.official_form_read_more_section),
                editBodyQuick = stringResource(R.string.official_form_edit_body_quick),
                editBodyAdvanced = stringResource(R.string.official_form_edit_body),
                titleLabel = stringResource(R.string.official_form_title),
                summaryLabel = stringResource(R.string.official_form_summary),
                linkLabel = stringResource(R.string.official_form_link),
                preview = stringResource(R.string.composer_preview),
                publish = stringResource(R.string.official_publish),
                publishing = stringResource(R.string.composer_publishing),
                unavailable = stringResource(R.string.official_form_unavailable),
                validation = stringResource(R.string.official_form_validation),
                close = stringResource(R.string.common_close),
                defaultTitle = stringResource(R.string.official_post_default_title),
                translationTitle = stringResource(R.string.official_translation_title),
                translationMessage = { source, targets ->
                    translationTemplate
                        .replace("\u0000", languageName(source))
                        .replace("\u0001", targets.joinToString(", ") { languageName(it) })
                },
                translationProgress = stringResource(R.string.official_translation_progress),
                translationConfirm = stringResource(R.string.official_translation_confirm),
                translationSkip = stringResource(R.string.official_translation_skip),
                translationFailed = stringResource(R.string.error_backend_generic),
                typeLabel = { type -> context.getOfficialPostTypeLabel(type) },
                readMoreLabel = { shortcode -> context.getOfficialReadMoreLabel(shortcode) },
            ),
            slots = OfficialPostEditorPlatformSlots(
                bodyEditorAction = { html, title, onHtmlChange, editorModifier ->
                    OutlinedButton(
                        onClick = {
                            longEditorHtml = html
                            longEditorTitle = title
                            pendingBodySave = onHtmlChange
                            isLongEditorOpen = true
                        },
                        modifier = editorModifier,
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(title, fontWeight = FontWeight.ExtraBold)
                    }
                },
                imagePicker = { onPicked, pickerModifier ->
                    OutlinedButton(
                        onClick = {
                            pendingImagePicked = onPicked
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = pickerModifier,
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.composer_pick_image), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                videoPicker = { onPicked, pickerModifier ->
                    OutlinedButton(
                        onClick = {
                            pendingVideoPicked = onPicked
                            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        modifier = pickerModifier,
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.composer_pick_video), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                mediaPreview = { media, onPicked, onRemove, previewModifier ->
                    OfficialEditorMediaPreview(
                        mediaType = media.type,
                        mediaUrl = media.url,
                        onEdit = {
                            when (media.type) {
                                OfficialMediaType.Image -> {
                                    pendingImagePicked = onPicked
                                    imageEditorUri = Uri.parse(media.url)
                                }
                                OfficialMediaType.Video -> {
                                    pendingVideoPicked = onPicked
                                    videoEditorUri = Uri.parse(media.url)
                                }
                            }
                        },
                        onRemove = onRemove,
                        modifier = previewModifier,
                    )
                },
                preview = { preview, _ ->
                    OfficialPostPreview(
                        author = preview.author,
                        title = preview.title,
                        summary = preview.summary,
                        contentHtml = preview.contentHtml,
                        readMoreLabel = preview.readMoreLabel,
                        postType = preview.postType,
                        mediaUrl = preview.mediaUrl,
                        mediaType = preview.mediaType,
                        linkUrl = preview.linkUrl,
                    )
                },
            ),
        )
    }

    imageEditorUri?.let { sourceUri ->
        QuataImageEditorDialog(
            imageUri = sourceUri,
            onDismiss = { imageEditorUri = null },
            onEdited = { editedUri ->
                pendingImagePicked?.invoke(OfficialEditorMedia(editedUri.toString(), OfficialMediaType.Image))
                pendingImagePicked = null
                imageEditorUri = null
            }
        )
    }

    videoEditorUri?.let { sourceUri ->
        QuataVideoEditorDialog(
            videoUri = sourceUri,
            onDismiss = { videoEditorUri = null },
            onExported = { editedUri ->
                pendingVideoPicked?.invoke(OfficialEditorMedia(editedUri.toString(), OfficialMediaType.Video))
                pendingVideoPicked = null
                videoEditorUri = null
            }
        )
    }
}

@Composable
private fun OfficialTranslationPromptDialog(
    pending: OfficialPendingTranslation,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onGenerate: () -> Unit
) {
    val spanishName = stringResource(R.string.official_language_spanish)
    val englishName = stringResource(R.string.official_language_english)
    val frenchName = stringResource(R.string.official_language_french)
    fun languageName(language: OfficialPostLanguage): String = when (language) {
        OfficialPostLanguage.Spanish -> spanishName
        OfficialPostLanguage.English -> englishName
        OfficialPostLanguage.French -> frenchName
    }
    val targets = pending.targetLanguages.joinToString(", ") { languageName(it) }
    OfficialTranslationPromptContent(
        title = stringResource(R.string.official_translation_title),
        message = stringResource(
            R.string.official_translation_message,
            languageName(pending.sourceLanguage),
            targets,
        ),
        progressLabel = stringResource(R.string.official_translation_progress),
        confirmLabel = stringResource(R.string.official_translation_confirm),
        skipLabel = stringResource(R.string.official_translation_skip),
        isTranslating = pending.isTranslating,
        loader = { OfficialTranslationLoaderContent() },
        onDismiss = onDismiss,
        onSkip = onSkip,
        onGenerate = onGenerate,
    )
}

@Composable
private fun OfficialLongContentEditor(
    html: String,
    title: String,
    onHtmlChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    OfficialLongTextEditorContent(
        title = title,
        onBack = onBack,
        backContentDescription = stringResource(R.string.video_editor_back),
        saveLabel = stringResource(R.string.common_save_changes),
        onSave = onSave,
        saveIcon = { Icon(Icons.Filled.Save, contentDescription = null) },
        editorContent = { editorModifier ->
            QuataRichTextEditorBox(
                initialHtml = html,
                placeholder = title,
                onHtmlChange = onHtmlChange,
                modifier = editorModifier,
            )
        },
    )
}

@Composable
private fun OfficialEditorCard(
    content: @Composable ColumnScope.() -> Unit
) {
    OfficialEditorSectionCardContent(content = content)
}

@Composable
private fun OfficialEditorSectionTitle(text: String) {
    OfficialEditorSectionTitleContent(text)
}

@Composable
private fun OfficialEditorMediaPreview(
    mediaType: OfficialMediaType,
    mediaUrl: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OfficialEditorMediaPreviewContent(
        removeLabel = stringResource(R.string.common_remove),
        onRemove = onRemove,
        modifier = modifier,
        mediaContent = { mediaModifier ->
            Box(
                modifier = mediaModifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (mediaType == OfficialMediaType.Image) {
                    AsyncImage(
                        model = mediaUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0xFF111827), Color(0xFF030712)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.38f),
                            contentColor = Color.White,
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        editAction = { editModifier ->
            OutlinedButton(onClick = onEdit, modifier = editModifier) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(
                        if (mediaType == OfficialMediaType.Image) {
                            R.string.composer_edit_image
                        } else {
                            R.string.video_editor_edit_video
                        }
                    ),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        },
    )
}

@Composable
private fun OfficialPostPreview(
    author: User?,
    title: String,
    summary: String,
    contentHtml: String,
    readMoreLabel: String,
    postType: OfficialPostType,
    mediaUrl: String,
    mediaType: OfficialMediaType?,
    linkUrl: String
) {
    val authorName = author?.displayName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.official_account_fallback)
    val authorSubtitle = author?.neighborhood?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.official_account_fallback)
    val safeTitle = title.ifBlank { stringResource(R.string.official_post_default_title) }
    val safeSummary = summary.ifBlank { stringResource(R.string.official_form_summary) }
    val longTextPlain = contentHtml.stripHtmlForOfficialEditor().ifBlank { safeSummary }
    val safeContentHtml = contentHtml.takeIf { it.stripHtmlForOfficialEditor().isNotBlank() }
        ?: "<p>${safeSummary.escapePreviewHtml()}</p>"
    val previewPost = OfficialPostItem(
        id = "official_preview",
        author = (author ?: User(
            id = "official_preview_author",
            email = "",
            displayName = authorName,
            neighborhood = authorSubtitle,
            isOfficial = true
        )).copy(
            displayName = authorName,
            neighborhood = authorSubtitle,
            isOfficial = true
        ),
        title = safeTitle,
        summary = safeSummary,
        contentHtml = safeContentHtml,
        contentPlain = longTextPlain,
        readMoreLabel = readMoreLabel,
        type = postType,
        mediaUrl = mediaUrl.takeIf { mediaType != null && it.isNotBlank() },
        mediaType = mediaType?.takeIf { mediaUrl.isNotBlank() },
        linkUrl = linkUrl.takeIf { it.isNotBlank() },
        isLive = false,
        createdAt = stringResource(R.string.common_now),
        likesCount = 0,
        commentsCount = 0
    )
    val previewMedia: (@Composable (Modifier) -> Unit)? = if (previewPost.mediaUrl.isNullOrBlank()) {
        null
    } else {
        { mediaModifier -> OfficialPostMedia(previewPost, onOpenMedia = {}, modifier = mediaModifier) }
    }
    OfficialEditorPostPreviewContent(
        post = previewPost,
        typeLabel = postType.remoteValue.uppercase(),
        readMoreLabel = localizedOfficialReadMoreLabel(readMoreLabel),
        closeLabel = stringResource(R.string.common_close),
        author = { authorModifier ->
            OfficialAuthorHeaderContent(
                displayName = previewPost.author.displayName,
                neighborhood = previewPost.author.neighborhood,
                fallbackNeighborhood = stringResource(R.string.official_account_fallback),
                avatar = {},
                modifier = authorModifier,
            )
        },
        media = previewMedia,
        articleContent = { selectedPost, articleModifier ->
            QuataRichTextRenderer(
                html = selectedPost.contentHtml,
                modifier = articleModifier,
                placeholder = selectedPost.contentPlain,
            )
        },
    )
}

@Composable
private fun OfficialPublishButton(
    enabled: Boolean,
    isPublishing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OfficialPublishButtonContent(
        enabled = enabled,
        isPublishing = isPublishing,
        publishLabel = stringResource(R.string.official_publish),
        publishingLabel = stringResource(R.string.composer_publishing),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun OfficialPostType.editorLabel(): String = when (this) {
    OfficialPostType.Announcement -> stringResource(R.string.official_type_announcement)
    OfficialPostType.News -> stringResource(R.string.official_type_news)
    OfficialPostType.Event -> stringResource(R.string.official_type_event)
    OfficialPostType.Urgent -> stringResource(R.string.official_type_urgent)
}

private fun android.content.Context.getOfficialPostTypeLabel(type: OfficialPostType): String = getString(
    when (type) {
        OfficialPostType.Announcement -> R.string.official_type_announcement
        OfficialPostType.News -> R.string.official_type_news
        OfficialPostType.Event -> R.string.official_type_event
        OfficialPostType.Urgent -> R.string.official_type_urgent
    },
)

private fun android.content.Context.getOfficialReadMoreLabel(storedValue: String): String {
    val option = OfficialReadMoreOption.fromStored(storedValue) ?: OfficialReadMoreOption.ReadMore
    val labelRes = officialReadMoreUiOptions.firstOrNull { it.option == option }?.labelRes
        ?: R.string.official_read_more
    return getString(labelRes)
}

@Composable
private fun OfficialPostLanguage.localizedName(): String = stringResource(
    when (this) {
        OfficialPostLanguage.Spanish -> R.string.official_language_spanish
        OfficialPostLanguage.English -> R.string.official_language_english
        OfficialPostLanguage.French -> R.string.official_language_french
    }
)

private fun currentOfficialPostLanguage(): OfficialPostLanguage =
    OfficialPostLanguage.fromAppLanguage(QuataLanguageManager.currentLanguage.tag)

private suspend fun buildTranslatedOfficialDrafts(
    context: android.content.Context,
    pending: OfficialPendingTranslation
): List<OfficialPostDraft> {
    val groupId = UUID.randomUUID().toString()
    val sourceDraft = pending.draft.copy(
        language = pending.sourceLanguage,
        translationGroupId = groupId
    )
    val translations = pending.targetLanguages.map { target ->
        translateOfficialDraft(
            context = context,
            draft = sourceDraft,
            source = pending.sourceLanguage,
            target = target,
            groupId = groupId
        )
    }
    return listOf(sourceDraft) + translations
}

private suspend fun translateOfficialDraft(
    context: android.content.Context,
    draft: OfficialPostDraft,
    source: OfficialPostLanguage,
    target: OfficialPostLanguage,
    groupId: String
): OfficialPostDraft {
    val sourceLanguage = source.toDeepLLanguage()
    val targetLanguage = target.toDeepLLanguage()
    return draft.copy(
        title = translateOfficialText(context, draft.title, sourceLanguage, targetLanguage),
        summary = translateOfficialText(context, draft.summary, sourceLanguage, targetLanguage),
        contentHtml = translateOfficialHtml(context, draft.contentHtml, sourceLanguage, targetLanguage),
        language = target,
        translationGroupId = groupId
    )
}

private suspend fun translateOfficialHtml(
    context: android.content.Context,
    html: String,
    source: QuataDeepLLanguage,
    target: QuataDeepLLanguage
): String {
    val matches = officialHtmlBlockRegex.findAll(html).toList()
    if (matches.isEmpty()) {
        val translated = translateOfficialText(
            context = context,
            text = html.stripHtmlForOfficialEditor(),
            source = source,
            target = target
        )
        return "<p>${translated.escapePreviewHtml()}</p>"
    }

    val translated = StringBuilder()
    var cursor = 0
    matches.forEach { match ->
        translated.append(html.substring(cursor, match.range.first))
        val tag = match.groupValues[1]
        val attributes = match.groupValues.getOrNull(2).orEmpty()
        val inner = match.groupValues.getOrNull(3).orEmpty().stripHtmlForOfficialEditor()
        val translatedInner = translateOfficialText(
            context = context,
            text = inner,
            source = source,
            target = target
        )
        translated.append('<')
            .append(tag)
            .append(attributes)
            .append('>')
            .append(translatedInner.escapePreviewHtml())
            .append("</")
            .append(tag)
            .append('>')
        cursor = match.range.last + 1
    }
    translated.append(html.substring(cursor))
    return translated.toString()
}

private suspend fun translateOfficialText(
    context: android.content.Context,
    text: String,
    source: QuataDeepLLanguage,
    target: QuataDeepLLanguage
): String {
    val normalized = text.trim()
    if (normalized.isBlank()) return ""
    if (source == target) return normalized
    return QuataOfficialDeepLTranslator.shared
        .translateText(normalized, source, target)
        .ifBlank { normalized }
}

private fun OfficialPostLanguage.toDeepLLanguage(): QuataDeepLLanguage = when (this) {
    OfficialPostLanguage.Spanish -> QuataDeepLLanguage.Spanish
    OfficialPostLanguage.English -> QuataDeepLLanguage.English
    OfficialPostLanguage.French -> QuataDeepLLanguage.French
}
