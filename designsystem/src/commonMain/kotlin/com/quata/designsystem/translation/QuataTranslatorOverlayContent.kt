package com.quata.designsystem.translation

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
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.language.FangOverlayTranslationUseCase
import com.quata.core.language.TextTranslator
import com.quata.core.language.TextLanguageIdentifier
import com.quata.core.language.TranslatorBoxState
import com.quata.core.localization.QuataLanguage
import kotlinx.coroutines.launch

fun interface QuataTranslatorGateway {
    suspend fun translate(text: String): TranslatorBoxState?
}

class FangTextTranslatorGateway(
    identifier: TextLanguageIdentifier,
    translator: TextTranslator,
    preferredLanguage: QuataLanguage,
) : QuataTranslatorGateway {
    private val useCase = FangOverlayTranslationUseCase(
        identifier = identifier,
        translator = translator,
        preferredLanguage = { preferredLanguage },
    )

    override suspend fun translate(text: String): TranslatorBoxState? = useCase.translate(text)
}

data class QuataTranslatorStrings(
    val contentDescription: String,
    val activeTitle: String,
    val exit: String,
    val instruction: String,
    val error: String,
)

fun quataTranslatorPreferredLanguage(languageTag: String?): QuataLanguage =
    when (languageTag?.substringBefore('-')?.substringBefore('_')?.lowercase()) {
        "fr" -> QuataLanguage.French
        "en" -> QuataLanguage.English
        else -> QuataLanguage.Spanish
    }

fun quataTranslatorStringsForLanguage(languageTag: String?): QuataTranslatorStrings =
    when (languageTag?.substringBefore('-')?.substringBefore('_')?.lowercase()) {
        "fr" -> QuataTranslatorStrings(
            contentDescription = "Traducteur Fang",
            activeTitle = "Mode traducteur actif",
            exit = "Quitter",
            instruction = "Touchez un commentaire pour le traduire",
            error = "Traduction impossible. Touchez pour reessayer.",
        )
        "en" -> QuataTranslatorStrings(
            contentDescription = "Fang translator",
            activeTitle = "Translator mode active",
            exit = "Exit",
            instruction = "Tap any comment to translate it",
            error = "Translation failed. Tap to retry.",
        )
        else -> QuataTranslatorStrings(
            contentDescription = "Traductor Fang",
            activeTitle = "Modo traductor activo",
            exit = "Salir",
            instruction = "Toca cualquier comentario para traducirlo",
            error = "No se pudo traducir. Toca para reintentar.",
        )
    }

