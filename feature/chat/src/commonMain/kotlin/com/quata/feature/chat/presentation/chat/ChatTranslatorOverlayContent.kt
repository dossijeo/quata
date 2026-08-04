package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val title: String,
    val close: String,
    val direction: String,
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
            title = "Traducteur Fang", close = "Fermer", direction = "Sens",
            instruction = "Touchez un message pour le traduire.", error = "Traduction impossible. Touchez pour réessayer.",
        )
        "en" -> ChatTranslatorStrings(
            title = "Fang translator", close = "Close", direction = "Direction",
            instruction = "Tap a message to translate it.", error = "Translation failed. Tap to retry.",
        )
        else -> ChatTranslatorStrings(
            title = "Traductor Fang", close = "Cerrar", direction = "Dirección",
            instruction = "Toca un mensaje para traducirlo.", error = "No se pudo traducir. Toca para reintentar.",
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
 * The live common Chat remains visible under this overlay. Registered bubble bounds drive the
 * tappable translation surfaces, while platform code supplies only the HTTP transport.
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
    var direction by remember(initialDirection) { mutableStateOf(initialDirection) }
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
        QuataTranslatorBackdrop(background = null, frostedTexture = null, modifier = Modifier.fillMaxSize())
        boxes.forEach { box ->
            val left = with(density) { (box.bounds.left - overlayOrigin.x).coerceAtLeast(0f).toDp() }
            val top = with(density) { (box.bounds.top - overlayOrigin.y).coerceAtLeast(0f).toDp() }
            val width = with(density) { box.bounds.width.coerceAtLeast(64f).toDp() }.coerceAtMost(maxWidth)
            val height = with(density) { box.bounds.height.coerceAtLeast(48f).toDp() }.coerceAtMost(maxHeight)
            val state = states[box.id]
            val translated = state?.translation?.takeIf { it.showTranslation }?.translation
            Surface(
                color = if (translated != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                contentColor = if (translated != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(20.dp),
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
                                states[box.id] = runCatching { gateway.translate(box.text, direction) }
                                    .fold(
                                        onSuccess = { ChatTranslatorBoxUiState(translation = it) },
                                        onFailure = { ChatTranslatorBoxUiState(failed = true) },
                                    )
                            }
                        }
                    },
            ) {
                Box(Modifier.fillMaxSize().padding(12.dp)) {
                    Text(
                        text = when {
                            state?.failed == true -> strings.error
                            translated != null -> translated
                            else -> box.text
                        },
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    if (state?.loading == true) {
                        CircularProgressIndicator(Modifier.align(Alignment.BottomEnd).size(18.dp), strokeWidth = 2.dp)
                    }
                    state?.translation?.directionLabel?.takeIf { translated != null }?.let { label ->
                        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(strings.title, fontWeight = FontWeight.Black)
                    Text(strings.instruction, style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = {
                    direction = direction.reversed()
                    states.clear()
                }) { Text("${strings.direction}: ${direction.label}") }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, strings.close) }
            }
        }
        Text(
            text = strings.instruction,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .padding(14.dp),
        )
    }
}
