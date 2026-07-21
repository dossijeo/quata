package com.quata.feature.whatsnew.presentation

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.R
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.whatsnew.domain.PendingRelease
import kotlinx.coroutines.launch

@Composable
fun WhatsNewScreen(
    releases: List<PendingRelease>,
    isCompleting: Boolean,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    padding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    require(releases.isNotEmpty())
    val pagerState = rememberPagerState(pageCount = releases::size)
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == releases.lastIndex
    val isLandscape = rememberQuataWindowLayoutInfo().isLandscape

    QuataScreen(padding = padding) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .then(
                    if (isLandscape) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars)
                ),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            Box(Modifier.fillMaxSize()) {
            IconButton(
                onClick = onDismiss,
                enabled = !isCompleting,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .semantics { contentDescription = "dismiss_whats_new" }
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrandMark()
                Text(
                    text = stringResource(R.string.whats_new_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 18.dp)
                )
                PageIndicator(
                    count = releases.size,
                    selectedIndex = currentPage,
                    modifier = Modifier.padding(top = 14.dp, bottom = 18.dp)
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    ReleasePage(
                        release = releases[page],
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(currentPage - 1, animationSpec = tween(260))
                            }
                        },
                        enabled = currentPage > 0 && !isCompleting,
                        colors = ButtonDefaults.outlinedButtonColors(),
                        modifier = Modifier.semantics { contentDescription = "previous_whats_new" }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.whats_new_previous), modifier = Modifier.padding(start = 6.dp))
                    }

                    Button(
                        onClick = {
                            if (isLastPage) {
                                onComplete()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(currentPage + 1, animationSpec = tween(260))
                                }
                            }
                        },
                        enabled = !isCompleting,
                        colors = ButtonDefaults.buttonColors(containerColor = QuataOrange),
                        modifier = Modifier.semantics { contentDescription = "next_whats_new" }
                    ) {
                        Text(
                            text = stringResource(
                                if (isLastPage) R.string.whats_new_continue else R.string.whats_new_next
                            )
                        )
                        if (!isLastPage) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(18.dp)
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun BrandMark() {
    Box(
        modifier = Modifier
            .size(58.dp)
            .background(QuataOrange, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("Q", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ReleasePage(release: PendingRelease, modifier: Modifier = Modifier) {
    val version = release.versionName?.takeIf { it.isNotBlank() } ?: release.versionCode.toString()
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.whats_new_version, version),
            style = MaterialTheme.typography.labelLarge,
            color = QuataOrange,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.whats_new_version_heading, version),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 20.dp)
        ) {
            Text(
                text = release.localizedNote,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp,
                modifier = Modifier.padding(22.dp)
            )
        }
    }
}

@Composable
private fun PageIndicator(count: Int, selectedIndex: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .size(if (index == selectedIndex) 22.dp else 8.dp, 8.dp)
                    .background(
                        color = if (index == selectedIndex) QuataOrange else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
            )
        }
    }
}

private val QuataOrange = Color(0xFFFF7416)
