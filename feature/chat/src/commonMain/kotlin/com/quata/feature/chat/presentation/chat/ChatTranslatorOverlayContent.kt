package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.language.QuataTranslationLanguage
import com.quata.core.language.TextTranslator
import com.quata.core.language.TranslatorBoxState
import com.quata.core.language.shortCode
import com.quata.designsystem.translation.QuataTranslatableTextRegistry
import com.quata.designsystem.translation.QuataTranslatorBackdrop
import kotlinx.coroutines.launch

data class ChatTranslationDirection(
    val source: QuataTranslationLanguage,
    val target: QuataTranslationLanguage,
) {
    val label: String get() = "${source.shortCode()}→${target.shortCode()}"
    fun reversed() = ChatTranslationDirection(target, source)
}

fun interface ChatTranslationGateway {
    suspend fun translate(text: String, direction: ChatTranslationDirection): TranslatorBoxState
}

class FangChatTranslationGateway(
    private val translator: TextTranslator,
) : ChatTranslationGateway {
    override suspend fun translate(text: String, direction: ChatTranslationDirection): TranslatorBoxState {
        val original = text.trim()
        require(original.isNotBlank())
        val result = translator.translate(original, direction.source, direction.target)
        return TranslatorBoxState(
            originalText = original,
            translation = result.translation,
            directionLabel = direction.label,
            showTranslation = true,
        )
    }
}

data class ChatTranslatorStrings(
    val contentDescription: String,
    val activeTitle: String,
    val exit: String,
    val instruction: String,
    val error: String,
)

fun chatTranslationDirectionForLanguage(languageTag: String?): ChatTranslationDirection {
    val target = when (languageTag?.substringBefore('-')?.substringBefore('_')?.lowercase()) {
        "fr" -> QuataTranslationLanguage.French
        "en" -> QuataTranslationLanguage.English
        else -> QuataTranslationLanguage.Spanish
    }
    return ChatTranslationDirection(QuataTranslationLanguage.Fang, target)
}

fun chatTranslatorStringsForLanguage(languageTag: String?): ChatTranslatorStrings =
    when (languageTag?.substringBefore('-')?.substringBefore('_')?.lowercase()) {
        "fr" -> ChatTranslatorStrings(
            contentDescription = "Traducteur Fang",
            activeTitle = "Mode traducteur actif",
            exit = "Quitter",
            instruction = "Touchez un message pour le traduire",
            error = "Traduction impossible. Touchez pour réessayer.",
        )
        "en" -> ChatTranslatorStrings(
            contentDescription = "Fang translator",
            activeTitle = "Translator mode active",
            exit = "Exit",
            instruction = "Tap any message to translate it",
            error = "Translation failed. Tap to retry.",
        )
        else -> ChatTranslatorStrings(
            contentDescription = "Traductor Fang",
            activeTitle = "Modo traductor activo",
            exit = "Salir",
            instruction = "Toca cualquier mensaje para traducirlo",
            error = "No se pudo traducir. Toca para reintentar.",
        )
    }

