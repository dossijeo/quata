package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.PostComment

@Composable
fun QuataReplyTargetBannerContent(comment: PostComment, replyingTo: String, cancelDescription: String, onClear: () -> Unit) {
    val template = quataTheme()
    Surface(color = template.colors.accent.copy(alpha = .08f), contentColor = template.colors.textPrimary, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().border(1.dp, template.colors.accent.copy(alpha = .34f), RoundedCornerShape(18.dp))) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(replyingTo, color = template.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp)); Text(comment.message, color = template.colors.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CompactIconButton(onClick = onClear, modifier = Modifier.size(42.dp).background(template.colors.surfaceAlt, CircleShape)) { CompactIcon(Icons.Filled.Close, cancelDescription) }
        }
    }
}
