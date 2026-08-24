package com.quata.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

const val QuataCommentsPanelErrorTestTag = "comments.error"

@Composable
fun QuataCommentsPanelErrorContent(
    message: String,
    modifier: Modifier = Modifier,
    testTag: String = QuataCommentsPanelErrorTestTag,
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surfaceRaised,
        contentColor = template.colors.error,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics { contentDescription = message }
            .border(1.dp, template.colors.error.copy(alpha = 0.55f), RoundedCornerShape(14.dp)),
    ) {
        Text(
            text = message,
            color = template.colors.error,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
