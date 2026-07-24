package com.quata.feature.postcomposer.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared text-post preview, including its portable canvas and non-interactive feed chrome.
 *
 * The caller provides localized labels and the reader dismiss affordance; this keeps resource
 * ownership in the host while making the entire text-only preview reusable on every platform.
 */
data class ComposerTextPostPreviewStrings(
    val emptyText: String,
    val readMoreText: String,
    val authorName: String,
    val authorSubtitle: String,
)

@Composable
fun ComposerTextPostPreviewContent(
    text: String,
    patternId: String?,
    compact: Boolean,
    strings: ComposerTextPostPreviewStrings,
    actionLabels: ComposerPreviewActionLabels,
    readerDismissButton: @Composable (modifier: Modifier, onDismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    ComposerPostPreviewContent(
        isVideo = false,
        compact = compact,
        mediaAspectRatio = 9f / 16f,
        backgroundSeed = text,
        media = {
            ComposerTextCanvasContent(
                text = text,
                patternId = patternId,
                compact = compact,
                emptyText = strings.emptyText,
                readMoreText = strings.readMoreText,
                readerDismissButton = readerDismissButton,
            )
        },
        scrim = { ComposerPreviewScrimContent() },
        topOverlay = { overlayModifier ->
            ComposerPreviewTopChipsContent(
                chips = emptyList(),
                modifier = overlayModifier,
            )
        },
        actionRail = { railModifier ->
            ComposerPreviewActionsContent(
                showRankLiveActions = !compact,
                labels = actionLabels,
                modifier = railModifier,
            )
        },
        compactLeadingActions = { leadingModifier ->
            ComposerPreviewRankLiveActionsContent(
                labels = actionLabels,
                modifier = leadingModifier,
            )
        },
        authorOverlay = { authorModifier ->
            ComposerPreviewAuthorContent(
                description = "",
                authorName = strings.authorName,
                subtitle = strings.authorSubtitle,
                modifier = authorModifier,
            )
        },
        modifier = modifier,
    )
}
