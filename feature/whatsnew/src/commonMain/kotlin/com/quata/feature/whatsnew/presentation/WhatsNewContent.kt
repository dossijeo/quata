package com.quata.feature.whatsnew.presentation

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.feature.whatsnew.domain.PendingRelease
import kotlinx.coroutines.launch

data class WhatsNewStrings(
    val title: String,
    val previous: String,
    val next: String,
    val continueLabel: String,
    val version: @Composable (String) -> String,
    val versionHeading: @Composable (String) -> String,
)

@Composable
fun WhatsNewContent(
    releases: List<PendingRelease>,
    isCompleting: Boolean,
    strings: WhatsNewStrings,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    padding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    require(releases.isNotEmpty())
    val pagerState = rememberPagerState(pageCount = releases::size)
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == releases.lastIndex
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize().padding(padding)) {
            IconButton(onClick = onDismiss, enabled = !isCompleting, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).semantics { contentDescription = "dismiss_whats_new" }) {
                Icon(Icons.Default.Close, contentDescription = null)
            }
            Column(
                modifier = Modifier.fillMaxSize().widthIn(max = 720.dp).padding(horizontal = 24.dp, vertical = 28.dp).align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WhatsNewBrandMark()
                Text(strings.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 18.dp))
                WhatsNewPageIndicator(releases.size, currentPage, Modifier.padding(top = 14.dp, bottom = 18.dp))
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    WhatsNewReleasePage(releases[page], strings, Modifier.fillMaxSize())
                }
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(currentPage - 1, animationSpec = tween(260)) } },
                        enabled = currentPage > 0 && !isCompleting,
                        colors = ButtonDefaults.outlinedButtonColors(),
                        modifier = Modifier.semantics { contentDescription = "previous_whats_new" },
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp)); Text(strings.previous, Modifier.padding(start = 6.dp)) }
                    Button(
                        onClick = { if (isLastPage) onComplete() else scope.launch { pagerState.animateScrollToPage(currentPage + 1, animationSpec = tween(260)) } },
                        enabled = !isCompleting,
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsNewOrange),
                        modifier = Modifier.semantics { contentDescription = "next_whats_new" },
                    ) {
                        Text(if (isLastPage) strings.continueLabel else strings.next)
                        if (!isLastPage) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = 6.dp).size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable private fun WhatsNewBrandMark() = Box(Modifier.size(58.dp).background(WhatsNewOrange, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
    Text("Q", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
}
@Composable private fun WhatsNewReleasePage(release: PendingRelease, strings: WhatsNewStrings, modifier: Modifier = Modifier) {
    val version = release.versionName?.takeIf { it.isNotBlank() } ?: release.versionCode.toString()
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(strings.version(version), style = MaterialTheme.typography.labelLarge, color = WhatsNewOrange, fontWeight = FontWeight.Bold)
        Text(strings.versionHeading(version), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 20.dp)) {
            Text(release.localizedNote, style = MaterialTheme.typography.bodyLarge, lineHeight = 26.sp, modifier = Modifier.padding(22.dp))
        }
    }
}
@Composable private fun WhatsNewPageIndicator(count: Int, selectedIndex: Int, modifier: Modifier = Modifier) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
    repeat(count) { index -> Box(Modifier.height(8.dp).size(if (index == selectedIndex) 22.dp else 8.dp, 8.dp).background(if (index == selectedIndex) WhatsNewOrange else MaterialTheme.colorScheme.outlineVariant, CircleShape)) }
}
private val WhatsNewOrange = Color(0xFFFF7416)
