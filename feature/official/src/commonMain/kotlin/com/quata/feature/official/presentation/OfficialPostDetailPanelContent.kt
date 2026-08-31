package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.QuataResolvedTheme
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.QuataFloatingPanelContent

const val OfficialPostDetailPanelTestTag = "official.detail.panel"
const val OfficialPostDetailCloseTestTag = "official.detail.panel.close"
const val OfficialPostDetailArticleTestTag = "official.detail.article"
const val OfficialPostDetailMediaTestTag = "official.detail.media"
const val OfficialPostDetailLinkTestTag = "official.detail.link"
const val OfficialPostDetailProfileTestTag = "official.detail.profile"

/**
 * Shared detail panel shell. Hosts inject avatar/author, media, external resources and
 * navigation actions; HTML rendering and platform link behavior remain platform-owned.
 * The responsive panel hierarchy itself stays portable.
 */
@Composable
fun OfficialPostDetailPanelContent(
    title: String,
    closeLabel: String,
    link: String?,
    onDismiss: () -> Unit,
    articleContent: @Composable (Modifier) -> Unit,
    author: (@Composable (Modifier) -> Unit)? = null,
    media: (@Composable (Modifier) -> Unit)? = null,
    resourceContent: (@Composable (Modifier) -> Unit)? = null,
    navigationContent: (@Composable (Modifier) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    QuataFloatingPanelContent(
        onDismiss = onDismiss,
        modifier = modifier
            .testTag(OfficialPostDetailPanelTestTag)
            .semantics { contentDescription = OfficialPostDetailPanelTestTag },
        template = template,
        landscapeHeightFraction = 0.86f,
        landscapeVerticalOffset = (-24).dp,
    ) { panelModifier, isLandscape ->
        Column(
            modifier = panelModifier.padding(
                start = 18.dp,
                top = if (isLandscape) 18.dp else 10.dp,
                end = 18.dp,
                bottom = if (isLandscape) 18.dp else 48.dp,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .testTag(OfficialPostDetailCloseTestTag)
                        .semantics { contentDescription = OfficialPostDetailCloseTestTag },
                ) { Text(closeLabel) }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                author?.let { authorSlot ->
                    item { authorSlot(Modifier.fillMaxWidth()) }
                }
                media?.let { mediaSlot ->
                    item {
                        mediaSlot(
                            Modifier
                                .fillMaxWidth()
                                .height(224.dp)
                                .testTag(OfficialPostDetailMediaTestTag)
                                .semantics { contentDescription = OfficialPostDetailMediaTestTag },
                        )
                    }
                }
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .testTag(OfficialPostDetailArticleTestTag)
                            .semantics { contentDescription = OfficialPostDetailArticleTestTag },
                    ) {
                        articleContent(Modifier.fillMaxWidth())
                    }
                }
                when {
                    resourceContent != null -> item {
                        resourceContent(
                            Modifier
                                .fillMaxWidth()
                                .testTag(OfficialPostDetailLinkTestTag)
                                .semantics { contentDescription = OfficialPostDetailLinkTestTag },
                        )
                    }
                    link?.isNotBlank() == true -> item {
                        Text(
                            link,
                            color = if (template.resolvedTheme == QuataResolvedTheme.Dark) Color(0xFF2EA7FF) else Color(0xFF17954B),
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .testTag(OfficialPostDetailLinkTestTag)
                                .semantics { contentDescription = OfficialPostDetailLinkTestTag },
                        )
                    }
                }
                navigationContent?.let { navigationSlot ->
                    item {
                        navigationSlot(
                            Modifier
                                .fillMaxWidth()
                                .testTag(OfficialPostDetailProfileTestTag)
                                .semantics { contentDescription = OfficialPostDetailProfileTestTag },
                        )
                    }
                }
            }
        }
    }
}
