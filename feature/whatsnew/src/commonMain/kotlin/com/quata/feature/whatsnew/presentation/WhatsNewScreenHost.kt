package com.quata.feature.whatsnew.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.quata.feature.whatsnew.domain.PendingRelease
import com.quata.feature.whatsnew.domain.WhatsNewRepository
import kotlinx.coroutines.launch

data class WhatsNewScreenHostStrings(
    val content: WhatsNewStrings,
    val loadError: String,
    val saveError: String,
    val retry: String,
)

/** Shared pending-release state machine for hosts without Android's navigation container. */
@Composable
fun WhatsNewScreenHost(
    repository: WhatsNewRepository,
    installedVersionCode: Long?,
    languageTags: List<String>,
    strings: WhatsNewScreenHostStrings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember(repository, installedVersionCode, languageTags) { mutableStateOf<WhatsNewScreenState>(WhatsNewScreenState.Loading) }
    var retryToken by remember { mutableStateOf(0) }
    var isCompleting by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository, installedVersionCode, languageTags, retryToken) {
        if (installedVersionCode == null) {
            state = WhatsNewScreenState.Empty
            return@LaunchedEffect
        }
        state = repository.getPendingReleases(installedVersionCode, languageTags).fold(
            onSuccess = { if (it.isEmpty()) WhatsNewScreenState.Empty else WhatsNewScreenState.Content(it) },
            onFailure = { WhatsNewScreenState.Error },
        )
    }
    when (val current = state) {
        WhatsNewScreenState.Loading -> CenteredWhatsNewMessage(modifier) { CircularProgressIndicator() }
        WhatsNewScreenState.Empty -> LaunchedEffect(onClose) { onClose() }
        WhatsNewScreenState.Error -> CenteredWhatsNewMessage(modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(strings.loadError)
                Button(onClick = { state = WhatsNewScreenState.Loading; retryToken++ }, modifier = Modifier.padding(top = 12.dp)) { Text(strings.retry) }
            }
        }
        is WhatsNewScreenState.Content -> {
            val finish: () -> Unit = finish@{
                if (isCompleting) return@finish
                isCompleting = true
                saveFailed = false
                scope.launch {
                    repository.markReleasesSeen(current.releases.maxOf(PendingRelease::versionCode), installedVersionCode!!)
                        .onSuccess { onClose() }
                        .onFailure { isCompleting = false; saveFailed = true }
                }
            }
            Box(modifier.fillMaxSize()) {
                WhatsNewContent(current.releases, isCompleting, strings.content, finish, finish)
                if (saveFailed) Text(strings.saveError, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}

@Composable
private fun CenteredWhatsNewMessage(modifier: Modifier, content: @Composable () -> Unit) =
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }

private sealed interface WhatsNewScreenState {
    data object Loading : WhatsNewScreenState
    data object Empty : WhatsNewScreenState
    data object Error : WhatsNewScreenState
    data class Content(val releases: List<PendingRelease>) : WhatsNewScreenState
}
