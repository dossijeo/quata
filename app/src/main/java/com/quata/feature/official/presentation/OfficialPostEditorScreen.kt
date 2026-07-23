package com.quata.feature.official.presentation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.language.QuataDetectedLanguage
import com.quata.core.language.QuataLanguageIdentifier
import com.quata.core.localization.QuataLanguageManager
import com.quata.core.model.User
import com.quata.core.text.decodeHtmlEntities
import com.quata.core.ui.components.QuataEditorScaffold
import com.quata.core.ui.components.QuataEditorToolButton
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.richtext.QuataRichTextEditorBox
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
    val template = quataTheme()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var editorMode by rememberSaveable { mutableStateOf(OfficialEditorMode.Quick) }
    var title by rememberSaveable { mutableStateOf("") }
    var summary by rememberSaveable { mutableStateOf("") }
    var contentHtml by rememberSaveable { mutableStateOf("") }
    var readMoreOption by rememberSaveable { mutableStateOf(OfficialReadMoreOption.ReadMore) }
    var readMoreMenuOpen by rememberSaveable { mutableStateOf(false) }
    var linkUrl by rememberSaveable { mutableStateOf("") }
    var mediaUrl by rememberSaveable { mutableStateOf("") }
    var mediaType by rememberSaveable { mutableStateOf<OfficialMediaType?>(null) }
    var postType by rememberSaveable { mutableStateOf(OfficialPostType.Announcement) }
    var typeMenuOpen by rememberSaveable { mutableStateOf(false) }
    var isLongEditorOpen by rememberSaveable { mutableStateOf(false) }
    var longEditorHtml by rememberSaveable { mutableStateOf("") }
    var imageEditorUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var videoEditorUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingTranslation by remember { mutableStateOf<OfficialPendingTranslation?>(null) }

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

    val contentPlain = contentHtml.stripHtmlForOfficialEditor()
    val quickTextBlocks = remember(contentHtml) { contentHtml.extractOfficialEditorBlocks() }
    val quickTitle = quickTextBlocks.firstOrNull().orEmpty()
    val quickSummary = quickTextBlocks.drop(1).joinToString(" ").ellipsizeOfficialSummary(140)
    val effectiveTitle = if (editorMode == OfficialEditorMode.Quick) quickTitle else title.trim()
    val effectiveSummary = if (editorMode == OfficialEditorMode.Quick) quickSummary else summary.trim()
    val effectiveReadMoreCode = if (editorMode == OfficialEditorMode.Quick) {
        OfficialReadMoreOption.ReadMore.shortcode
    } else {
        readMoreOption.shortcode
    }
    val effectiveLinkUrl = if (editorMode == OfficialEditorMode.Quick) "" else linkUrl.trim()

    fun canPublishPost(): Boolean =
        if (editorMode == OfficialEditorMode.Quick) {
            contentPlain.isNotBlank()
        } else {
            title.isNotBlank() && (
                summary.isNotBlank() ||
                    contentPlain.isNotBlank() ||
                    (mediaType != null && mediaUrl.isNotBlank())
                )
        }

    fun buildDraft(language: OfficialPostLanguage = currentOfficialPostLanguage()): OfficialPostDraft =
        OfficialPostDraft(
            title = effectiveTitle.ifBlank { context.getString(R.string.official_post_default_title) },
            summary = effectiveSummary,
            contentHtml = contentHtml,
            readMoreLabel = effectiveReadMoreCode,
            language = language,
            type = postType,
            mediaUrl = mediaUrl.takeIf { mediaType != null && it.isNotBlank() },
            mediaType = mediaType?.takeIf { mediaUrl.isNotBlank() },
            linkUrl = effectiveLinkUrl.takeIf { it.isNotBlank() },
            isLive = false
        )

    fun requestPublication() {
        val draft = buildDraft()
        coroutineScope.launch {
            val sourceLanguage = detectOfficialPostLanguage(context, draft)
            val missingLanguages = OfficialPostLanguage.entries.filterNot { it == sourceLanguage }
            pendingTranslation = OfficialPendingTranslation(
                draft = draft.copy(language = sourceLanguage),
                sourceLanguage = sourceLanguage,
                targetLanguages = missingLanguages
            )
        }
    }

    if (isLongEditorOpen) {
        OfficialLongContentEditor(
            html = longEditorHtml,
            title = stringResource(
                if (editorMode == OfficialEditorMode.Quick) {
                    R.string.official_form_body_quick
                } else {
                    R.string.official_form_body
                }
            ),
            onHtmlChange = { longEditorHtml = it },
            onBack = { isLongEditorOpen = false },
            onSave = {
                contentHtml = longEditorHtml
                isLongEditorOpen = false
            }
        )
    } else {
        QuataScreen(padding = padding) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.official_create),
                        color = template.colors.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 23.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OfficialEditorCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        if (editorMode == OfficialEditorMode.Quick) {
                                            R.string.official_form_mode_quick
                                        } else {
                                            R.string.official_form_mode_advanced
                                        }
                                    ),
                                    color = template.colors.textPrimary,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = stringResource(
                                        if (editorMode == OfficialEditorMode.Quick) {
                                            R.string.official_form_mode_description_quick
                                        } else {
                                            R.string.official_form_mode_description_advanced
                                        }
                                    ),
                                    color = template.colors.textSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp
                                )
                            }
                            Switch(
                                checked = editorMode == OfficialEditorMode.Advanced,
                                onCheckedChange = { checked ->
                                    editorMode = if (checked) OfficialEditorMode.Advanced else OfficialEditorMode.Quick
                                }
                            )
                        }
                    }

                    OfficialEditorCard {
                        OfficialEditorSectionTitle(stringResource(R.string.official_form_main_section))
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { typeMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(postType.editorLabel())
                            }
                            DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                                OfficialPostType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.editorLabel()) },
                                        onClick = {
                                            postType = type
                                            typeMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        if (editorMode == OfficialEditorMode.Advanced) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text(stringResource(R.string.official_form_title)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = summary,
                                onValueChange = { summary = it },
                                label = { Text(stringResource(R.string.official_form_summary)) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                    }

                    OfficialEditorCard {
                        OfficialEditorSectionTitle(stringResource(R.string.official_form_media_section))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.composer_pick_image), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.composer_pick_video), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val selectedMediaType = mediaType
                        if (mediaUrl.isNotBlank() && selectedMediaType != null) {
                            OfficialEditorMediaPreview(
                                mediaType = selectedMediaType,
                                mediaUrl = mediaUrl,
                                onEdit = {
                                    when (selectedMediaType) {
                                        OfficialMediaType.Image -> imageEditorUri = Uri.parse(mediaUrl)
                                        OfficialMediaType.Video -> videoEditorUri = Uri.parse(mediaUrl)
                                    }
                                },
                                onRemove = {
                                    mediaType = null
                                    mediaUrl = ""
                                }
                            )
                        }
                    }

                    OfficialEditorCard {
                        OfficialEditorSectionTitle(
                            stringResource(
                                if (editorMode == OfficialEditorMode.Quick) {
                                    R.string.official_form_body_quick
                                } else {
                                    R.string.official_form_read_more_section
                                }
                            )
                        )
                        if (editorMode == OfficialEditorMode.Advanced) {
                            Box(Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { readMoreMenuOpen = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(localizedOfficialReadMoreLabel(readMoreOption.shortcode))
                                }
                                DropdownMenu(
                                    expanded = readMoreMenuOpen,
                                    onDismissRequest = { readMoreMenuOpen = false }
                                ) {
                                    officialReadMoreUiOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(option.labelRes)) },
                                            onClick = {
                                                readMoreOption = option.option
                                                readMoreMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                longEditorHtml = contentHtml
                                isLongEditorOpen = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(
                                stringResource(
                                    if (editorMode == OfficialEditorMode.Quick) {
                                        R.string.official_form_edit_body_quick
                                    } else {
                                        R.string.official_form_edit_body
                                    }
                                ),
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        if (editorMode == OfficialEditorMode.Advanced) {
                            OutlinedTextField(
                                value = linkUrl,
                                onValueChange = { linkUrl = it },
                                label = { Text(stringResource(R.string.official_form_link)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }

                    OfficialEditorSectionTitle(stringResource(R.string.composer_preview))
                    OfficialPostPreview(
                        author = currentUser,
                        title = effectiveTitle,
                        summary = effectiveSummary,
                        contentHtml = contentHtml,
                        readMoreLabel = effectiveReadMoreCode,
                        postType = postType,
                        mediaUrl = mediaUrl,
                        mediaType = mediaType,
                        linkUrl = effectiveLinkUrl
                    )

                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }

                    OfficialPublishButton(
                        enabled = canPublishPost(),
                        isPublishing = isPublishing,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { requestPublication() },
                    )
                    Spacer(Modifier.height(96.dp))
                }
            }
        }
    }

    pendingTranslation?.let { pending ->
        OfficialTranslationPromptDialog(
            pending = pending,
            onDismiss = {
                if (!pending.isTranslating) {
                    pendingTranslation = null
                }
            },
            onSkip = {
                val groupId = UUID.randomUUID().toString()
                onSubmit(listOf(pending.draft.copy(translationGroupId = groupId)))
                pendingTranslation = null
            },
            onGenerate = {
                pendingTranslation = pending.copy(isTranslating = true)
                coroutineScope.launch {
                    val translatedDrafts = runCatching {
                        buildTranslatedOfficialDrafts(context, pending)
                    }.getOrElse {
                        listOf(pending.draft.copy(translationGroupId = UUID.randomUUID().toString()))
                    }
                    onSubmit(translatedDrafts)
                    pendingTranslation = null
                }
            }
        )
    }

    imageEditorUri?.let { sourceUri ->
        QuataImageEditorDialog(
            imageUri = sourceUri,
            onDismiss = { imageEditorUri = null },
            onEdited = { editedUri ->
                mediaType = OfficialMediaType.Image
                mediaUrl = editedUri.toString()
                imageEditorUri = null
            }
        )
    }

    videoEditorUri?.let { sourceUri ->
        QuataVideoEditorDialog(
            videoUri = sourceUri,
            onDismiss = { videoEditorUri = null },
            onExported = { editedUri ->
                mediaType = OfficialMediaType.Video
                mediaUrl = editedUri.toString()
                videoEditorUri = null
            }
        )
    }
}

private enum class OfficialEditorMode {
    Quick,
    Advanced
}

private data class OfficialPendingTranslation(
    val draft: OfficialPostDraft,
    val sourceLanguage: OfficialPostLanguage,
    val targetLanguages: List<OfficialPostLanguage>,
    val isTranslating: Boolean = false
)

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.official_translation_title), fontWeight = FontWeight.Black) },
        text = {
            if (pending.isTranslating) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OfficialTranslationLoader()
                    Text(stringResource(R.string.official_translation_progress), fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    stringResource(
                        R.string.official_translation_message,
                        languageName(pending.sourceLanguage),
                        targets
                    )
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !pending.isTranslating, onClick = onGenerate) {
                Text(stringResource(R.string.official_translation_confirm), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !pending.isTranslating, onClick = onSkip) {
                Text(stringResource(R.string.official_translation_skip))
            }
        }
    )
}

