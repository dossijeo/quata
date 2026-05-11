package com.quata.feature.postcomposer.presentation

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataSurface
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.textCanvasBrush
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class ComposerStep {
    TypePicker,
    Text,
    Image,
    Video
}

private enum class CaptureTarget {
    Photo,
    Video
}

private enum class EmojiSection(@StringRes val labelRes: Int, val emojis: List<String>) {
    Recentes(R.string.emoji_recent, listOf("😀", "😁", "😂", "🤣", "😊", "😍", "🥰", "😘", "😎", "🤩", "🤗", "😴", "🤔", "😅", "😳", "😭", "😤", "😡", "🤯", "🥳", "👏", "👎", "🙏", "💪", "🔥")),
    Frecuentes(R.string.emoji_frequent, listOf("😀", "😍", "😂", "🥰", "👏", "🙌", "🔥", "❤️", "👍", "🙏", "💯", "✨", "🌍", "⭐", "🎉", "📍", "✅", "👀", "💬", "💎")),
    Gestos(R.string.emoji_gestures, listOf("👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✊", "🤞", "☝️", "👉", "👈", "🖕", "👇", "👍", "👎", "👊", "🤜", "🤛", "🙌", "🙏", "🤝")),
    Objetos(R.string.emoji_objects_symbols, listOf("🏢", "💻", "🕘", "📷", "🎥", "📺", "🎮", "🎧", "🧠", "🫀", "💡", "📌", "📎", "✂️", "🔒", "🔑", "🪙", "💸", "💰", "🧾", "💎", "⚙️", "🛒", "🧳")),
    Animales(R.string.emoji_animals_nature, listOf("🐶", "🐱", "🦁", "🐵", "🐼", "🦋", "🐝", "🌴", "🌿", "🍃", "🌺", "🌸", "🌞", "🌙", "⭐", "☁️", "🌧️", "🌊", "🔥", "🌍"))
}

