package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.official.domain.OfficialRepository
import platform.UIKit.UIViewController

/**
 * iOS composition input for the shared Official list and detail flow.
 *
 * [repository] belongs to the iOS launcher: it supplies authenticated transport and lifecycle.
 * [browserSlots] keep native avatar/media renderers and navigation outside common presentation.
 */
class IosOfficialHostDependencies(
    val repository: OfficialRepository,
    val officialPostId: String? = null,
    val navigationMessage: String = "Quata para iOS",
    val browserSlots: OfficialBrowserHostSlots = OfficialBrowserHostSlots(),
)

/**
 * Swift-facing dependency factory for the read-only iOS Official vertical.
 *
 * Keeping the default platform slots in Kotlin avoids making the UIKit launcher construct
 * Compose slot objects or depend on Kotlin default-argument Objective-C export details.
 */
fun createIosOfficialHostDependencies(
    repository: OfficialRepository,
    officialPostId: String?,
    navigationMessage: String,
): IosOfficialHostDependencies = IosOfficialHostDependencies(
    repository = repository,
    officialPostId = officialPostId,
    navigationMessage = navigationMessage,
)

/**
 * Stable Swift-callable factory for the shared Official list/detail viewport.
 * No repository implementation is created here; the iOS composition root injects the real one.
 */
fun QuataOfficialViewController(dependencies: IosOfficialHostDependencies): UIViewController =
    ComposeUIViewController {
        QuataTheme {
            OfficialBrowserHostContent(
                repository = dependencies.repository,
                officialPostId = dependencies.officialPostId,
                navigationMessage = dependencies.navigationMessage,
                slots = dependencies.browserSlots,
            )
        }
    }

/**
 * iOS composition input for the shared Official editor shell.
 *
 * The editor fields, media picker and publication actions are intentionally injected. This avoids
 * a pretend iOS backend while allowing the shared editor hierarchy to be hosted now.
 */
class IosOfficialEditorDependencies(
    val title: String,
    val content: @Composable ColumnScope.() -> Unit,
)

/** Swift-callable UIKit factory for a host-supplied Official editor built on common Compose UI. */
fun QuataOfficialEditorViewController(dependencies: IosOfficialEditorDependencies): UIViewController =
    ComposeUIViewController {
        QuataTheme {
            OfficialEditorScreenContent(
                padding = PaddingValues(),
                title = dependencies.title,
                content = dependencies.content,
            )
        }
    }
