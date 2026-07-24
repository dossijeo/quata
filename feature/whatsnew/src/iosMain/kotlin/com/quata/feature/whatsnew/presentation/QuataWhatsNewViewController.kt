package com.quata.feature.whatsnew.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.ui.components.QuataAboutDialogContent
import com.quata.feature.whatsnew.domain.PendingRelease
import com.quata.feature.whatsnew.domain.WhatsNewRepository
import platform.UIKit.UIViewController

/**
 * iOS composition input for the common What's New pager. Release state remains launcher-owned so
 * this module never needs WorkManager, caches or a local release repository.
 */
class IosWhatsNewHostDependencies(
    val releases: List<PendingRelease>,
    val isCompleting: Boolean,
    val strings: WhatsNewStrings,
    val onComplete: () -> Unit,
    val onDismiss: () -> Unit,
)

/** Swift-callable UIKit factory for the shared What's New screen. */
fun QuataWhatsNewViewController(dependencies: IosWhatsNewHostDependencies): UIViewController =
    ComposeUIViewController {
        QuataTheme {
            WhatsNewContent(
                releases = dependencies.releases,
                isCompleting = dependencies.isCompleting,
                strings = dependencies.strings,
                onComplete = dependencies.onComplete,
                onDismiss = dependencies.onDismiss,
            )
        }
    }

/**
 * iOS composition input for common release history. The host supplies its authenticated
 * repository and preferred language tags; this factory owns neither transport nor persistence.
 */
class IosReleaseHistoryHostDependencies(
    val repository: WhatsNewRepository,
    val languageTags: List<String>,
    val strings: ReleaseHistoryStrings,
    val onBack: () -> Unit,
)

/** Swift-callable UIKit factory for the shared release-history viewport. */
fun QuataReleaseHistoryViewController(dependencies: IosReleaseHistoryHostDependencies): UIViewController =
    ComposeUIViewController {
        QuataTheme {
            ReleaseHistoryContent(
                repository = dependencies.repository,
                languageTags = dependencies.languageTags,
                strings = dependencies.strings,
                onBack = dependencies.onBack,
            )
        }
    }

/**
 * Host-owned About copy and legal-link slot. The iOS launcher chooses how URLs are opened and
 * where the release-history destination lives; common Compose owns only the dialog structure.
 */
class IosAboutHostDependencies(
    val title: String,
    val version: String,
    val versionDate: String,
    val body: String,
    val releaseHistoryLabel: String,
    val closeLabel: String,
    val onDismiss: () -> Unit,
    val onOpenReleaseHistory: () -> Unit,
    val legalLinks: @Composable () -> Unit,
)

/** Swift-callable UIKit factory for the portable About dialog. */
fun QuataAboutViewController(dependencies: IosAboutHostDependencies): UIViewController =
    ComposeUIViewController {
        QuataTheme {
            QuataAboutDialogContent(
                title = dependencies.title,
                version = dependencies.version,
                versionDate = dependencies.versionDate,
                body = dependencies.body,
                releaseHistoryLabel = dependencies.releaseHistoryLabel,
                closeLabel = dependencies.closeLabel,
                onDismiss = dependencies.onDismiss,
                onOpenReleaseHistory = dependencies.onOpenReleaseHistory,
                legalLinks = dependencies.legalLinks,
            )
        }
    }
