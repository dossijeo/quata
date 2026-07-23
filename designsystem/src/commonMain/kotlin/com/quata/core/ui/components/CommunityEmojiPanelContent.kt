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
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 44.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = gridMaxHeight),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedSection.emojis) { emoji ->
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(template.colors.surfaceAlt).border(1.dp, template.colors.divider, RoundedCornerShape(14.dp)).clickable { onEmojiClick(emoji) }, contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) }
                }
            }
        }
    }
}
