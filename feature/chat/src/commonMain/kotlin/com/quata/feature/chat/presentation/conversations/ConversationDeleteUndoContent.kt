package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.compactButtonMinSize

/** Shared transient control that restores a just-deleted conversation. */
@Composable
fun ConversationDeleteUndoContent(
    title: String,
    undoLabel: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    Surface(color = template.colors.surfaceRaised, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = onUndo,
                colors = ButtonDefaults.buttonColors(containerColor = template.colors.accent, contentColor = template.colors.accentContent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(32.dp).compactButtonMinSize(),
                contentPadding = CompactButtonContentPadding,
            ) {
                Text(undoLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}