private data class TranslatorBoxUiState(
    val translation: TranslatorBoxState? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

@Composable
fun QuataTranslatorOverlayContent(
    registry: QuataTranslatableTextRegistry,
    gateway: QuataTranslatorGateway,
    strings: QuataTranslatorStrings,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        QuataTranslatorOverlaySurface(
            registry = registry,
            gateway = gateway,
            strings = strings,
            onDismiss = onDismiss,
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun QuataTranslatorOverlaySurface(
    registry: QuataTranslatableTextRegistry,
    gateway: QuataTranslatorGateway,
    strings: QuataTranslatorStrings,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val states = remember { mutableStateMapOf<String, TranslatorBoxUiState>() }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val boxes = registry.visibleBoxes
    val visibleIds = boxes.map { it.id }.toSet()
    LaunchedEffect(visibleIds) {
        states.keys.filter { it !in visibleIds }.forEach(states::remove)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .consumeTranslatorGestures()
            .onGloballyPositioned { overlayOrigin = it.boundsInWindow().topLeft },
    ) {
        val density = LocalDensity.current
        val firstTopPx = boxes.minOfOrNull { (it.bounds.top - overlayOrigin.y).coerceAtLeast(0f) } ?: 0f
        val shiftPx = (with(density) { 148.dp.toPx() } - firstTopPx).coerceAtLeast(0f)
        QuataTranslatorBackdrop(background = null, modifier = Modifier.fillMaxSize())
        boxes.forEach { box ->
            val left = with(density) { (box.bounds.left - overlayOrigin.x).coerceAtLeast(0f).toDp() }
            val top = with(density) { ((box.bounds.top - overlayOrigin.y).coerceAtLeast(0f) + shiftPx).toDp() }
            val width = with(density) { box.bounds.width.coerceAtLeast(64f).toDp() }.coerceAtMost(maxWidth)
            val height = with(density) { box.bounds.height.coerceAtLeast(48f).toDp() }.coerceAtMost(maxHeight)
            val state = states[box.id]
            val translated = state?.translation?.takeIf { it.showTranslation }?.translation
            TranslatorTextSurface(
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
                            states[box.id] = TranslatorBoxUiState(loading = true)
                            scope.launch {
                                states[box.id] = runCatching { gateway.translate(box.text) }
                                    .fold(
                                        onSuccess = { translated ->
                                            translated?.let { TranslatorBoxUiState(translation = it) }
                                                ?: TranslatorBoxUiState(failed = true)
                                        },
                                        onFailure = { TranslatorBoxUiState(failed = true) },
                                    )
                            }
                        }
                    },
            )
        }
        TranslatorModeHeader(strings, onDismiss, Modifier.align(Alignment.TopCenter).padding(start = 24.dp, top = 26.dp, end = 24.dp))
        TranslatorModeFooter(strings.instruction, Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp))
    }
}

private fun Modifier.consumeTranslatorGestures(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { change -> change.consume() }
            }
        }
    }

@Composable
private fun TranslatorTextSurface(
    displayText: String,
    originalText: String,
    translatedText: String?,
    directionLabel: String?,
    failedText: String?,
    loading: Boolean,
    modifier: Modifier,
) {
    val template = quataTheme()
    val lines = remember(displayText, originalText, translatedText, failedText) {
        val visibleText = when {
            translatedText != null -> displayText.replaceFirst(originalText, translatedText)
            failedText != null -> failedText
            else -> displayText
        }
        visibleText.lineSequence().filter(String::isNotBlank).toList()
    }
    val translated = translatedText != null
    val bubbleColor = when {
        translated -> template.colors.accent.copy(alpha = 0.94f)
        loading -> template.colors.accent.copy(alpha = 0.18f)
        else -> template.colors.surface.copy(alpha = 0.94f)
    }
    val textColor = if (translated) template.colors.accentContent else template.colors.textPrimary
    Surface(color = bubbleColor, contentColor = textColor, shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lines.firstOrNull().orEmpty(), color = textColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (translated && directionLabel != null) TranslatorDirectionBadge(directionLabel)
                }
                val body = lines.drop(1).joinToString("\n")
                if (body.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(body, color = textColor, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                }
            }
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.BottomEnd).size(16.dp), color = template.colors.accent, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun TranslatorDirectionBadge(label: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.22f)).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("T", color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp, lineHeight = 10.sp)
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 10.sp)
    }
}

@Composable
private fun TranslatorModeHeader(strings: QuataTranslatorStrings, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = Color.Transparent, shape = RoundedCornerShape(18.dp), shadowElevation = 10.dp) {
        Row(
            modifier = Modifier.background(Brush.linearGradient(listOf(Color(0xFFFF6A00), Color(0xFFFF7F1A))), RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TranslatorSparkle()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(strings.activeTitle, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(strings.instruction, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onDismiss).semantics { contentDescription = strings.exit }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(26.dp).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { SparkleDot(8); SparkleDot(15) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { SparkleDot(16); SparkleDot(9) }
    }
}

@Composable
private fun SparkleDot(size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color.White))
}

@Composable
private fun TranslatorModeFooter(instruction: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.TouchApp, contentDescription = null, tint = Color(0xFFFF6A00), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(instruction, color = Color.White.copy(alpha = 0.88f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
