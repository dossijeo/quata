package com.quata.core.translation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect as AndroidRect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.language.QuataDetectedLanguage
import com.quata.core.language.QuataLanguageIdentifier
import com.quata.core.language.QuataTranslationLanguage
import com.quata.core.language.QuataTranslator
import com.quata.core.localization.QuataLanguage
import com.quata.core.localization.QuataLanguageManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.roundToInt
import kotlin.coroutines.resume

data class QuataTranslatableTextBox(
    val id: String,
    val text: String,
    val displayText: String,
    val bounds: Rect
)

data class QuataTranslatorBackground(
    val image: ImageBitmap,
    val originLeftPx: Int,
    val originTopPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val excludesNavigationBar: Boolean = false
)

enum class QuataTranslatorOverlaySource {
    Chat,
    Comments
}

@Stable
class QuataTranslatableTextRegistry {
    private val boxes = mutableStateMapOf<String, QuataTranslatableTextBox>()

    val visibleBoxes: List<QuataTranslatableTextBox>
        get() = boxes.values
            .filter { it.text.isNotBlank() && it.bounds.width > 8f && it.bounds.height > 8f }
            .sortedWith(compareBy<QuataTranslatableTextBox> { it.bounds.top }.thenBy { it.bounds.left })

    fun update(id: String, text: String, displayText: String, bounds: Rect) {
        if (text.isBlank()) {
            boxes.remove(id)
        } else {
            boxes[id] = QuataTranslatableTextBox(id = id, text = text, displayText = displayText, bounds = bounds)
        }
    }

    fun unregister(id: String) {
        boxes.remove(id)
    }
}

val LocalQuataTranslatableTextRegistry = compositionLocalOf<QuataTranslatableTextRegistry?> { null }
fun interface QuataTranslatorModeController {
    fun activate(anchorView: View, source: QuataTranslatorOverlaySource)
}

val LocalQuataTranslatorModeController = compositionLocalOf<QuataTranslatorModeController> {
    QuataTranslatorModeController { _, _ -> }
}

@Composable
fun QuataTranslatorModeProvider(
    registry: QuataTranslatableTextRegistry,
    controller: QuataTranslatorModeController,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalQuataTranslatableTextRegistry provides registry,
        LocalQuataTranslatorModeController provides controller
    ) {
        content()
    }
}

fun Modifier.quataTranslatableText(
    id: String,
    text: String,
    displayText: String = text
): Modifier = composed {
    val registry = LocalQuataTranslatableTextRegistry.current
    DisposableEffect(registry, id) {
        onDispose { registry?.unregister(id) }
    }
    if (registry == null) {
        this
    } else {
        onGloballyPositioned { coordinates ->
            registry.update(id, text, displayText, coordinates.boundsInWindow())
        }
    }
}