@Composable
fun CreatePostScreen(
    padding: PaddingValues,
    repository: PostComposerRepository,
    onPostCreated: () -> Unit,
    viewModel: CreatePostViewModel = viewModel(factory = CreatePostViewModel.factory(repository))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(ComposerStep.TypePicker) }
    var textValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var isEmojiPanelOpen by rememberSaveable { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCaptureTarget by remember { mutableStateOf<CaptureTarget?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            context.lastKnownLocation()?.let { location ->
                viewModel.onEvent(CreatePostUiEvent.LocationResolved(location.toLocationLabel(), location.latitude, location.longitude))
            }
        }
    }
    val galleryImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onEvent(CreatePostUiEvent.ImageSelected(uri?.toString()))
        uri?.let {
            val exifLocation = context.exifLocationFromUri(it)
            if (exifLocation != null) {
                viewModel.onEvent(CreatePostUiEvent.LocationResolved(exifLocation.toLocationLabel(), exifLocation.latitude, exifLocation.longitude))
            } else {
                locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
    }
    val cameraImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) {
            val uri = pendingImageUri
            viewModel.onEvent(CreatePostUiEvent.ImageSelected(uri?.toString()))
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    val galleryVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onEvent(CreatePostUiEvent.VideoSelected(uri?.toString()))
    }
    val cameraVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { saved ->
        if (saved) viewModel.onEvent(CreatePostUiEvent.VideoSelected(pendingVideoUri?.toString()))
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            when (pendingCaptureTarget) {
                CaptureTarget.Photo -> pendingImageUri?.let { cameraImageLauncher.launch(it) }
                CaptureTarget.Video -> pendingVideoUri?.let { cameraVideoLauncher.launch(it) }
                null -> Unit
            }
        } else {
            Toast.makeText(context, context.getString(R.string.composer_camera_permission), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onPostCreated()
            viewModel.onEvent(CreatePostUiEvent.ClearMessage)
        }
    }

    QuataScreen(padding) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(
                    onClick = { step = ComposerStep.TypePicker },
                    modifier = Modifier
                        .size(48.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
                }
            }
            Text(
                text = stringResource(R.string.composer_title),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                color = Color.White
            )
            Spacer(Modifier.height(28.dp))

            when (step) {
                ComposerStep.TypePicker -> TypePicker(
                    onText = { step = ComposerStep.Text },
                    onImage = { step = ComposerStep.Image },
                    onVideo = { step = ComposerStep.Video }
                )
                ComposerStep.Text -> TextPostForm(
                    state = state,
                    textValue = textValue,
                    isEmojiPanelOpen = isEmojiPanelOpen,
                    onTextChange = {
                        textValue = it
                        viewModel.onEvent(CreatePostUiEvent.TextChanged(it.text))
                    },
                    onToggleEmojiPanel = { isEmojiPanelOpen = !isEmojiPanelOpen },
                    onEmoji = { emoji ->
            val updated = textValue.insertAtSelection(emoji)
            textValue = updated
            viewModel.onEvent(CreatePostUiEvent.TextChanged(updated.text))
                    },
                    onChangeType = { step = ComposerStep.TypePicker },
                    onSubmit = { viewModel.submit(PostComposerType.Text) }
                )
                ComposerStep.Image -> ImagePostForm(
                    state = state,
                    onChangeType = { step = ComposerStep.TypePicker },
                    onPickImage = { galleryImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onTakePhoto = {
                        val uri = context.createMediaUri("quata_photo_", "image/jpeg", MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        pendingImageUri = uri
                        pendingCaptureTarget = CaptureTarget.Photo
                        if (uri != null) {
                            if (context.hasCameraPermission()) {
                                cameraImageLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    onRequestLocation = { locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    onSubmit = { viewModel.submit(PostComposerType.Image) }
                )
                ComposerStep.Video -> VideoPostForm(
                    state = state,
                    onDescriptionChange = { viewModel.onEvent(CreatePostUiEvent.TextChanged(it)) },
                    onChangeType = { step = ComposerStep.TypePicker },
                    onPickVideo = { galleryVideoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                    onRecordVideo = {
                        val uri = context.createMediaUri("quata_video_", "video/mp4", MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                        pendingVideoUri = uri
                        pendingCaptureTarget = CaptureTarget.Video
                        if (uri != null) {
                            if (context.hasCameraPermission()) {
                                cameraVideoLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    onSubmit = { viewModel.submit(PostComposerType.Video) }
                )
            }

            state.error?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            state.successMessage?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = QuataOrange, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TypePicker(
    onText: () -> Unit,
    onImage: () -> Unit,
    onVideo: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TypeCard(stringResource(R.string.composer_text_type), Icons.Filled.Edit, onText)
        TypeCard(stringResource(R.string.composer_image_type), Icons.Filled.PhotoCamera, onImage)
        TypeCard(stringResource(R.string.composer_video_type), Icons.Filled.Videocam, onVideo)
    }
}

@Composable
private fun TypeCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = QuataSurface.copy(alpha = 0.62f),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(128.dp)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFF28405D), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(20.dp))
            Text(label.uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun TextPostForm(
    state: CreatePostUiState,
    textValue: TextFieldValue,
    isEmojiPanelOpen: Boolean,
    onTextChange: (TextFieldValue) -> Unit,
    onToggleEmojiPanel: () -> Unit,
    onEmoji: (String) -> Unit,
    onChangeType: () -> Unit,
    onSubmit: () -> Unit
) {
    StepHeader(
        stringResource(R.string.composer_text_post_title),
        stringResource(R.string.composer_text_post_subtitle),
        stringResource(R.string.composer_step_text),
        onChangeType
    )
    ComposerPanel(stringResource(R.string.composer_content)) {
        OutlinedTextField(
            value = textValue,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(R.string.composer_text_placeholder)) },
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = onToggleEmojiPanel) {
                    Icon(
                        Icons.Filled.InsertEmoticon,
                        contentDescription = stringResource(R.string.comments_show_emojis),
                        tint = Color(0xFFFFC55C)
                    )
                }
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(stringResource(R.string.composer_word_count, state.text.length), color = Color.White.copy(alpha = 0.62f))
        }
    }
    if (isEmojiPanelOpen) {
        Spacer(Modifier.height(10.dp))
        ExpandedEmojiPanel(onEmoji = onEmoji)
    }
    PreviewPanel(stringResource(R.string.composer_preview)) {
        TextReelPreview(state.text)
    }
    PublishButton(state.isLoading, onSubmit)
}

@Composable
private fun ImagePostForm(
    state: CreatePostUiState,
    onChangeType: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onRequestLocation: () -> Unit,
    onSubmit: () -> Unit
) {
    StepHeader(
        stringResource(R.string.composer_image_post_title),
        stringResource(R.string.composer_image_post_subtitle),
        stringResource(R.string.composer_step_image),
        onChangeType
    )
    ComposerPanel(stringResource(R.string.composer_image)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ComposerActionButton(stringResource(R.string.composer_pick_image), Icons.Filled.PhotoLibrary, onPickImage, Modifier.weight(1f))
            ComposerActionButton(stringResource(R.string.composer_take_photo), Icons.Filled.PhotoCamera, onTakePhoto, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = QuataOrange)
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.locationLabel ?: stringResource(R.string.composer_no_location),
                color = Color.White.copy(alpha = 0.68f),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onRequestLocation) {
                Text(stringResource(R.string.common_update))
            }
        }
    }
    PreviewPanel(stringResource(R.string.composer_preview)) {
        if (state.imageUri != null) {
            AsyncImage(
                model = state.imageUri,
                contentDescription = stringResource(R.string.composer_selected_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f)
                    .clip(RoundedCornerShape(20.dp))
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.feed_location_chip, state.locationLabel ?: stringResource(R.string.composer_pending_location)),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        } else {
            EmptyPreview(
                stringResource(R.string.composer_image_preview_title),
                stringResource(R.string.composer_image_preview_tag),
                stringResource(R.string.composer_image_preview_body)
            )
        }
    }
    PublishButton(state.isLoading, onSubmit)
}

@Composable
private fun VideoPostForm(
    state: CreatePostUiState,
    onDescriptionChange: (String) -> Unit,
    onChangeType: () -> Unit,
    onPickVideo: () -> Unit,
    onRecordVideo: () -> Unit,
    onSubmit: () -> Unit
) {
    StepHeader(
        stringResource(R.string.composer_video_post_title),
        stringResource(R.string.composer_video_post_subtitle),
        stringResource(R.string.composer_step_video),
        onChangeType
    )
    ComposerPanel(stringResource(R.string.composer_video)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ComposerActionButton(stringResource(R.string.composer_pick_video), Icons.Filled.VideoLibrary, onPickVideo, Modifier.weight(1f))
            ComposerActionButton(stringResource(R.string.composer_record_video), Icons.Filled.Videocam, onRecordVideo, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text(state.videoUri ?: stringResource(R.string.composer_no_file), color = Color.White.copy(alpha = 0.68f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    ComposerPanel(stringResource(R.string.composer_description)) {
        OutlinedTextField(
            value = state.text,
            onValueChange = onDescriptionChange,
            placeholder = { Text(stringResource(R.string.composer_description_placeholder)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
    }
    PreviewPanel(stringResource(R.string.composer_preview)) {
        EmptyPreview(
            title = if (state.videoUri == null) stringResource(R.string.composer_video_preview_empty) else stringResource(R.string.composer_video_ready),
            tag = stringResource(R.string.composer_video_preview_tag),
            body = state.text.ifBlank { stringResource(R.string.composer_video_preview_body) }
        )
    }
    PublishButton(state.isLoading, onSubmit)
}

@Composable
private fun StepHeader(title: String, subtitle: String, step: String, onChangeType: () -> Unit) {
    Surface(color = Color.White.copy(alpha = 0.94f), contentColor = Color(0xFF111827), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Surface(color = Color(0xFF111827), contentColor = Color.White, shape = RoundedCornerShape(16.dp)) {
                    Text(stringResource(R.string.composer_chosen), fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(step, color = Color(0xFF4A5568), fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(12.dp))
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Color(0xFF42526A), lineHeight = 24.sp)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onChangeType,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E7EB), contentColor = Color(0xFF111827)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.composer_change_type), fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun ComposerPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = QuataSurface.copy(alpha = 0.45f),
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title.uppercase(), color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun PreviewPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    ComposerPanel(title, content)
}

@Composable
private fun ComposerActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(64.dp), shape = RoundedCornerShape(18.dp)) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun TextReelPreview(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(textCanvasBrush(text))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.ifBlank { stringResource(R.string.composer_text_preview_empty) },
            color = Color.White,
            fontSize = if (text.isBlank()) 20.sp else 34.sp,
            lineHeight = if (text.isBlank()) 28.sp else 42.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyPreview(title: String, tag: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF101827))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.White, fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        Surface(color = Color.Transparent, shape = RoundedCornerShape(16.dp), modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))) {
            Text(tag, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(body, color = Color.White.copy(alpha = 0.72f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ExpandedEmojiPanel(onEmoji: (String) -> Unit) {
    var section by rememberSaveable { mutableStateOf(EmojiSection.Frecuentes) }
    Surface(
        color = Color(0xFF101827),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, QuataOrange.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(14.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(EmojiSection.entries.toList()) { item ->
                    Surface(
                        color = if (item == section) QuataOrange.copy(alpha = 0.34f) else Color.Transparent,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.clickable { section = item }
                    ) {
                        Text(
                            stringResource(item.labelRes),
                            color = Color.White.copy(alpha = if (item == section) 1f else 0.76f),
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(section.emojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { onEmoji(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 26.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PublishButton(isLoading: Boolean, onSubmit: () -> Unit) {
    Button(
        onClick = onSubmit,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(
            if (isLoading) stringResource(R.string.composer_publishing) else stringResource(R.string.nav_publish),
            fontWeight = FontWeight.ExtraBold
        )
    }
}

private fun TextFieldValue.insertAtSelection(value: String): TextFieldValue {
    val start = selection.start.coerceIn(0, text.length)
    val end = selection.end.coerceIn(0, text.length)
    val replaceStart = minOf(start, end)
    val replaceEnd = maxOf(start, end)
    val updatedText = text.replaceRange(replaceStart, replaceEnd, value)
    val cursor = replaceStart + value.length
    return TextFieldValue(updatedText, TextRange(cursor))
}

private fun Context.exifLocationFromUri(uri: Uri): Location? {
    return runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            val latLong = ExifInterface(input).latLong ?: return null
            Location("exif").apply {
                latitude = latLong[0]
                longitude = latLong[1]
            }
        }
    }.getOrNull()
}

@Suppress("MissingPermission")
private fun Context.lastKnownLocation(): Location? {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.getProviders(true)
        .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Location.toLocationLabel(): String = "%.5f, %.5f".format(latitude, longitude)

private fun Context.createMediaUri(prefix: String, mimeType: String, collection: Uri): Uri? {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$prefix$timestamp")
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
    }
    return contentResolver.insert(collection, values)
}
