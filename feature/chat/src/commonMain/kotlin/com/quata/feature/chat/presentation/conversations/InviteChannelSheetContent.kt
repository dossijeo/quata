package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.platform.ClipboardService
import kotlinx.coroutines.launch

/** Platform-neutral representation of an invitation destination. */
data class InviteChannelTargetUi(
    val id: String,
    val label: String,
)

data class InviteChannelSheetStrings(
    val shareTextTitle: String,
    val copyMessage: String,
    val chooseAppFor: String,
)

/**
 * Portable invitation-channel panel. Hosts supply platform target discovery, their icons and
 * launch behavior; the message/copy affordance and responsive panel body are common.
 */
@Composable
fun InviteChannelSheetContent(
    invitationMessage: String,
    targets: List<InviteChannelTargetUi>,
    strings: InviteChannelSheetStrings,
    clipboardService: ClipboardService,
    onDismiss: () -> Unit,
    onTargetSelected: (InviteChannelTargetUi) -> Unit,
    panelHost: @Composable (@Composable (Modifier) -> Unit) -> Unit,
    targetIcon: @Composable (InviteChannelTargetUi, Modifier) -> Unit = { _, modifier ->
        Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = modifier.padding(14.dp))
    },
) {
    val template = quataTheme()
    val scope = rememberCoroutineScope()
    panelHost { panelModifier ->
        Column(panelModifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                strings.shareTextTitle,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = template.colors.textPrimary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Surface(
                color = template.colors.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        invitationMessage,
                        color = template.colors.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 14.dp, top = 11.dp, end = 58.dp, bottom = 11.dp),
                    )
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = strings.copyMessage,
                        tint = template.colors.textSecondary,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clip(CircleShape)
                            .clickable { scope.launch { clipboardService.writeText(invitationMessage) } }
                            .padding(12.dp)
                            .size(24.dp),
                    )
                }
            }
            HorizontalDivider(color = template.colors.divider, modifier = Modifier.padding(vertical = 16.dp))
            Text(
                strings.chooseAppFor,
                fontSize = 14.sp,
                color = template.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
            ) {
                items(targets, key = InviteChannelTargetUi::id) { target ->
                    InviteChannelTargetItemContent(
                        target = target,
                        onClick = { onTargetSelected(target) },
                        icon = { modifier -> targetIcon(target, modifier) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteChannelTargetItemContent(
    target: InviteChannelTargetUi,
    onClick: () -> Unit,
    icon: @Composable (Modifier) -> Unit,
) {
    val template = quataTheme()
    Column(
        modifier = Modifier
            .width(86.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = template.colors.surface, modifier = Modifier.size(58.dp)) {
            Box(contentAlignment = Alignment.Center) { icon(Modifier.size(58.dp)) }
        }
        Text(
            target.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = template.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