private data class ChatTranslatorBoxUiState(
    val translation: TranslatorBoxState? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Portable Fang mode for `CHAT-TRANSLATION` / `FLOW-TRANSLATOR`.
 *
 * The shared Android visual contract is reproduced by this common root. Platform code only
 * supplies the HTTP transport; the frosted backdrop, copy, bubbles and interactions stay common.
 */
@Composable
fun ChatTranslatorOverlayContent(
    registry: QuataTranslatableTextRegistry,
    gateway: ChatTranslationGateway,
    initialDirection: ChatTranslationDirection,
    strings: ChatTranslatorStrings,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val states = remember { mutableStateMapOf<String, ChatTranslatorBoxUiState>() }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val boxes = registry.visibleBoxes
    val visibleIds = boxes.map { it.id }.toSet()
    LaunchedEffect(visibleIds) {
        states.keys.filter { it !in visibleIds }.forEach(states::remove)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.boundsInWindow().topLeft },
    ) {
        val density = LocalDensity.current
        val firstMessageTopPx = boxes.minOfOrNull { box ->
            (box.bounds.top - overlayOrigin.y).coerceAtLeast(0f)
        } ?: 0f
        val messageShiftPx = (
            with(density) { 148.dp.toPx() } - firstMessageTopPx
        ).coerceAtLeast(0f)
        QuataTranslatorBackdrop(background = null, frostedTexture = null, modifier = Modifier.fillMaxSize())
        boxes.forEach { box ->
            val left = with(density) { (box.bounds.left - overlayOrigin.x).coerceAtLeast(0f).toDp() }
            val top = with(density) {
                ((box.bounds.top - overlayOrigin.y).coerceAtLeast(0f) + messageShiftPx).toDp()
            }
            val width = with(density) { box.bounds.width.coerceAtLeast(64f).toDp() }.coerceAtMost(maxWidth)
            val height = with(density) { box.bounds.height.coerceAtLeast(48f).toDp() }.coerceAtMost(maxHeight)
            val state = states[box.id]
            val translated = state?.translation?.takeIf { it.showTranslation }?.translation
            TranslatorMessageSurface(
                displayText = box.displayText,
                originalText = box.text,
                translatedText = translated,
                directionLabel = state?.translation?.directionLabel,
                failedText = strings.error.takeIf { state?.failed == true },
                loading = state?.loading == true,
                modifier = Modifier
                    .offset(left, top)
                    .size(width, height)
                    .clickable(enabled = state?.loading != true) {
                        val existing = state?.translation
                        if (existing?.translation != null) {
                            states[box.id] = state.copy(translation = existing.copy(showTranslation = !existing.showTranslation))
                        } else {
                            states[box.id] = ChatTranslatorBoxUiState(loading = true)
                            scope.launch {
                                states[box.id] = runCatching { gateway.translate(box.text, initialDirection) }
                                    .fold(
                                        onSuccess = { ChatTranslatorBoxUiState(translation = it) },
                                        onFailure = { ChatTranslatorBoxUiState(failed = true) },
                                    )
                            }
                        }
                    },
            )
        }
        TranslatorModeHeader(
            strings = strings,
            onDismiss = onDismiss,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 24.dp, top = 26.dp, end = 24.dp),
        )
        TranslatorModeFooter(
            instruction = strings.instruction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp),
        )
    }
}

@Composable
private fun TranslatorMessageSurface(
    displayText: String,
    originalText: String,
    translatedText: String?,
    directionLabel: String?,
    failedText: String?,
    loading: Boolean,
    modifier: Modifier,
) {
    val template = quataTheme()
    val parts = remember(displayText, originalText, translatedText) {
        ChatTranslatorMessageParts.from(displayText, translatedText ?: originalText)
    }
    val translated = translatedText != null
    val bubbleColor = when {
        translated -> template.colors.accent.copy(alpha = 0.94f)
        loading -> template.colors.accent.copy(alpha = 0.18f)
        parts.isMine -> template.colors.chatMine
        else -> template.colors.chatOther
    }
    val textColor = if (parts.isMine || translated) template.colors.accentContent else template.colors.textPrimary
    Surface(
        color = bubbleColor,
        contentColor = textColor,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        parts.sender,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (translated && directionLabel != null) {
                        TranslatorDirectionBadge(directionLabel)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        parts.timestamp,
                        color = textColor.copy(alpha = 0.56f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = failedText ?: parts.message,
                    color = textColor,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.BottomEnd).size(16.dp),
                    color = template.colors.accent,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun TranslatorDirectionBadge(label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("T", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp, lineHeight = 10.sp)
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 10.sp)
    }
}

@Composable
private fun TranslatorModeHeader(
    strings: ChatTranslatorStrings,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(listOf(Color(0xFFFF6A00), Color(0xFFFF7F1A))),
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TranslatorSparkle()
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    strings.activeTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(strings.instruction, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(4.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = strings.exit }
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(22.dp).border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("X", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                Spacer(Modifier.width(6.dp))
                Text(strings.exit, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun TranslatorSparkle() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            SparkleDot(6)
            Spacer(Modifier.width(3.dp))
            SparkleDot(12)
        }
        Spacer(Modifier.height(2.dp))
        Row {
            SparkleDot(13)
            Spacer(Modifier.width(4.dp))
            SparkleDot(7)
        }
    }
}

@Composable
private fun SparkleDot(size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color.White))
}

@Composable
private fun TranslatorModeFooter(instruction: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.TouchApp,
            contentDescription = null,
            tint = Color(0xFFFF6A00),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            instruction,
            color = Color.White.copy(alpha = 0.88f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

private data class ChatTranslatorMessageParts(
    val isMine: Boolean,
    val sender: String,
    val timestamp: String,
    val message: String,
) {
    companion object {
        fun from(displayText: String, message: String): ChatTranslatorMessageParts {
            val header = displayText.lineSequence().firstOrNull().orEmpty().split(" | ", limit = 3)
            return ChatTranslatorMessageParts(
                isMine = header.getOrNull(0) == "mine",
                sender = header.getOrNull(1).orEmpty(),
                timestamp = header.getOrNull(2).orEmpty(),
                message = message,
            )
        }
    }
}
