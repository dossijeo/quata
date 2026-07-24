package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.compactButtonMinSize

data class ProfileManagementAction(val label: String, val onClick: () -> Unit)

/** Shared account-management section; the host provides its navigation/back affordance. */
@Composable
fun ProfileAccountManagementContent(
    title: String,
    description: String,
    descriptionColor: Color,
    actions: List<ProfileManagementAction>,
    backButton: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            backButton()
            Spacer(Modifier.width(6.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(description, color = descriptionColor)
        actions.forEach { action ->
            OutlinedButton(
                onClick = action.onClick,
                modifier = Modifier.fillMaxWidth().compactButtonMinSize(),
                shape = RoundedCornerShape(9.dp),
                contentPadding = CompactButtonContentPadding
            ) { Text(action.label) }
        }
    }
}
