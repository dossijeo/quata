package com.quata.feature.whatsnew.presentation

import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.R
import com.quata.feature.whatsnew.domain.PendingRelease
import com.quata.feature.whatsnew.domain.WhatsNewRepository
import kotlinx.coroutines.launch

@Composable
fun ReleaseHistoryScreen(
    repository: WhatsNewRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf<ReleaseHistoryState>(ReleaseHistoryState.Loading) }
    val locales = remember { LocaleList.getDefault() }

    LaunchedEffect(repository, locales) {
        state = ReleaseHistoryState.Loading
        state = repository.getReleaseHistory(locales)
            .fold(
                onSuccess = { releases ->
                    if (releases.isEmpty()) ReleaseHistoryState.Empty else ReleaseHistoryState.Content(releases)
                },
                onFailure = { ReleaseHistoryState.Error }
            )
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(Modifier.fillMaxSize()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
            }
            when (val current = state) {
                ReleaseHistoryState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ReleaseHistoryState.Empty -> HistoryMessage(
                    stringResource(R.string.release_history_empty),
                    Modifier.align(Alignment.Center)
                )
                ReleaseHistoryState.Error -> HistoryMessage(
                    stringResource(R.string.release_history_error),
                    Modifier.align(Alignment.Center)
                )
                is ReleaseHistoryState.Content -> ReleaseHistoryPager(
                    releases = current.releases,
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ReleaseHistoryPager(
    releases: List<PendingRelease>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = releases::size)
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage

    Column(
        modifier = modifier
            .widthIn(max = 720.dp)
            .padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.release_history_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            text = stringResource(R.string.release_history_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        HistoryIndicator(
            count = releases.size,
            selectedIndex = currentPage,
            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)
        )
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            ReleaseHistoryPage(releases[page], Modifier.fillMaxSize())
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                enabled = currentPage > 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(currentPage - 1) } }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                Text(stringResource(R.string.whats_new_previous), Modifier.padding(start = 6.dp))
            }
            Button(
                onClick = {
                    if (currentPage == releases.lastIndex) onBack()
                    else scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                },
                colors = ButtonDefaults.buttonColors(containerColor = HistoryOrange)
            ) {
                Text(stringResource(if (currentPage == releases.lastIndex) R.string.common_close else R.string.whats_new_next))
                if (currentPage != releases.lastIndex) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = 6.dp).size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ReleaseHistoryPage(release: PendingRelease, modifier: Modifier = Modifier) {
    val version = release.versionName?.takeIf { it.isNotBlank() } ?: release.versionCode.toString()
    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Text(
            stringResource(R.string.whats_new_version, version),
            style = MaterialTheme.typography.labelLarge,
            color = HistoryOrange,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.whats_new_version_heading, version),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) {
            Text(
                release.localizedNote,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(22.dp)
            )
        }
    }
}

@Composable
private fun HistoryIndicator(count: Int, selectedIndex: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(count.coerceAtMost(12)) { index ->
            Box(
                Modifier
                    .size(if (index == selectedIndex) 22.dp else 8.dp, 8.dp)
                    .background(
                        if (index == selectedIndex) HistoryOrange else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}

@Composable
private fun HistoryMessage(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = modifier.padding(24.dp))
}

private sealed interface ReleaseHistoryState {
    data object Loading : ReleaseHistoryState
    data object Empty : ReleaseHistoryState
    data object Error : ReleaseHistoryState
    data class Content(val releases: List<PendingRelease>) : ReleaseHistoryState
}

private val HistoryOrange = androidx.compose.ui.graphics.Color(0xFFFF7416)