@Composable
private fun OfficialTranslationLoader() {
    val transition = rememberInfiniteTransition(label = "official_translation_loader")
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "official_translation_loader_step"
    )
    val flip by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "official_translation_loader_flip"
    )
    val isSecondPhase = step >= 0.5f
    val containerRotation = if (isSecondPhase) -90f else 0f
    val containerScale = if (isSecondPhase) -1f else 1f
    val halfColor = if (isSecondPhase) Color(0xFF25B09B) else Color(0xFF514B82)
    val halfTravel = when {
        flip <= 0.05f -> 0.dp
        flip <= 0.33f -> ((flip - 0.05f) / 0.28f * -10f).dp
        flip <= 0.66f -> (-10).dp
        flip <= 0.95f -> (-10f + ((flip - 0.66f) / 0.29f * 10f)).dp
        else -> 0.dp
    }
    val halfRotationX = when {
        flip <= 0.33f -> 0f
        flip <= 0.66f -> ((flip - 0.33f) / 0.33f) * -180f
        else -> -180f
    }
    val leftShape = if (isSecondPhase) {
        RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp)
    } else {
        RoundedCornerShape(0.dp)
    }
    val rightShape = if (isSecondPhase) {
        RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp)
    } else {
        RoundedCornerShape(0.dp)
    }
    Row(
        modifier = Modifier
            .size(60.dp)
            .rotate(containerRotation)
            .graphicsLayer {
                scaleX = containerScale
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .graphicsLayer {
                    translationX = halfTravel.toPx()
                    rotationX = halfRotationX
                    cameraDistance = 12f * density
                }
                .background(halfColor, leftShape)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .graphicsLayer {
                    translationX = -halfTravel.toPx()
                    rotationX = -halfRotationX
                    cameraDistance = 12f * density
                }
                .background(halfColor, rightShape)
        )
    }
}

