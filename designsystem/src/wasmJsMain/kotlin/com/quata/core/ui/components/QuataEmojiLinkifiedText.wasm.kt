package com.quata.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import org.jetbrains.compose.resources.imageResource

@Composable
actual fun QuataEmojiLinkifiedText(value: String, color: Color, linkColor: Color, fontSize: TextUnit, lineHeight: TextUnit, maxLines: Int, overflow: TextOverflow, fontWeight: FontWeight?, onOpenLink: (String) -> Unit, modifier: Modifier) {
    val annotated = remember(value, linkColor) { quataEmojiLinkifiedAnnotatedString(value, linkColor) }
    val inlineContent = rememberQuataEmojiInlineContent(value)
    val ids = remember(value) { quataEmojiInlineIdsInOrder(value) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val uniqueIds = remember(ids) { ids.distinct() }
    val fixedImages = uniqueIds.mapNotNull { id -> quataFeedEmojiResource(id)?.let { id to imageResource(it) } }.toMap()
    val atlasImages = uniqueIds.mapNotNull { id -> quataCommunityEmojiEntry(id)?.let { entry -> id to imageResource(communityEmojiAtlas(entry.sectionKey).resource) } }.toMap()
    Box(modifier.pointerInput(annotated) {
        detectTapGestures { position ->
            val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
            annotated.getStringAnnotations(QuataLinkAnnotationTag, offset, offset).firstOrNull()?.item?.let(onOpenLink)
        }
    }) {
        Text(annotated, inlineContent = inlineContent, color = color, fontSize = fontSize, lineHeight = lineHeight, maxLines = maxLines, overflow = overflow, fontWeight = fontWeight, onTextLayout = { layout = it })
        val rects = layout?.placeholderRects.orEmpty()
        if (layout != null) Canvas(Modifier.matchParentSize()) {
            ids.zip(rects).forEach { (id, rect) ->
                if (rect == null) return@forEach
                val fixed = fixedImages[id]
                if (fixed != null) {
                    drawImage(fixed, dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()), dstSize = IntSize(rect.width.toInt(), rect.height.toInt()))
                } else quataCommunityEmojiEntry(id)?.let { entry ->
                    val atlas = communityEmojiAtlas(entry.sectionKey)
                    val image: ImageBitmap = atlasImages.getValue(id)
                    drawImage(image, srcOffset = IntOffset((entry.index % atlas.columns) * atlas.cellPx, (entry.index / atlas.columns) * atlas.cellPx), srcSize = IntSize(atlas.cellPx, atlas.cellPx), dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()), dstSize = IntSize(rect.width.toInt(), rect.height.toInt()))
                }
            }
        }
    }
}
