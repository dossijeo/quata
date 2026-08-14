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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.CompactIcon

data class ProfileModerationStrings(
    val report: String,
    val block: String,
    val unblock: String,
)

/** Platform-neutral entry points; confirmation and mutation stay with the host. */
@Composable
fun ProfileModerationActions(
    userId: String,
    visible: Boolean,
    isBlocked: Boolean,
    isUpdating: Boolean,
    strings: ProfileModerationStrings,
    onReport: () -> Unit,
    onBlock: () -> Unit
) {
    if (!visible) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = PublicProfileModerationRootTestTagPrefix + userId },
    ) {
        TextButton(
            onClick = onReport,
            enabled = !isUpdating,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    testTag = PublicProfileModerationReportTestTagPrefix + userId
                    contentDescription = PublicProfileModerationReportTestTagPrefix + userId
                },
        ) {
            CompactIcon(Icons.Filled.Flag, contentDescription = strings.report, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.report)
        }
        val blockTag = if (isBlocked) {
            PublicProfileModerationUnblockTestTagPrefix + userId
        } else {
            PublicProfileModerationBlockTestTagPrefix + userId
        }
        TextButton(
            onClick = onBlock,
            enabled = !isUpdating,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    testTag = blockTag
                    contentDescription = blockTag
                },
        ) {
            CompactIcon(Icons.Filled.Close, contentDescription = if (isBlocked) strings.unblock else strings.block, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (isBlocked) strings.unblock else strings.block)
        }
    }
}
