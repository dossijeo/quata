package com.quata.feature.feed.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quata.core.ui.textCanvasBrush
import com.quata.core.ui.textCanvasTypography
import kotlinx.coroutines.flow.collectLatest

const val TextOnlyReelReadMoreTestTag = "feed.text-reader.open"
const val TextOnlyReelReaderTestTag = "feed.text-reader"
const val TextOnlyReelReaderScrollTestTag = "feed.text-reader.scroll"

data class TextOnlyReelReaderScrollAnchor(
    val stableId: String,
    val scrollOffsetPx: Int,
) {
    companion object {
        val Empty = TextOnlyReelReaderScrollAnchor("", 0)
        val Saver: Saver<TextOnlyReelReaderScrollAnchor, Any> = listSaver(
            save = { listOf(it.stableId, it.scrollOffsetPx) },
            restore = { TextOnlyReelReaderScrollAnchor(it[0] as String, it[1] as Int) },
        )
    }
}

internal fun textOnlyReelReaderInitialOffset(
    stableId: String,
    anchor: TextOnlyReelReaderScrollAnchor?,
): Int = anchor
    ?.takeIf { it.stableId == stableId }
    ?.scrollOffsetPx
    ?.coerceAtLeast(0)
    ?: 0

/** Portable visual body and reader for a text-only reel. The host owns localized text and close UI. */
@Composable
fun TextOnlyReelContent(
    stableId: String,
    displayText: String,
    seedText: String,
    patternId: String?,
    readMoreText: String,
    readerDismissButton: @Composable (Modifier, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = remember(displayText) { textCanvasTypography(displayText) }
    var hasOverflow by remember(stableId, displayText) { mutableStateOf(false) }
    var isReaderOpen by rememberSaveable(stableId) { mutableStateOf(false) }
    var readerScrollAnchor by rememberSaveable(stableId, stateSaver = TextOnlyReelReaderScrollAnchor.Saver) {
        mutableStateOf(TextOnlyReelReaderScrollAnchor.Empty)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(textCanvasBrush(seedText, patternId))
            .padding(horizontal = TextOnlyReelActionRailPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = displayText,
                color = Color.White,
                fontSize = typography.fontSize,
                lineHeight = typography.lineHeight,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = typography.maxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { hasOverflow = it.hasVisualOverflow }
            )
            if (hasOverflow) {
                Spacer(Modifier.height(18.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.36f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .testTag(TextOnlyReelReadMoreTestTag)
                        .clickable { isReaderOpen = true }
                ) {
                    Text(readMoreText, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp))
                }
            }
        }
    }
    if (isReaderOpen) {
        val readerScrollState = rememberScrollState(textOnlyReelReaderInitialOffset(stableId, readerScrollAnchor))
        val closeReader = {
            readerScrollAnchor = TextOnlyReelReaderScrollAnchor(stableId, readerScrollState.value)
            isReaderOpen = false
        }
        LaunchedEffect(stableId, readerScrollState) {
            snapshotFlow { readerScrollState.value }.collectLatest {
                readerScrollAnchor = TextOnlyReelReaderScrollAnchor(stableId, it)
            }
        }
        Dialog(
            onDismissRequest = closeReader,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(textCanvasBrush(seedText, patternId))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp)
                    .testTag(TextOnlyReelReaderTestTag)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .verticalScroll(readerScrollState)
                        .padding(top = 56.dp, bottom = 24.dp)
                        .testTag(TextOnlyReelReaderScrollTestTag),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = displayText,
                        color = Color.White,
                        fontSize = 24.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }
                readerDismissButton(Modifier.align(Alignment.TopEnd), closeReader)
            }
        }
    }
}

private val TextOnlyReelActionRailPadding = 92.dp
