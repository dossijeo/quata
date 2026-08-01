package com.quata.feature.official.presentation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.quata.R
import com.quata.core.language.QuataDetectedLanguage
import com.quata.core.language.QuataLanguageIdentifier
import com.quata.core.localization.QuataLanguageManager
import com.quata.core.model.User
import com.quata.core.ui.richtext.QuataRichTextEditorBox
import com.quata.core.ui.richtext.QuataRichTextRenderer
import com.quata.core.translation.QuataDeepLLanguage
import com.quata.core.translation.QuataOfficialDeepLTranslator
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialRepository
import com.quata.feature.postcomposer.imageeditor.QuataImageEditorDialog
import com.quata.feature.postcomposer.videoeditor.QuataVideoEditorDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
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
    val context = LocalContext.current
    if (state.currentUser?.isOfficial != true && state.currentUser?.isAdmin != true) {
        Text("Official authorisation is required")
        return
    }
    BackHandler { onPublished(null) }
    OfficialPostEditorScreenHost(
        padding = padding,
        language = currentOfficialPostLanguage(),
        strings = OfficialPostEditorStrings.forLanguage(QuataLanguageManager.currentLanguage.tag),
        slots = rememberAndroidOfficialEditorPlatformSlots(state.currentUser, onFullscreenEditorVisibilityChange),
        onSubmit = { drafts -> repository.createPosts(drafts).map { it?.id } },
        onPublished = onPublished,
        onBack = { onPublished(null) },
        newTranslationGroupId = { UUID.randomUUID().toString() },
        translator = OfficialDraftTranslator { draft, target ->
            runCatching { translateOfficialDraft(context, draft, draft.language, target, draft.translationGroupId.orEmpty()) }
        },
        languageDetector = OfficialLanguageDetector { text ->
            runCatching {
                when (QuataLanguageIdentifier.detect(context, text).language) {
                    QuataDetectedLanguage.English -> OfficialPostLanguage.English
                    QuataDetectedLanguage.French -> OfficialPostLanguage.French
                    else -> OfficialPostLanguage.Spanish
                }
            }
        },
    )
}

