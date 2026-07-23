package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.textCanvasBrush
import com.quata.core.ui.textCanvasPatterns

@Composable
fun TextPatternSelectorContent(
    selectedPatternId: String,
    title: String,
    onPatternSelected: (String) -> Unit
) {
    val template = quataTheme()
    var isDialogOpen by rememberSaveable { mutableStateOf(false) }
    val selectedPattern = remember(selectedPatternId) {
        textCanvasPatterns.firstOrNull { it.id == selectedPatternId } ?: textCanvasPatterns.first()
    }
    Surface(
        color = template.colors.surface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable { isDialogOpen = true }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextPatternSwatch(selectedPattern.id, Modifier.size(54.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = template.colors.textPrimary, fontWeight = FontWeight.ExtraBold)
                Text(selectedPattern.label, color = template.colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (isDialogOpen) {
        Dialog(onDismissRequest = { isDialogOpen = false }) {
            Surface(color = template.colors.surface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, color = template.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    textCanvasPatterns.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { pattern ->
                                TextPatternChoice(pattern.label, pattern.id, selectedPattern.id == pattern.id, {
                                    onPatternSelected(pattern.id)
                                    isDialogOpen = false
                                }, Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextPatternChoice(label: String, patternId: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val template = quataTheme()
    Column(
        modifier = modifier.border(2.dp, if (isSelected) template.colors.accent else template.colors.divider, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextPatternSwatch(patternId, Modifier.fillMaxWidth().height(68.dp))
        Text(label, color = template.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TextPatternSwatch(patternId: String, modifier: Modifier = Modifier) {
    Box(modifier.background(textCanvasBrush(seedText = null, patternId = patternId), RoundedCornerShape(12.dp)))
}
