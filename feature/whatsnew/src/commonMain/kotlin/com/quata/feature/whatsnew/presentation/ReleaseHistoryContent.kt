package com.quata.feature.whatsnew.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.feature.whatsnew.domain.PendingRelease
import com.quata.feature.whatsnew.domain.WhatsNewRepository
import kotlinx.coroutines.launch

data class ReleaseHistoryStrings(
    val close: String, val empty: String, val error: String, val title: String, val subtitle: String,
    val previous: String, val next: String, val version: @Composable (String) -> String,
    val versionHeading: @Composable (String) -> String,
)

@Composable
fun ReleaseHistoryContent(repository: WhatsNewRepository, languageTags: List<String>, strings: ReleaseHistoryStrings, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var state by remember { mutableStateOf<ReleaseHistoryState>(ReleaseHistoryState.Loading) }
    LaunchedEffect(repository, languageTags) {
        state = ReleaseHistoryState.Loading
        state = repository.getReleaseHistory(languageTags).fold(
            onSuccess = { if (it.isEmpty()) ReleaseHistoryState.Empty else ReleaseHistoryState.Content(it) },
            onFailure = { ReleaseHistoryState.Error },
        )
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize()) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { Icon(Icons.Default.Close, strings.close) }
            when (val current = state) {
                ReleaseHistoryState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ReleaseHistoryState.Empty -> ReleaseHistoryMessage(strings.empty, Modifier.align(Alignment.Center))
                ReleaseHistoryState.Error -> ReleaseHistoryMessage(strings.error, Modifier.align(Alignment.Center))
                is ReleaseHistoryState.Content -> ReleaseHistoryPager(current.releases, strings, onBack, Modifier.fillMaxSize())
            }
        }
    }
}
@Composable private fun ReleaseHistoryPager(releases: List<PendingRelease>, strings: ReleaseHistoryStrings, onBack: () -> Unit, modifier: Modifier) {
    val pagerState = rememberPagerState(pageCount = releases::size); val scope = rememberCoroutineScope(); val current = pagerState.currentPage
    Column(modifier.widthIn(max = 720.dp).padding(horizontal = 24.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(strings.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(strings.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        ReleaseHistoryIndicator(releases.size, current, Modifier.padding(top = 16.dp, bottom = 20.dp))
        HorizontalPager(pagerState, Modifier.weight(1f)) { ReleaseHistoryPage(releases[it], strings, Modifier.fillMaxSize()) }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { scope.launch { pagerState.animateScrollToPage(current - 1) } }, enabled = current > 0) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp)); Text(strings.previous, Modifier.padding(start = 6.dp)) }
            Button({ if (current == releases.lastIndex) onBack() else scope.launch { pagerState.animateScrollToPage(current + 1) } }, colors = ButtonDefaults.buttonColors(containerColor = HistoryOrange)) { Text(if (current == releases.lastIndex) strings.close else strings.next); if (current != releases.lastIndex) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = 6.dp).size(18.dp)) }
        }
    }
}
@Composable private fun ReleaseHistoryPage(release: PendingRelease, strings: ReleaseHistoryStrings, modifier: Modifier) {
    val version = release.versionName?.takeIf { it.isNotBlank() } ?: release.versionCode.toString()
    Column(modifier.padding(horizontal = 4.dp)) { Text(strings.version(version), style = MaterialTheme.typography.labelLarge, color = HistoryOrange, fontWeight = FontWeight.Bold); Text(strings.versionHeading(version), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)); Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text(release.localizedNote, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(22.dp)) } }
}
@Composable private fun ReleaseHistoryIndicator(count: Int, selected: Int, modifier: Modifier) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) { repeat(count.coerceAtMost(12)) { index -> Box(Modifier.size(if (index == selected) 22.dp else 8.dp, 8.dp).background(if (index == selected) HistoryOrange else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))) } }
@Composable private fun ReleaseHistoryMessage(text: String, modifier: Modifier) { Text(text, style = MaterialTheme.typography.bodyLarge, modifier = modifier.padding(24.dp)) }
private sealed interface ReleaseHistoryState { data object Loading : ReleaseHistoryState; data object Empty : ReleaseHistoryState; data object Error : ReleaseHistoryState; data class Content(val releases: List<PendingRelease>) : ReleaseHistoryState }
private val HistoryOrange = Color(0xFFFF7416)