/** Android-only bindings. The common host owns the editor state and transaction flow. */
@Composable
internal fun rememberAndroidOfficialEditorPlatformSlots(
    currentUser: User?,
    onFullscreenEditorVisibilityChange: (Boolean) -> Unit,
): OfficialEditorPlatformSlots {
    val context = LocalContext.current
    var longEditor by remember { mutableStateOf<AndroidLongEditor?>(null) }
    var mediaEdit by remember { mutableStateOf<AndroidMediaEdit?>(null) }
    var pendingPickedMedia by remember { mutableStateOf<AndroidPickedMedia?>(null) }
    var imagePicked by remember { mutableStateOf<((OfficialEditorMedia) -> Unit)?>(null) }
    var videoPicked by remember { mutableStateOf<((OfficialEditorMedia) -> Unit)?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { selected -> imagePicked?.let { callback -> pendingPickedMedia = AndroidPickedMedia(OfficialEditorMedia(selected.toString(), OfficialMediaType.Image, displayName = selected.lastPathSegment), callback) } }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { selected -> videoPicked?.let { callback -> pendingPickedMedia = AndroidPickedMedia(OfficialEditorMedia(selected.toString(), OfficialMediaType.Video, displayName = selected.lastPathSegment), callback) } }
    }
    LaunchedEffect(longEditor, mediaEdit, pendingPickedMedia) {
        onFullscreenEditorVisibilityChange(longEditor != null || mediaEdit != null || pendingPickedMedia != null)
    }
    DisposableEffect(Unit) {
        onDispose { onFullscreenEditorVisibilityChange(false) }
    }
    BackHandler(enabled = longEditor != null || mediaEdit != null || pendingPickedMedia != null) {
        when {
            longEditor != null -> { longEditor?.onFullscreenChanged(false); longEditor = null }
            mediaEdit != null -> { mediaEdit?.let { it.completion.complete(it.media) }; mediaEdit = null }
            else -> pendingPickedMedia = null
        }
    }
    val slots = remember(currentUser) {
        OfficialEditorPlatformSlots(
            richTextEditor = OfficialRichBodyEditor(
                content = { html, onHtmlChanged, onFullscreenChanged ->
                    OutlinedButton(onClick = { onFullscreenChanged(true); longEditor = AndroidLongEditor(html, onHtmlChanged, onFullscreenChanged) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text(context.getString(R.string.official_form_edit_body), fontWeight = FontWeight.ExtraBold)
                    }
                    longEditor?.let { editor -> OfficialLongContentEditor(editor.html, context.getString(R.string.official_form_body), { editor.html = it }, { editor.onFullscreenChanged(false); longEditor = null }, { editor.onHtmlChanged(editor.html); editor.onFullscreenChanged(false); longEditor = null }) }
                },
                cancel = { longEditor?.onFullscreenChanged(false); longEditor = null; Result.success(Unit) },
            ),
            imagePicker = { onPicked, modifier -> OutlinedButton(onClick = { imagePicked = onPicked; imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = modifier) { Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text(context.getString(R.string.composer_pick_image)) } },
            videoPicker = { onPicked, modifier -> OutlinedButton(onClick = { videoPicked = onPicked; videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, modifier = modifier) { Icon(Icons.Filled.VideoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text(context.getString(R.string.composer_pick_video)) } },
            mediaPreview = { media, onRemove, onEdit, modifier ->
                OfficialEditorMediaPreview(media.type, media.url, onEdit, onRemove)
            },
            mediaEditor = OfficialEditorCapability.Available(OfficialMediaEditExporter(OfficialMediaType.entries.toSet(), { media ->
                val completion = CompletableDeferred<OfficialEditorMedia>(); mediaEdit = AndroidMediaEdit(media, completion); runCatching { completion.await() }
            }, { mediaEdit?.let { it.completion.complete(it.media) }; mediaEdit = null; Result.success(Unit) })),
            cardPreview = OfficialEditorCapability.Available(OfficialCardPreview { draft, _ -> OfficialPostPreview(currentUser, draft.title, draft.summary, draft.contentHtml, draft.readMoreLabel, draft.type, draft.mediaUrl.orEmpty(), draft.mediaType, draft.linkUrl.orEmpty()) }),
        )
    }
    mediaEdit?.let { edit ->
        when (edit.media.type) {
            OfficialMediaType.Image -> QuataImageEditorDialog(Uri.parse(edit.media.url), { edit.completion.complete(edit.media); mediaEdit = null }, { uri -> edit.completion.complete(edit.media.copy(url = uri.toString())); mediaEdit = null })
            OfficialMediaType.Video -> QuataVideoEditorDialog(Uri.parse(edit.media.url), { edit.completion.complete(edit.media); mediaEdit = null }, { uri -> edit.completion.complete(edit.media.copy(url = uri.toString())); mediaEdit = null })
        }
    }
    pendingPickedMedia?.let { picked ->
        when (picked.media.type) {
            OfficialMediaType.Image -> QuataImageEditorDialog(Uri.parse(picked.media.url), { pendingPickedMedia = null }, { uri -> picked.onPicked(picked.media.copy(url = uri.toString())); pendingPickedMedia = null })
            OfficialMediaType.Video -> QuataVideoEditorDialog(Uri.parse(picked.media.url), { pendingPickedMedia = null }, { uri -> picked.onPicked(picked.media.copy(url = uri.toString())); pendingPickedMedia = null })
        }
    }
    return slots
}

private class AndroidLongEditor(var html: String, val onHtmlChanged: (String) -> Unit, val onFullscreenChanged: (Boolean) -> Unit)
private class AndroidMediaEdit(val media: OfficialEditorMedia, val completion: CompletableDeferred<OfficialEditorMedia>)
private class AndroidPickedMedia(val media: OfficialEditorMedia, val onPicked: (OfficialEditorMedia) -> Unit)

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
private fun OfficialEditorMediaPreview(
    mediaType: OfficialMediaType,
    mediaUrl: String,
    onEdit: (() -> Unit)?,
    onRemove: () -> Unit
) {
    OfficialEditorMediaPreviewContent(
        removeLabel = stringResource(R.string.common_remove),
        onRemove = onRemove,
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
        editAction = onEdit?.let { edit ->
            { editModifier ->
                OutlinedButton(onClick = edit, modifier = editModifier) {
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
        { mediaModifier ->
            if (previewPost.mediaType == OfficialMediaType.Image) {
                AsyncImage(
                    model = previewPost.mediaUrl,
                    contentDescription = previewPost.title,
                    contentScale = ContentScale.Crop,
                    modifier = mediaModifier,
                )
            } else {
                com.quata.core.ui.components.VideoAttachmentThumbnail(
                    uri = previewPost.mediaUrl.orEmpty(),
                    name = previewPost.title,
                    showPlayButton = true,
                    modifier = mediaModifier,
                )
            }
        }
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
        actionRail = { _, _ -> },
        overflowAction = null,
        articleContent = { selectedPost, articleModifier ->
            QuataRichTextRenderer(
                html = selectedPost.contentHtml,
                modifier = articleModifier,
                placeholder = selectedPost.contentPlain,
            )
        },
    )
}

private fun currentOfficialPostLanguage(): OfficialPostLanguage =
    OfficialPostLanguage.fromAppLanguage(QuataLanguageManager.currentLanguage.tag)

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