suspend fun captureTranslatorBackground(view: View): QuataTranslatorBackground? {
    val captureView = view.rootView ?: view
    val activityWindow = view.context.findActivity()?.window
    val activityDecorView = activityWindow?.decorView
    if (activityDecorView != null && captureView !== activityDecorView) {
        captureView.drawTranslatorBackgroundOrNull()?.let { return it }
    }

    val width = view.width
    val height = view.height
    if (width <= 0 || height <= 0) return null
    val location = IntArray(2)
    view.getLocationInWindow(location)
    val bitmap = Bitmap.createBitmap(
        (width * CaptureScale).roundToInt().coerceAtLeast(1),
        (height * CaptureScale).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (activityWindow != null) {
            val sourceRect = AndroidRect(
                location[0],
                location[1],
                location[0] + width,
                location[1] + height
            )
            val copied = suspendCancellableCoroutine { continuation ->
                runCatching {
                    PixelCopy.request(
                        activityWindow,
                        sourceRect,
                        bitmap,
                        { result ->
                            if (continuation.isActive) {
                                continuation.resume(result == PixelCopy.SUCCESS)
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                }.onFailure {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }
            if (copied) {
                val cropBottomPx = view.navigationBarCaptureCropHeightPx(context = view.context, topPx = location[1], heightPx = height)
                val croppedBitmap = bitmap.cropBottom(cropBottomPx)
                return QuataTranslatorBackground(
                    image = croppedBitmap.asImageBitmap(),
                    originLeftPx = location[0],
                    originTopPx = location[1],
                    widthPx = width,
                    heightPx = height - cropBottomPx,
                    excludesNavigationBar = cropBottomPx > 0
                )
            }
        }
    }

    val canvas = Canvas(bitmap)
    canvas.scale(CaptureScale, CaptureScale)
    return runCatching {
        view.draw(canvas)
        val cropBottomPx = view.navigationBarCaptureCropHeightPx(context = view.context, topPx = location[1], heightPx = height)
        val croppedBitmap = bitmap.cropBottom(cropBottomPx)
        QuataTranslatorBackground(
            image = croppedBitmap.asImageBitmap(),
            originLeftPx = location[0],
            originTopPx = location[1],
            widthPx = width,
            heightPx = height - cropBottomPx,
            excludesNavigationBar = cropBottomPx > 0
        )
    }.getOrNull()
}

private fun View.drawTranslatorBackgroundOrNull(): QuataTranslatorBackground? {
    val width = width
    val height = height
    if (width <= 0 || height <= 0) return null
    val location = IntArray(2)
    getLocationInWindow(location)
    val bitmap = Bitmap.createBitmap(
        (width * CaptureScale).roundToInt().coerceAtLeast(1),
        (height * CaptureScale).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    canvas.scale(CaptureScale, CaptureScale)
    return runCatching {
        draw(canvas)
        val cropBottomPx = navigationBarCaptureCropHeightPx(context = context, topPx = location[1], heightPx = height)
        val croppedBitmap = bitmap.cropBottom(cropBottomPx)
        QuataTranslatorBackground(
            image = croppedBitmap.asImageBitmap(),
            originLeftPx = location[0],
            originTopPx = location[1],
            widthPx = width,
            heightPx = height - cropBottomPx,
            excludesNavigationBar = cropBottomPx > 0
        )
    }.getOrNull()
}

@Composable
fun FangTranslatorIconButton(
    onClick: (View) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    val description = stringResource(R.string.translator_button_content_description)
    val view = androidx.compose.ui.platform.LocalView.current
    Box(
        modifier = modifier
            .size(width = 58.dp, height = 38.dp)
            .clip(RoundedCornerShape(16.dp))
            .graphicsLayer { alpha = if (enabled) 1f else 0.42f }
            .clickable(enabled = enabled) { onClick(view) }
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 2.dp, y = 2.dp)
                .size(width = 31.dp, height = 25.dp)
                .background(
                    brush = Brush.linearGradient(listOf(QuataOrange, Color(0xFFFF8A20))),
                    shape = RoundedCornerShape(13.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-1).dp, y = (-1).dp)
                .size(width = 39.dp, height = 22.dp)
                .background(
                    color = template.colors.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(1.dp, template.colors.accent.copy(alpha = 0.58f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "fang",
                color = template.colors.accent,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                lineHeight = 10.sp
            )
        }
    }
}

@Composable
fun QuataTranslatorOverlay(
    registry: QuataTranslatableTextRegistry,
    background: QuataTranslatorBackground?,
    source: QuataTranslatorOverlaySource,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val template = quataTheme()
    val translationStates = remember { mutableStateMapOf<String, TranslatorBoxState>() }
    val boxes = registry.visibleBoxes
    val visibleIds = boxes.map { it.id }.toSet()

    LaunchedEffect(visibleIds) {
        translationStates.keys
            .filter { it !in visibleIds }
            .forEach(translationStates::remove)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(template.colors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        val density = LocalDensity.current
        val localView = LocalView.current
        val isCommentOverlay = source == QuataTranslatorOverlaySource.Comments
        val statusBarCorrectionPx = if (isCommentOverlay) {
            localView.visibleStatusBarHeightPx(context)
        } else {
            0
        }
        val navigationBarCorrectionPx = if (background?.excludesNavigationBar == true) {
            0
        } else {
            localView.visibleNavigationBarHeightPx(context)
        }
        val viewportWidthPx = (background?.widthPx ?: constraints.maxWidth).coerceAtMost(constraints.maxWidth)
        val viewportHeightPx = ((background?.heightPx ?: constraints.maxHeight) - statusBarCorrectionPx - navigationBarCorrectionPx)
            .coerceAtLeast(1)
            .coerceAtMost(constraints.maxHeight)
        val viewportOriginLeftPx = background?.originLeftPx ?: 0
        val viewportOriginTopPx = (background?.originTopPx ?: 0) + statusBarCorrectionPx
        val viewportWidth = with(density) { viewportWidthPx.toDp() }
        val viewportHeight = with(density) { viewportHeightPx.toDp() }

        Box(
            modifier = Modifier
                .width(viewportWidth)
                .height(viewportHeight)
                .clipToBounds()
                .consumeTranslatorScrollGestures()
                .background(template.colors.background)
        ) {
            if (background != null) {
                Image(
                    bitmap = background.image,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.86f },
                    contentScale = ContentScale.FillBounds
                )
            }
            Image(
                painter = painterResource(R.drawable.quata_translator_frosted_texture),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.70f },
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.16f))
            )

            boxes.forEach { box ->
                TranslatorTextOverlayBox(
                    box = box,
                    state = translationStates[box.id],
                    viewportOriginLeftPx = viewportOriginLeftPx,
                    viewportOriginTopPx = viewportOriginTopPx,
                    onClick = {
                        val currentState = translationStates[box.id]
                        if (currentState?.translation != null) {
                            translationStates[box.id] = currentState.copy(showTranslation = !currentState.showTranslation)
                            return@TranslatorTextOverlayBox
                        }
                        if (currentState?.isLoading == true) return@TranslatorTextOverlayBox

                        translationStates[box.id] = TranslatorBoxState(originalText = box.text, isLoading = true)
                        scope.launch {
                            val translatedState = translateOverlayText(context, box.text)
                            translationStates[box.id] = translatedState ?: TranslatorBoxState(originalText = box.text)
                        }
                    }
                )
            }

            TranslatorModeHeader(
                onDismiss = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 24.dp, top = 26.dp, end = 24.dp)
            )
            TranslatorModeFooter(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp)
            )
        }
    }
}

