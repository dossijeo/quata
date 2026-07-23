package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme
import com.quata.feature.neighborhoods.domain.NeighborhoodUser

data class ProfileRoleStrings(val title: String, val admin: String, val official: String)

@Composable
fun ProfileRoleControlsContent(
    user: NeighborhoodUser,
    isUpdating: Boolean,
    strings: ProfileRoleStrings,
    onSetRoles: (Boolean, Boolean) -> Unit
) {
    val template = quataTheme()
    Card(colors = CardDefaults.cardColors(containerColor = template.colors.surfaceAlt), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(strings.title, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (isUpdating) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = QuataOrange)
            }
            RoleSwitchRow(strings.admin, user.isAdmin, !isUpdating) { onSetRoles(it, user.isOfficial) }
            RoleSwitchRow(strings.official, user.isOfficial, !isUpdating) { onSetRoles(user.isAdmin, it) }
        }
    }
}

@Composable
private fun RoleSwitchRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}