@Composable
private fun OfficialLongContentEditor(
    html: String,
    title: String,
    onHtmlChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val template = quataTheme()
    QuataEditorScaffold(
        title = title,
        showTitle = true,
        onBack = onBack,
        backContentDescription = stringResource(R.string.video_editor_back),
        actions = {
            QuataEditorToolButton(
                label = stringResource(R.string.common_save_changes),
                enabled = true,
                onClick = onSave
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
            }
        }
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 10.dp)
        ) {
            QuataRichTextEditorBox(
                initialHtml = html,
                placeholder = title,
                onHtmlChange = onHtmlChange,
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxWidth()
                    .border(1.dp, template.colors.divider, RoundedCornerShape(8.dp))
                    .padding(top = 6.dp, bottom = 2.dp)
            )
        }
    }
}

@Composable
private fun OfficialEditorCard(
    content: @Composable ColumnScope.() -> Unit
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surface,
        contentColor = template.colors.textPrimary,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, template.colors.divider.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun OfficialEditorSectionTitle(text: String) {
    Text(
        text = text,
        color = quataTheme().colors.textPrimary,
        fontWeight = FontWeight.Black,
        fontSize = 16.sp
    )
}

@Composable
private fun OfficialEditorMediaPreview(
    mediaType: OfficialMediaType,
    mediaUrl: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surfaceAlt,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
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
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.common_remove), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
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
    val template = quataTheme()
    var readMorePost by remember { mutableStateOf<OfficialPostItem?>(null) }
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
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val proportionalHeight = maxWidth * 1.45f
        val previewHeight = when {
            proportionalHeight < 420.dp -> 420.dp
            proportionalHeight > 560.dp -> 560.dp
            else -> proportionalHeight
        }
        Surface(
            color = template.colors.surfaceAlt,
            contentColor = template.colors.textPrimary,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .border(1.dp, template.colors.divider.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
        ) {
            OfficialPostCard(
                post = previewPost,
                rank = 1,
                canPublish = false,
                canModerate = false,
                onCreate = {},
                onOpenAuthor = {},
                onReadMore = { readMorePost = previewPost },
                onOpenMedia = {},
                onOpenLive = {},
                onLike = {},
                onComment = {},
                onShare = {},
                onDelete = {},
                modifier = Modifier.fillMaxSize(),
                forcePortraitLayout = true
            )
        }
    }
    readMorePost?.let { post ->
        OfficialPostDetailPanel(
            post = post,
            onDismiss = { readMorePost = null }
        )
    }
}

