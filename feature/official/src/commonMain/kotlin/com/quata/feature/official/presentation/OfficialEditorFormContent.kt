package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Structural flow for the Official editor form.
 *
 * The caller owns editor state and supplies every platform-sensitive surface through
 * slots: media acquisition/editing, avatar/preview rendering, localized resources,
 * navigation and publication side effects. Keeping this sequence here lets platform
 * hosts reuse the same form hierarchy without moving URI, bitmap or Android APIs into
 * [commonMain].
 */
@Composable
fun OfficialEditorFormContent(
    modeSelector: @Composable () -> Unit,
    mainSection: @Composable () -> Unit,
    mediaSection: @Composable () -> Unit,
    bodySection: @Composable () -> Unit,
    previewSection: @Composable () -> Unit,
    feedback: @Composable () -> Unit,
    publishAction: @Composable () -> Unit,
) {
    modeSelector()
    mainSection()
    mediaSection()
    bodySection()
    previewSection()
    feedback()
    publishAction()
    Spacer(Modifier.height(96.dp))
}
