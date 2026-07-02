package com.quata.feature.official.presentation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.quata.core.model.User
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.richtext.QuataRichTextEditorBox
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialRepository
import com.quata.feature.postcomposer.imageeditor.QuataImageEditorDialog
import com.quata.feature.postcomposer.videoeditor.QuataVideoEditorDialog

@Composable
fun OfficialPostEditorRoute(
    padding: PaddingValues,
    repository: OfficialRepository,
    onBack: () -> Unit,
    onPublished: () -> Unit,
    onFullscreenEditorVisibilityChange: (Boolean) -> Unit = {},
    viewModel: OfficialFeedViewModel = viewModel(factory = OfficialFeedViewModel.factory(repository))
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.message) {
        if (state.message != null) {
            viewModel.onEvent(OfficialFeedUiEvent.ClearMessage)
            onPublished()
        }
    }

    OfficialPostEditorScreen(
        padding = padding,
        currentUser = state.currentUser,
        isPublishing = state.isPublishing,
        error = state.error,
        onBack = onBack,
        onSubmit = { draft -> viewModel.onEvent(OfficialFeedUiEvent.CreatePost(draft)) },
        onFullscreenEditorVisibilityChange = onFullscreenEditorVisibilityChange
    )
}

@Composable
fun OfficialPostEditorScreen(
    padding: PaddingValues,
    currentUser: User?,
    isPublishing: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSubmit: (OfficialPostDraft) -> Unit,
    onFullscreenEditorVisibilityChange: (Boolean) -> Unit = {}
) {
    val template = quataTheme()
    val defaultReadMoreLabel = stringResource(R.string.official_read_more)
    var title by rememberSaveable { mutableStateOf("") }
    var summary by rememberSaveable { mutableStateOf("") }
    var contentHtml by rememberSaveable { mutableStateOf("") }
    var readMoreLabel by rememberSaveable { mutableStateOf(defaultReadMoreLabel) }
    var linkUrl by rememberSaveable { mutableStateOf("") }
    var mediaUrl by rememberSaveable { mutableStateOf("") }
    var mediaType by rememberSaveable { mutableStateOf<OfficialMediaType?>(null) }
    var postType by rememberSaveable { mutableStateOf(OfficialPostType.Announcement) }
    var typeMenuOpen by rememberSaveable { mutableStateOf(false) }
    var isLongEditorOpen by rememberSaveable { mutableStateOf(false) }
    var longEditorHtml by rememberSaveable { mutableStateOf("") }
    var imageEditorUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var videoEditorUri by rememberSaveable { mutableStateOf<Uri?>(null) }

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

    fun canPublishPost(): Boolean =
        title.isNotBlank() && (
            summary.isNotBlank() ||
                contentHtml.stripHtmlForOfficialEditor().isNotBlank() ||
                (mediaType != null && mediaUrl.isNotBlank())
            )

    if (isLongEditorOpen) {
        OfficialLongContentEditor(
            html = longEditorHtml,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, enabled = !isPublishing) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                        Text(
                            text = stringResource(R.string.official_create),
                            color = template.colors.textPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 23.sp,
                            modifier = Modifier.weight(1f)
                        )
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
                        OfficialEditorSectionTitle(stringResource(R.string.official_form_read_more_section))
                        OutlinedTextField(
                            value = readMoreLabel,
                            onValueChange = { readMoreLabel = it },
                            label = { Text(stringResource(R.string.official_form_read_more_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedButton(
                            onClick = {
                                longEditorHtml = contentHtml
                                isLongEditorOpen = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.official_form_edit_body), fontWeight = FontWeight.ExtraBold)
                        }
                        OutlinedTextField(
                            value = linkUrl,
                            onValueChange = { linkUrl = it },
                            label = { Text(stringResource(R.string.official_form_link)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    OfficialEditorSectionTitle(stringResource(R.string.composer_preview))
                    OfficialPostPreview(
                        author = currentUser,
                        title = title,
                        summary = summary,
                        contentHtml = contentHtml,
                        readMoreLabel = readMoreLabel.ifBlank { defaultReadMoreLabel },
                        postType = postType,
                        mediaUrl = mediaUrl,
                        mediaType = mediaType,
                        linkUrl = linkUrl
                    )

                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }

                    OfficialPublishButton(
                        enabled = canPublishPost(),
                        isPublishing = isPublishing,
                        onClick = {
                            onSubmit(
                                OfficialPostDraft(
                                    title = title.trim(),
                                    summary = summary.trim(),
                                    contentHtml = contentHtml,
                                    readMoreLabel = readMoreLabel.trim().ifBlank { defaultReadMoreLabel },
                                    type = postType,
                                    mediaUrl = mediaUrl.takeIf { mediaType != null && it.isNotBlank() },
                                    mediaType = mediaType?.takeIf { mediaUrl.isNotBlank() },
                                    linkUrl = linkUrl.trim().takeIf { it.isNotBlank() },
                                    isLive = false
                                )
                            )
                        },
                    )
                }
            }
        }
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

@Composable
private fun OfficialLongContentEditor(
    html: String,
    onHtmlChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val template = quataTheme()
    Surface(color = template.colors.background, contentColor = template.colors.textPrimary, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(
                    stringResource(R.string.official_form_body),
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSave) {
                    Text(stringResource(R.string.common_save_changes))
                }
            }
            QuataRichTextEditorBox(
                initialHtml = html,
                placeholder = stringResource(R.string.official_form_body),
                onHtmlChange = onHtmlChange,
                modifier = Modifier
                    .weight(1f)
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
        readMoreLabel = readMoreLabel.ifBlank { stringResource(R.string.official_read_more) },
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

private fun String.escapePreviewHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

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

private fun String.stripHtmlForOfficialEditor(): String = replace(Regex("<[^>]+>"), " ")
    .replace("&nbsp;", " ")
    .trim()