@Composable
private fun OfficialPublishButton(
    enabled: Boolean,
    isPublishing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        enabled = enabled && !isPublishing,
        colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.White),
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isPublishing) {
                LinearProgressIndicator(
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Text(stringResource(R.string.composer_publishing), fontWeight = FontWeight.Bold)
                }
            } else {
                Text(stringResource(R.string.official_publish), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OfficialPostType.editorLabel(): String = when (this) {
    OfficialPostType.Announcement -> stringResource(R.string.official_type_announcement)
    OfficialPostType.News -> stringResource(R.string.official_type_news)
    OfficialPostType.Event -> stringResource(R.string.official_type_event)
    OfficialPostType.Urgent -> stringResource(R.string.official_type_urgent)
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

private suspend fun detectOfficialPostLanguage(
    context: android.content.Context,
    draft: OfficialPostDraft
): OfficialPostLanguage {
    val text = buildString {
        appendLine(draft.title)
        appendLine(draft.summary)
        append(draft.contentHtml.stripHtmlForOfficialEditor())
    }.trim()
    val detected = runCatching { QuataLanguageIdentifier.detect(context, text) }.getOrNull()?.language
    return when (detected) {
        QuataDetectedLanguage.Spanish -> OfficialPostLanguage.Spanish
        QuataDetectedLanguage.English -> OfficialPostLanguage.English
        QuataDetectedLanguage.French -> OfficialPostLanguage.French
        else -> currentOfficialPostLanguage()
    }
}

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
