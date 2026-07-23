package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.CompactIcon

data class ProfileModerationStrings(
    val report: String,
    val block: String
)

/** Platform-neutral entry points; confirmation and mutation stay with the host. */
@Composable
fun ProfileModerationActions(
    visible: Boolean,
    strings: ProfileModerationStrings,
    onReport: () -> Unit,
    onBlock: () -> Unit
) {
    if (!visible) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        TextButton(onClick = onReport, modifier = Modifier.weight(1f)) {
            CompactIcon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.report)
        }
        TextButton(onClick = onBlock, modifier = Modifier.weight(1f)) {
            CompactIcon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.block)
        }
    }
}
