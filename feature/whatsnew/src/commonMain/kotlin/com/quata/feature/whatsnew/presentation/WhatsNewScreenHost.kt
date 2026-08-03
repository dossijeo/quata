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

/** Shared pending-release state machine for hosts without Android's navigation container. */
@Composable
fun WhatsNewScreenHost(
    repository: WhatsNewRepository,
    installedVersionCode: Long?,
    languageTags: List<String>,
    strings: WhatsNewStrings,
    loadError: String,
    saveError: String,
    retry: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var releases by remember(repository, installedVersionCode, languageTags) { mutableStateOf<List<PendingRelease>?>(null) }
    var isLoading by remember(repository, installedVersionCode, languageTags) { mutableStateOf(true) }
    var loadFailed by remember(repository, installedVersionCode, languageTags) { mutableStateOf(false) }
    var retryToken by remember { mutableStateOf(0) }
    var isCompleting by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(repository, installedVersionCode, languageTags, retryToken) {
        if (installedVersionCode == null) {
            isLoading = false
            return@LaunchedEffect
        }
        repository.getPendingReleases(installedVersionCode, languageTags).fold(
            onSuccess = { releases = it; isLoading = false },
            onFailure = { loadFailed = true; isLoading = false },
        )
    }
    when {
        isLoading -> CenteredWhatsNewMessage(modifier) { CircularProgressIndicator() }
        loadFailed -> CenteredWhatsNewMessage(modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(loadError)
                Button(
                    onClick = { releases = null; loadFailed = false; isLoading = true; retryToken++ },
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text(retry) }
            }
        }
        releases.isNullOrEmpty() -> LaunchedEffect(onClose) { onClose() }
        else -> {
            val currentReleases = releases.orEmpty()
            val finish: () -> Unit = finish@{
                if (isCompleting) return@finish
                isCompleting = true
                saveFailed = false
                scope.launch {
                    repository.markReleasesSeen(currentReleases.maxOf(PendingRelease::versionCode), installedVersionCode!!)
                        .onSuccess { onClose() }
                        .onFailure { isCompleting = false; saveFailed = true }
                }
            }
            Box(modifier.fillMaxSize()) {
                WhatsNewContent(currentReleases, isCompleting, strings, finish, finish)
                if (saveFailed) Text(saveError, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}

@Composable
private fun CenteredWhatsNewMessage(modifier: Modifier, content: @Composable () -> Unit) =
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