private fun Modifier.consumeTranslatorScrollGestures(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                event.changes
                    .filter { it.positionChanged() }
                    .forEach { it.consume() }
            }
        }
    }

@Composable
private fun TranslatorTextOverlayBox(
    box: QuataTranslatableTextBox,
    state: TranslatorBoxState?,
    viewportOriginLeftPx: Int,
    viewportOriginTopPx: Int,
    onClick: () -> Unit
) {
    if (box.id.startsWith("feed-comment:")) {
        TranslatorCommentOverlayBox(
            box = box,
            state = state,
            viewportOriginLeftPx = viewportOriginLeftPx,
            viewportOriginTopPx = viewportOriginTopPx,
            onClick = onClick
        )
        return
    }
    if (box.id.startsWith("chat-message:")) {
        TranslatorChatOverlayBox(
            box = box,
            state = state,
            viewportOriginLeftPx = viewportOriginLeftPx,
            viewportOriginTopPx = viewportOriginTopPx,
            onClick = onClick
        )
        return
    }

    val density = LocalDensity.current
    val template = quataTheme()
    val left = with(density) { (box.bounds.left.roundToInt() - viewportOriginLeftPx).coerceAtLeast(0).toDp() }
    val top = with(density) { (box.bounds.top.roundToInt() - viewportOriginTopPx).coerceAtLeast(0).toDp() }
    val width = with(density) { box.bounds.width.roundToInt().coerceAtLeast(42).toDp() }
    val height = with(density) { box.bounds.height.roundToInt().coerceAtLeast(36).toDp() }
    val shape = RoundedCornerShape(18.dp)
    val displayText = if (state?.showTranslation == true && state.translation != null) {
        box.displayText.replaceFirst(box.text, state.translation)
    } else {
        box.displayText
    }

    Box(
        modifier = Modifier
            .offset(x = left, y = top)
            .size(width = width, height = height)
            .clip(shape)
            .background(
                color = when {
                    state?.showTranslation == true -> template.colors.accent.copy(alpha = 0.94f)
                    state?.isLoading == true -> template.colors.accent.copy(alpha = 0.18f)
                    else -> template.colors.surface.copy(alpha = 0.90f)
                },
                shape = shape
            )
            .border(
                width = 1.dp,
                color = when {
                    state?.showTranslation == true -> template.colors.accentContent.copy(alpha = 0.34f)
                    state?.isLoading == true -> template.colors.accent.copy(alpha = 0.64f)
                    else -> template.colors.divider
                },
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = displayText,
            color = if (state?.showTranslation == true) template.colors.accentContent else template.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        if (state?.isLoading == true) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp),
                color = template.colors.accent,
                strokeWidth = 2.dp
            )
        } else if (state?.showTranslation == true && state.directionLabel != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "T",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = state.directionLabel,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun TranslatorChatOverlayBox(
    box: QuataTranslatableTextBox,
    state: TranslatorBoxState?,
    viewportOriginLeftPx: Int,
    viewportOriginTopPx: Int,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val template = quataTheme()
    val left = with(density) { (box.bounds.left.roundToInt() - viewportOriginLeftPx).coerceAtLeast(0).toDp() }
    val top = with(density) { (box.bounds.top.roundToInt() - viewportOriginTopPx).coerceAtLeast(0).toDp() }
    val width = with(density) { box.bounds.width.roundToInt().coerceAtLeast(42).toDp() }
    val height = with(density) { box.bounds.height.roundToInt().coerceAtLeast(36).toDp() }
    val parts = remember(box.displayText, box.text, state?.translation, state?.showTranslation) {
        TranslatorChatParts.from(
            displayText = if (state?.showTranslation == true && state.translation != null) {
                box.displayText.replaceFirst(box.text, state.translation)
            } else {
                box.displayText
            },
            message = if (state?.showTranslation == true && state.translation != null) state.translation else box.text
        )
    }
    val shape = RoundedCornerShape(20.dp)
    val isTranslated = state?.showTranslation == true
    val textColor = if (parts.isMine || isTranslated) template.colors.accentContent else template.colors.textPrimary
    val bubbleColor = when {
        isTranslated -> template.colors.accent.copy(alpha = 0.94f)
        state?.isLoading == true -> template.colors.accent.copy(alpha = 0.18f)
        parts.isMine -> template.colors.chatMine
        else -> template.colors.chatOther
    }
    val borderColor = when {
        state?.isLoading == true -> template.colors.accent.copy(alpha = 0.58f)
        parts.isMine || isTranslated -> Color.Transparent
        else -> template.colors.divider
    }

    Box(
        modifier = Modifier
            .offset(x = left, y = top)
            .size(width = width, height = height)
            .clip(shape)
            .background(bubbleColor, shape)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = parts.sender,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (state?.showTranslation == true && state.directionLabel != null) {
                    TranslatorDirectionBadge(directionLabel = state.directionLabel)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = parts.timestamp,
                    color = textColor.copy(alpha = 0.56f),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = parts.message,
                color = textColor,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (state?.isLoading == true) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp),
                color = template.colors.accent,
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun TranslatorDirectionBadge(
    directionLabel: String,
    modifier: Modifier = Modifier
) {
    val displayLabel = remember(directionLabel) {
        directionLabel
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "T",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            lineHeight = 10.sp
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = displayLabel,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TranslatorCommentOverlayBox(
    box: QuataTranslatableTextBox,
    state: TranslatorBoxState?,
    viewportOriginLeftPx: Int,
    viewportOriginTopPx: Int,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val template = quataTheme()
    val left = with(density) { (box.bounds.left.roundToInt() - viewportOriginLeftPx).coerceAtLeast(0).toDp() }
    val top = with(density) { (box.bounds.top.roundToInt() - viewportOriginTopPx).coerceAtLeast(0).toDp() }
    val width = with(density) { box.bounds.width.roundToInt().coerceAtLeast(42).toDp() }
    val height = with(density) { box.bounds.height.roundToInt().coerceAtLeast(36).toDp() }
    val parts = remember(box.displayText, box.text, state?.translation, state?.showTranslation) {
        TranslatorCommentParts.from(
            displayText = if (state?.showTranslation == true && state.translation != null) {
                box.displayText.replaceFirst(box.text, state.translation)
            } else {
                box.displayText
            },
            message = if (state?.showTranslation == true && state.translation != null) state.translation else box.text
        )
    }
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .offset(x = left, y = top)
            .size(width = width, height = height)
            .clip(shape)
            .background(
                color = when {
                    state?.showTranslation == true -> template.colors.accent.copy(alpha = 0.94f)
                    state?.isLoading == true -> template.colors.accent.copy(alpha = 0.12f)
                    else -> template.colors.surface.copy(alpha = 0.96f)
                },
                shape = shape
            )
            .border(
                width = 1.dp,
                color = if (state?.isLoading == true) {
                    template.colors.accent.copy(alpha = 0.58f)
                } else {
                    template.colors.divider
                },
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        val isTranslated = state?.showTranslation == true
        val primary = if (isTranslated) template.colors.accentContent else template.colors.textPrimary
        val secondary = if (isTranslated) template.colors.accentContent.copy(alpha = 0.78f) else template.colors.textSecondary
        val accent = if (isTranslated) template.colors.accentContent else template.colors.accent

        if (parts.replyLine != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxSize()
                    .padding(top = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(height - 28.dp)
                        .background(accent)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (parts.replyLine != null) 14.dp else 0.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = parts.author,
                    color = primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = parts.timestamp,
                    color = secondary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            parts.replyLine?.let { reply ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = reply,
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            parts.quoteLine?.let { quote ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = quote,
                    color = secondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = parts.message,
                color = primary,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (state?.isLoading == true) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp),
                color = template.colors.accent,
                strokeWidth = 2.dp
            )
        } else if (state?.showTranslation == true && state.directionLabel != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("T", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                Spacer(Modifier.width(4.dp))
                Text(state.directionLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
    }
}

private data class TranslatorCommentParts(
    val author: String,
    val timestamp: String,
    val replyLine: String?,
    val quoteLine: String?,
    val message: String
) {
    companion object {
        fun from(displayText: String, message: String): TranslatorCommentParts {
            val lines = displayText.lines().map { it.trim() }.filter { it.isNotBlank() }
            val header = lines.firstOrNull().orEmpty()
            val headerParts = header.split(" · ", limit = 2)
            val author = headerParts.getOrNull(0).orEmpty()
            val timestamp = headerParts.getOrNull(1).orEmpty()
            val replyIndex = lines.indexOfFirst { it.startsWith("↳") || it.startsWith("Respuesta") || it.startsWith("Reply") || it.startsWith("Réponse") }
            val replyLine = lines.getOrNull(replyIndex)?.takeIf { replyIndex >= 0 }
            val quoteLine = if (replyIndex >= 0 && lines.size > replyIndex + 2) lines.getOrNull(replyIndex + 1) else null
            return TranslatorCommentParts(
                author = author,
                timestamp = timestamp,
                replyLine = replyLine,
                quoteLine = quoteLine?.takeUnless { it == message },
                message = message
            )
        }
    }
}

private data class TranslatorChatParts(
    val isMine: Boolean,
    val sender: String,
    val timestamp: String,
    val message: String
) {
    companion object {
        fun from(displayText: String, message: String): TranslatorChatParts {
            val lines = displayText.lines()
            val header = lines.firstOrNull().orEmpty()
            val headerParts = header.split(" | ", limit = 3)
            return TranslatorChatParts(
                isMine = headerParts.getOrNull(0) == "mine",
                sender = headerParts.getOrNull(1).orEmpty(),
                timestamp = headerParts.getOrNull(2).orEmpty(),
                message = message
            )
        }
    }
}

@Composable
private fun TranslatorModeHeader(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(listOf(Color(0xFFFF6A00), Color(0xFFFF7F1A))),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TranslatorSparkle()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.translator_mode_active_title),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.translator_mode_active_hint),
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("X", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.translator_mode_exit),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun TranslatorSparkle() {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            SparkleDot(size = 8)
            SparkleDot(size = 15)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SparkleDot(size = 16)
            SparkleDot(size = 9)
        }
    }
}

@Composable
private fun SparkleDot(size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White)
    )
}

@Composable
private fun TranslatorModeFooter(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.TouchApp,
            contentDescription = null,
            tint = QuataOrange,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.translator_mode_active_hint),
            color = Color.White.copy(alpha = 0.88f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

private suspend fun translateOverlayText(
    context: Context,
    text: String
): TranslatorBoxState? {
    val originalText = text.trim()
    if (originalText.isBlank()) return null
    return runCatching {
        val detection = QuataLanguageIdentifier.detect(context, originalText)
        val source = QuataTranslationLanguage.fromDetectedLanguage(detection.language) ?: return null
        val target = if (source == QuataTranslationLanguage.Fang) {
            QuataLanguageManager.currentLanguage.toTranslationLanguage()
        } else {
            QuataTranslationLanguage.Fang
        }
        if (source == target) return TranslatorBoxState(originalText = originalText)
        val result = QuataCachedTranslator.get(context).translate(
            text = originalText,
            sourceLanguage = source,
            targetLanguage = target
        )
        TranslatorBoxState(
            originalText = originalText,
            translation = result.translation,
            directionLabel = "${source.shortCode()}->${target.shortCode()}",
            showTranslation = true
        )
    }.getOrNull()
}

private fun QuataLanguage.toTranslationLanguage(): QuataTranslationLanguage = when (this) {
    QuataLanguage.Spanish -> QuataTranslationLanguage.Spanish
    QuataLanguage.French -> QuataTranslationLanguage.French
    QuataLanguage.English -> QuataTranslationLanguage.English
}

private fun QuataTranslationLanguage.shortCode(): String = when (this) {
    QuataTranslationLanguage.Fang -> "FAN"
    QuataTranslationLanguage.Spanish -> "ES"
    QuataTranslationLanguage.English -> "EN"
    QuataTranslationLanguage.French -> "FR"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun View.visibleStatusBarHeightPx(context: Context): Int {
    val insetTop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        rootWindowInsets?.getInsets(android.view.WindowInsets.Type.statusBars())?.top ?: 0
    } else {
        @Suppress("DEPRECATION")
        rootWindowInsets?.systemWindowInsetTop ?: 0
    }
    if (insetTop > 0) return insetTop
    val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
}

private fun View.visibleNavigationBarHeightPx(context: Context): Int {
    val insetBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        rootWindowInsets?.getInsets(android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0
    } else {
        @Suppress("DEPRECATION")
        rootWindowInsets?.systemWindowInsetBottom ?: 0
    }
    if (insetBottom > 0) return insetBottom
    val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
    return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
}

private fun View.navigationBarCaptureCropHeightPx(
    context: Context,
    topPx: Int,
    heightPx: Int
): Int {
    val navigationBarHeight = visibleNavigationBarHeightPx(context)
    if (navigationBarHeight <= 0 || heightPx <= navigationBarHeight) return 0

    val rootHeight = rootView.height
    val displayHeight = maxOf(context.resources.displayMetrics.heightPixels, rootHeight)
    val capturedBottom = topPx + heightPx
    val overlapsNavigationBar = capturedBottom > displayHeight - navigationBarHeight / 2
    return if (overlapsNavigationBar) {
        navigationBarHeight.coerceAtMost(heightPx - 1)
    } else {
        0
    }
}

private fun Bitmap.cropBottom(cropBottomPx: Int): Bitmap {
    if (cropBottomPx <= 0) return this
    val scaledCropBottom = (cropBottomPx * CaptureScale)
        .roundToInt()
        .coerceIn(0, height - 1)
    if (scaledCropBottom <= 0) return this
    return Bitmap.createBitmap(this, 0, 0, width, height - scaledCropBottom)
}

data class TranslatorBoxState(
    val originalText: String,
    val translation: String? = null,
    val directionLabel: String? = null,
    val showTranslation: Boolean = false,
    val isLoading: Boolean = false
)

private const val CaptureScale = 0.1f
