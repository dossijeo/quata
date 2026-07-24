package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quata.core.text.cleanTextCanvasSeedBody
import com.quata.core.ui.textCanvasBrush
import com.quata.core.ui.textCanvasTypography

/**
 * Portable text-post canvas and expanded reader. The host owns its preview frame and
 * supplies the dismiss affordance so platform-specific icon/resource policies stay out of commonMain.
 */
@Composable
fun ComposerTextCanvasContent(
    text: String,
    patternId: String?,
    compact: Boolean,
    emptyText: String,
    readMoreText: String,
    readerDismissButton: @Composable (modifier: Modifier, onDismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val seedText = if (patternId == null) remember(text) { text.cleanTextCanvasSeedBody() } else null
    val displayText = text.ifBlank { emptyText }
    val typography = remember(displayText, compact) { textCanvasTypography(displayText, compact = compact) }
    var hasOverflow by remember(text, compact) { mutableStateOf(false) }
    var isReaderOpen by rememberSaveable(text) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(textCanvasBrush(seedText, patternId))
            .padding(horizontal = ComposerTextCanvasActionRailPadding),
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
            if (text.isNotBlank() && hasOverflow) {
                Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.36f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable { isReaderOpen = true }
                ) {
                    Text(
                        text = readMoreText,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 12.sp else 14.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }

    if (isReaderOpen) {
        Dialog(
            onDismissRequest = { isReaderOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(textCanvasBrush(seedText, patternId))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 56.dp, bottom = 24.dp),
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
                readerDismissButton(Modifier.align(Alignment.TopEnd)) { isReaderOpen = false }
            }
        }
    }
}

private val ComposerTextCanvasActionRailPadding = 78.dp
