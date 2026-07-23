package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataThemeTemplate
import com.quata.core.designsystem.theme.quataTheme

@Composable
fun QuataLiveRankingPanel(
    items: List<QuataLiveRankingItem>,
    onDismiss: () -> Unit,
    onOpenItem: (String) -> Unit
) {
    val template = quataTheme()
    QuataStandardFloatingPanel(
        onDismiss = onDismiss,
        template = template
    ) { panelModifier, isLandscape ->
        QuataLiveRankingPanelContent(
            items = items,
            isLandscape = isLandscape,
            strings = QuataLiveRankingStrings(
                title = stringResource(R.string.feed_live_title),
                subtitle = stringResource(R.string.feed_live_subtitle),
                monitoredPosts = stringResource(R.string.feed_live_posts_monitored, items.size),
                updated = stringResource(R.string.feed_live_updated),
                live = stringResource(R.string.common_live),
                close = stringResource(R.string.common_close),
                openPost = stringResource(R.string.feed_open_post)
            ),
            avatar = { item -> AvatarImage(name = item.avatarName, avatarUrl = item.avatarUrl, profileId = item.profileId, isOfficial = item.isOfficial, modifier = Modifier.size(44.dp)) },
            onDismiss = onDismiss,
            onOpenItem = onOpenItem,
            modifier = panelModifier
        )
    }
}
