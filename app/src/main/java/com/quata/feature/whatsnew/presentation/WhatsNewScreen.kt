package com.quata.feature.whatsnew.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quata.R
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.whatsnew.domain.PendingRelease

/** Android resource/window adapter for the shared Compose WhatsNewContent screen. */
@Composable
fun WhatsNewScreen(
    releases: List<PendingRelease>,
    isCompleting: Boolean,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    padding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    val strings = WhatsNewStrings(
        title = stringResource(R.string.whats_new_title),
        previous = stringResource(R.string.whats_new_previous),
        next = stringResource(R.string.whats_new_next),
        continueLabel = stringResource(R.string.whats_new_continue),
        version = { version -> stringResource(R.string.whats_new_version, version) },
        versionHeading = { version -> stringResource(R.string.whats_new_version_heading, version) },
    )
    val statusInset = if (rememberQuataWindowLayoutInfo().isLandscape) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars)
    QuataScreen(padding) {
        WhatsNewContent(
            releases = releases,
            isCompleting = isCompleting,
            strings = strings,
            onComplete = onComplete,
            onDismiss = onDismiss,
            modifier = modifier.then(statusInset),
        )
    }
}
