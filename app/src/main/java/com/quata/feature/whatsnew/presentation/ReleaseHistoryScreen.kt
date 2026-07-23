package com.quata.feature.whatsnew.presentation

import android.os.LocaleList
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.quata.R
import com.quata.feature.whatsnew.domain.WhatsNewRepository

@Composable
fun ReleaseHistoryScreen(repository: WhatsNewRepository, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val languageTags = remember { LocaleList.getDefault().let { locales -> List(locales.size()) { locales[it].toLanguageTag() } } }
    val strings = ReleaseHistoryStrings(
        close = stringResource(R.string.common_close), empty = stringResource(R.string.release_history_empty),
        error = stringResource(R.string.release_history_error), title = stringResource(R.string.release_history_title),
        subtitle = stringResource(R.string.release_history_subtitle), previous = stringResource(R.string.whats_new_previous),
        next = stringResource(R.string.whats_new_next), version = { stringResource(R.string.whats_new_version, it) },
        versionHeading = { stringResource(R.string.whats_new_version_heading, it) },
    )
    ReleaseHistoryContent(repository, languageTags, strings, onBack, modifier.windowInsetsPadding(WindowInsets.safeDrawing))
}
