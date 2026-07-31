package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource
import quata.designsystem.generated.resources.Res
import quata.designsystem.generated.resources.quata_community_emoji_atlas_animals_nature
import quata.designsystem.generated.resources.quata_community_emoji_atlas_flags
import quata.designsystem.generated.resources.quata_community_emoji_atlas_food_drink
import quata.designsystem.generated.resources.quata_community_emoji_atlas_frequent
import quata.designsystem.generated.resources.quata_community_emoji_atlas_gestures
import quata.designsystem.generated.resources.quata_community_emoji_atlas_objects_symbols
import quata.designsystem.generated.resources.quata_community_emoji_atlas_people
import quata.designsystem.generated.resources.quata_community_emoji_atlas_recent

data class QuataEmojiSection(val key: String, val label: String, val emojis: List<String>)

@Composable
fun CommunityEmojiPanelContent(
    sections: List<QuataEmojiSection>,
    onEmojiClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialSectionKey: String = "frequent",
    gridMaxHeight: Dp = 220.dp
) {
    if (sections.isEmpty()) return
    val template = quataTheme()
    var selectedSectionKey by remember { mutableStateOf(initialSectionKey) }
    val selectedSection = sections.firstOrNull { it.key == selectedSectionKey } ?: sections.first()
    Surface(
        color = template.colors.surfaceRaised,
        contentColor = template.colors.textPrimary,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth().border(1.dp, template.colors.accent.copy(alpha = .62f), RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(14.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sections) { section ->
                    Surface(
                        color = if (section.key == selectedSectionKey) template.colors.accent else Color.Transparent,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.clickable { selectedSectionKey = section.key }
                    ) {
                        Text(section.label, color = if (section.key == selectedSectionKey) template.colors.accentContent else template.colors.textSecondary, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Compose resources resolves only the selected atlas; inactive sections are not decoded.
            val selectedAtlasLayout = communityEmojiAtlas(selectedSection.key)
            val selectedAtlas = imageResource(selectedAtlasLayout.resource)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 44.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = gridMaxHeight),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(selectedSection.emojis) { index, emoji ->
                    Box(
                        Modifier.size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(template.colors.surfaceAlt)
                            .border(1.dp, template.colors.divider, RoundedCornerShape(14.dp))
                            .clickable { onEmojiClick(emoji) },
                        contentAlignment = Alignment.Center,
                    ) {
                        CommunityEmojiAtlasCell(selectedAtlas, selectedAtlasLayout, index)
                    }
                }
            }
        }
    }
}

/** A 72 px Noto PNG atlas is the common source of picker artwork on Android, Wasm and iOS. */
internal data class CommunityEmojiAtlas(
    val resource: DrawableResource,
    val emojiCount: Int,
    val columns: Int = 6,
    val cellPx: Int = 72,
)

/** Kept separate from the composable so common tests can prove every catalog entry has a cell. */
internal fun communityEmojiAtlasCoordinates(sectionKey: String, index: Int): Pair<Int, Int> {
    val atlas = communityEmojiAtlas(sectionKey)
    require(index >= 0) { "Emoji atlas index must not be negative: $index" }
    require(index < atlas.emojiCount) { "Emoji atlas index $index exceeds ${atlas.emojiCount} cells for $sectionKey" }
    return index % atlas.columns to index / atlas.columns
}

internal fun communityEmojiAtlas(sectionKey: String): CommunityEmojiAtlas = when (sectionKey) {
    "recent" -> CommunityEmojiAtlas(Res.drawable.quata_community_emoji_atlas_recent, emojiCount = 24)
    "frequent" -> CommunityEmojiAtlas(Res.drawable.quata_community_emoji_atlas_frequent, emojiCount = 45)
    "gestures" -> CommunityEmojiAtlas(Res.drawable.quata_community_emoji_atlas_gestures, emojiCount = 35)
    "people" -> CommunityEmojiAtlas(Res.drawable.quata_community_emoji_atlas_people, emojiCount = 34)
    "animals_nature" -> CommunityEmojiAtlas(Res.drawable.quata_community_emoji_atlas_animals_nature, emojiCount = 58)
    "food_drink" -> CommunityEmojiAtlas(Res.drawable.quata_community_emoji_atlas_food_drink, emojiCount = 57)
    "objects_symbols" -> CommunityEmojiAtlas(Res.drawable.quata_community_emoji_atlas_objects_symbols, emojiCount = 51)
    "flags" -> CommunityEmojiAtlas(Res.drawable.quata_community_emoji_atlas_flags, emojiCount = 34)
    else -> error("Unknown community emoji atlas section: $sectionKey")
}

@Composable
private fun CommunityEmojiAtlasCell(image: ImageBitmap, atlas: CommunityEmojiAtlas, index: Int) {
    val (column, row) = index % atlas.columns to index / atlas.columns
    Canvas(Modifier.size(32.dp)) {
        drawImage(
            image = image,
            srcOffset = IntOffset(column * atlas.cellPx, row * atlas.cellPx),
            srcSize = IntSize(atlas.cellPx, atlas.cellPx),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}
