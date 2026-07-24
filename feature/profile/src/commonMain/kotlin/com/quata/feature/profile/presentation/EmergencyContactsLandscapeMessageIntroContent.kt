package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

/** Shared explanatory header displayed above the landscape SOS message panel. */
@Composable
fun EmergencyContactsLandscapeMessageIntroContent(
    tabLabel: String,
    description: String,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Column(modifier = modifier) {
        Text(tabLabel, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            color = template.colors.textSecondary,
            lineHeight = 18.sp,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
    }
}
