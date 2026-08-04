package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.official.data.IosOfficialReadRepository
import com.quata.feature.official.data.IosOfficialRuntimeConfiguration
import com.quata.feature.official.domain.OfficialRepository
import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.platform.IosShareService
import com.quata.core.platform.ShareService
import com.quata.core.ui.components.IosMemberProfileOpeningState
import platform.UIKit.UIViewController

/** Narrow Swift-owned native viewer contract; it never owns pager or Official state. */
interface IosOfficialMediaViewerFactory {
    fun create(url: String, isVideo: Boolean): IosOfficialMediaViewerSurface
}

interface IosOfficialMediaViewerSurface {
    fun nativeView(): platform.UIKit.UIView
    fun dispose()
}

/**
 * iOS composition input for the shared Official list and detail flow.
 *
 * [repository] belongs to the iOS launcher: it supplies authenticated transport and lifecycle.
 * The UIKit launcher supplies only target seams; the product screen remains common.
 */
class IosOfficialHostDependencies(
    val repository: OfficialRepository,
    val officialPostId: String? = null,
    val currentUserId: String? = null,
    val preferredLanguageTag: String? = null,
    val shareService: ShareService = IosShareService(),
    val mediaViewerFactory: IosOfficialMediaViewerFactory? = null,
    val canCreateOfficialPost: Boolean = false,
    val onAuthRequired: () -> Unit = {},
    val onOpenUserProfile: (String) -> Unit = {},
    val onCreateOfficialPost: () -> Unit = {},
    val profileOpeningState: IosMemberProfileOpeningState,
)

/**
 * Swift-facing dependency factory for the shared iOS Official vertical.
 *
 * Keeping the default platform slots in Kotlin avoids making the UIKit launcher construct
 * Compose slot objects or depend on Kotlin default-argument Objective-C export details.
 */
fun createIosOfficialHostDependencies(
    repository: OfficialRepository,
    officialPostId: String?,
    shareService: ShareService = IosShareService(), mediaViewerFactory: IosOfficialMediaViewerFactory? = null,
    currentUserId: String? = null,
    preferredLanguageTag: String? = null,
    onAuthRequired: () -> Unit = {}, onOpenUserProfile: (String) -> Unit = {},
    onCreateOfficialPost: () -> Unit = {},
    canCreateOfficialPost: Boolean = false,
    profileOpeningState: IosMemberProfileOpeningState,
): IosOfficialHostDependencies = IosOfficialHostDependencies(
    repository = repository,
    officialPostId = officialPostId,
    currentUserId = currentUserId,
    preferredLanguageTag = preferredLanguageTag,
    shareService = shareService,
    mediaViewerFactory = mediaViewerFactory,
    onAuthRequired = onAuthRequired, onOpenUserProfile = onOpenUserProfile,
    onCreateOfficialPost = onCreateOfficialPost,
    canCreateOfficialPost = canCreateOfficialPost,
    profileOpeningState = profileOpeningState,
)

/**
 * Creates the public iOS Official browser from client-safe deployment values only.
 *
 * The repository has no interactive-session parameter on this route. Its read transport can
 * therefore neither wait for Keychain restoration nor attach a bearer credential; authenticated
 * interactions fail closed and publishing remains unsupported.
 */
fun iosPublicPostgrestReadOnlyOfficialHostDependencies(
    configuration: IosOfficialRuntimeConfiguration,
    officialPostId: String? = null,
    shareService: ShareService = IosShareService(),
    mediaViewerFactory: IosOfficialMediaViewerFactory? = null,
    onAuthRequired: () -> Unit = {}, onOpenUserProfile: (String) -> Unit = {},
    profileOpeningState: IosMemberProfileOpeningState,
): IosOfficialHostDependencies = createIosOfficialHostDependencies(
    repository = IosOfficialReadRepository(configuration = configuration),
    officialPostId = officialPostId,
    shareService = shareService,
    mediaViewerFactory = mediaViewerFactory,
    onAuthRequired = onAuthRequired, onOpenUserProfile = onOpenUserProfile,
    profileOpeningState = profileOpeningState,
)

/** Authenticated iOS path reuses the renewable Keychain session for reviewed Official writes. */
fun iosAuthenticatedPostgrestOfficialHostDependencies(
    configuration: IosOfficialRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
    officialPostId: String? = null,
    shareService: ShareService = IosShareService(),
    mediaViewerFactory: IosOfficialMediaViewerFactory? = null,
    currentUserId: String? = authSession.restoredSession()?.userId,
    onAuthRequired: () -> Unit = {},
    onOpenUserProfile: (String) -> Unit = {},
    onCreateOfficialPost: () -> Unit = {},
    canCreateOfficialPost: Boolean = false,
    preferredLanguageTag: String? = null,
    profileOpeningState: IosMemberProfileOpeningState,
): IosOfficialHostDependencies = createIosOfficialHostDependencies(
    repository = IosOfficialReadRepository(configuration = configuration, authSession = authSession, preferredLanguageTag = preferredLanguageTag),
    officialPostId = officialPostId,
    shareService = shareService,
    mediaViewerFactory = mediaViewerFactory,
    currentUserId = currentUserId,
    preferredLanguageTag = preferredLanguageTag,
    onAuthRequired = onAuthRequired,
    onOpenUserProfile = onOpenUserProfile,
    canCreateOfficialPost = canCreateOfficialPost,
    onCreateOfficialPost = onCreateOfficialPost,
    profileOpeningState = profileOpeningState,
)

/**
 * Stable Swift-callable factory for the shared Official list/detail viewport.
 * No repository implementation is created here; the iOS composition root injects the real one.
 */
fun QuataOfficialViewController(dependencies: IosOfficialHostDependencies): UIViewController =
    ComposeUIViewController {
        QuataTheme {
            val strings = defaultOfficialFeedScreenStrings(dependencies.preferredLanguageTag)
            val openingProfileUserId by dependencies.profileOpeningState.profileId.collectAsState()
            OfficialFeedScreenHost(
                padding = PaddingValues(),
                repository = dependencies.repository,
                currentUserId = dependencies.currentUserId,
                strings = strings,
                focusedPostId = dependencies.officialPostId,
                onAuthRequired = dependencies.onAuthRequired,
                onOpenUserProfile = dependencies.onOpenUserProfile,
                onCreateOfficialPost = dependencies.onCreateOfficialPost,
                slots = iosOfficialPlatformSlots(
                    dependencies.shareService,
                    dependencies.mediaViewerFactory,
                    dependencies.canCreateOfficialPost,
                    strings.close,
                    openingProfileUserId,
                ),
                onFocusedPostHandled = {},
                modifier = Modifier,
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
