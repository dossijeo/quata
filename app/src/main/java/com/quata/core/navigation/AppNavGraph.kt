package com.quata.core.navigation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quata.core.common.toUserFacingMessage
import com.quata.core.device.QuataProximityState
import com.quata.core.di.AppContainer
import com.quata.core.location.hasQuataLocationPermission
import com.quata.core.location.quataLastLocation
import com.quata.core.location.SosLocationRecoveryService
import com.quata.core.moderation.LegalDocument
import com.quata.core.moderation.LegalDocuments
import com.quata.core.moderation.ModerationTarget
import com.quata.core.network.ForegroundConnectivityReconciler
import com.quata.core.presence.LocalUserPresence
import com.quata.core.session.AuthState
import com.quata.core.text.SosShortcodeKind
import com.quata.core.text.buildSosShortcode
import com.quata.core.ui.components.LocalQuataNetworkImageState
import com.quata.core.ui.components.QuataBottomBar
import com.quata.core.ui.components.QuataNavigationRail
import com.quata.core.ui.components.QuataNavigationRailWidth
import com.quata.core.ui.components.QuataNetworkImageState
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.applyQuataSystemBars
import com.quata.designsystem.effects.fluidTouchEffect
import com.quata.core.ui.richtext.RichTextEditorQaScreen
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.designsystem.translation.QuataTranslatableTextRegistry
import com.quata.designsystem.translation.QuataTranslatorBackground
import com.quata.core.translation.QuataTranslatorModeProvider
import com.quata.core.translation.QuataTranslatorOverlay
import com.quata.core.translation.QuataTranslatorOverlayBackdrop
import com.quata.designsystem.translation.QuataTranslatorOverlaySource
import com.quata.core.translation.QuataTranslatorModeController
import com.quata.core.translation.captureTranslatorBackground
import com.quata.feature.auth.presentation.login.LoginScreen
import com.quata.feature.auth.presentation.recovery.ForgotPasswordScreen
import com.quata.feature.auth.presentation.register.RegisterScreen
import com.quata.feature.chat.domain.SosRateLimitException
import com.quata.feature.chat.presentation.chat.ChatScreen
import com.quata.feature.chat.presentation.conversations.ConversationsScreen
import com.quata.feature.feed.presentation.FeedScreen
import com.quata.feature.externalshare.ExternalSharePayload
import com.quata.feature.externalshare.ShareTargetAvailability
import com.quata.feature.externalshare.ShareToQuataDialog
import com.quata.feature.neighborhoods.presentation.CommunityProfileScreen
import com.quata.feature.neighborhoods.presentation.NeighborhoodsScreen
import com.quata.feature.neighborhoods.presentation.NeighborhoodsAndroidViewModel
import com.quata.feature.notifications.presentation.NotificationsScreen
import com.quata.feature.official.presentation.OfficialFeedScreen
import com.quata.feature.official.presentation.OfficialPostEditorRoute
import com.quata.feature.postcomposer.presentation.CreatePostScreen
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.UserProfile
import com.quata.feature.profile.presentation.EmergencyContactsDialog
import com.quata.feature.profile.presentation.ProfileUiEvent
import com.quata.feature.profile.presentation.ProfileAndroidViewModel
import com.quata.feature.profile.presentation.ProfileScreen
import com.quata.feature.whatsnew.domain.StartupDestination
import com.quata.feature.whatsnew.presentation.StartupCoordinator
import com.quata.feature.whatsnew.presentation.ReleaseHistoryScreen
import com.quata.feature.whatsnew.presentation.WhatsNewScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.quata.BuildConfig
import com.quata.core.designsystem.theme.QuataResolvedTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.language.QuataTranslator

@Composable
fun AppNavGraph(
    container: AppContainer,
    themeMode: QuataThemeMode,
    incomingLink: Uri? = null,
    onIncomingLinkHandled: () -> Unit = {},
    incomingShare: ExternalSharePayload? = null,
    onIncomingShareHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = currentBackStackEntry?.destination?.route
    val currentConversationId = currentBackStackEntry?.arguments?.getString("conversationId")
    val authState by container.sessionManager.authState.collectAsState()
    val currentUserId = (authState as? AuthState.LoggedIn)?.userId
    val isAuthenticated = currentUserId != null
    val startupCoordinator = remember(container.whatsNewRepository) {
        StartupCoordinator(container.whatsNewRepository)
    }
    var startupDestination by remember(currentUserId) {
        mutableStateOf(if (currentUserId == null) StartupDestination.Main else StartupDestination.Loading)
    }
    var isCompletingWhatsNew by remember(currentUserId) { mutableStateOf(false) }
    val isWhatsNewStartupActive = isAuthenticated && startupDestination != StartupDestination.Main
    val touchFlowEnabled by remember(currentUserId, container.touchFlowPreferences) {
        container.touchFlowPreferences.observeEnabled(currentUserId)
    }.collectAsState(initial = container.touchFlowPreferences.isEnabled(currentUserId))
    val startDestination = AppDestinations.Feed.route
    val template = quataTheme()
    var isVideoEditorOpen by rememberSaveable { mutableStateOf(false) }
    var isCreatePostUploadInProgress by rememberSaveable { mutableStateOf(false) }
    var pendingCreatePostUploadRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val routeShowsAppChrome = currentRoute != null &&
        currentRoute != AppDestinations.Login.route &&
        currentRoute != AppDestinations.Register.route &&
        currentRoute != AppDestinations.ForgotPassword.route &&
        currentRoute != AppDestinations.ReleaseHistory.route
    val showAppChrome = routeShowsAppChrome && !isVideoEditorOpen && !isWhatsNewStartupActive
    val observedNotificationCount by container.notificationsRepository.observeNotificationCount().collectAsState<Int, Int?>(initial = null)
    val notificationCount = observedNotificationCount ?: 0
    val isDeviceNetworkAvailable = rememberDeviceNetworkAvailable()
    val isAppOnline = isDeviceNetworkAvailable
    var feedNetworkReconnectToken by rememberSaveable { mutableLongStateOf(0L) }
    var previousDeviceNetworkAvailable by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val appContext = LocalContext.current
    val rootView = LocalView.current
    val appScope = rememberCoroutineScope()
    val translatorRegistry = remember { QuataTranslatableTextRegistry() }
    var isTranslatorModeActive by remember { mutableStateOf(false) }
    var isTranslatorOverlayMounted by remember { mutableStateOf(false) }
    var translatorBackground by remember { mutableStateOf<QuataTranslatorBackground?>(null) }
    var translatorOverlaySource by remember { mutableStateOf(QuataTranslatorOverlaySource.Chat) }
    var translatorActivationToken by remember { mutableLongStateOf(0L) }
    var isFeedCommentsOverlayVisible by remember { mutableStateOf(false) }
    val translatorModeController = remember(rootView, appScope) {
        QuataTranslatorModeController { anchorView, source ->
            val activationToken = translatorActivationToken + 1L
            translatorActivationToken = activationToken
            appScope.launch {
                launch { runCatching { QuataTranslator.shared.warmup() } }
                translatorOverlaySource = source
                val capturedBackground = captureTranslatorBackground(
                    view = anchorView,
                    cropNavigationBars = source != QuataTranslatorOverlaySource.Comments
                )
                if (translatorActivationToken == activationToken) {
                    translatorBackground = capturedBackground
                    isTranslatorOverlayMounted = true
                    isTranslatorModeActive = false
                    withFrameNanos { }
                    if (translatorActivationToken == activationToken) {
                        isTranslatorModeActive = true
                    }
                }
            }
        }
    }
    var hasObservedNotificationCount by rememberSaveable { mutableStateOf(false) }
    var previousNotificationCount by rememberSaveable { mutableStateOf(0) }
    var isNotificationBounceActive by rememberSaveable { mutableStateOf(false) }
    var isAboutDialogOpen by rememberSaveable { mutableStateOf(false) }
    var pendingAccountAction by remember { mutableStateOf<AccountLifecycleAction?>(null) }
    var isAccountOperationInProgress by remember { mutableStateOf(false) }
    var accountOperationError by remember { mutableStateOf<String?>(null) }
    var ugcTermsAccepted by remember(currentUserId) { mutableStateOf<Boolean?>(null) }
    var isAcceptingUgcTerms by remember(currentUserId) { mutableStateOf(false) }
    ConfigureAppSystemBars()
    LaunchedEffect(currentUserId) {
        ugcTermsAccepted = if (currentUserId == null) null else
            container.moderationRepository.hasAcceptedTerms().getOrDefault(false)
    }
    LaunchedEffect(isDeviceNetworkAvailable) {
        container.chatRepository.setDeviceNetworkAvailable(isDeviceNetworkAvailable)
        container.userPresenceRepository.setDeviceNetworkAvailable(isDeviceNetworkAvailable)
        if (previousDeviceNetworkAvailable == false && isDeviceNetworkAvailable) {
            feedNetworkReconnectToken = System.currentTimeMillis()
        }
        previousDeviceNetworkAvailable = isDeviceNetworkAvailable
    }
    LaunchedEffect(observedNotificationCount) {
        val currentNotificationCount = observedNotificationCount ?: return@LaunchedEffect
        if (!hasObservedNotificationCount) {
            previousNotificationCount = currentNotificationCount
            hasObservedNotificationCount = true
            return@LaunchedEffect
        }
        val previousCount = previousNotificationCount
        previousNotificationCount = currentNotificationCount
        if (currentNotificationCount > previousCount) {
            isNotificationBounceActive = true
            appContext.playDefaultNotificationSound()
            delay(2_000L)
            isNotificationBounceActive = false
        } else {
            isNotificationBounceActive = false
        }
    }
    val bottomRoutes = setOf(
        AppDestinations.Neighborhoods.route,
        AppDestinations.Feed.route,
        AppDestinations.Official.route,
        AppDestinations.Conversations.route,
        AppDestinations.Profile.route
    )
    val layoutDirection = LocalLayoutDirection.current
    val windowLayoutInfo = rememberQuataWindowLayoutInfo()
    val isLandscapeLayout = windowLayoutInfo.isLandscape
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val translatorViewportKey = windowLayoutInfo.viewportKey
    LaunchedEffect(translatorViewportKey) {
        if (isTranslatorOverlayMounted || isTranslatorModeActive || translatorBackground != null) {
            translatorActivationToken += 1L
            isTranslatorModeActive = false
            translatorBackground = null
            isTranslatorOverlayMounted = false
        }
    }
    val publishEditorRoutes = setOf(
        AppDestinations.CreatePost.route,
        AppDestinations.OfficialPostEditor.route
    )
    val primaryNavigationRoutes = bottomRoutes + publishEditorRoutes
    val showPrimaryNavigation = currentRoute in primaryNavigationRoutes && !isVideoEditorOpen
    val showBottomNavigation = showPrimaryNavigation && !isLandscapeLayout && !isImeVisible
    val useNavigationRail = showAppChrome && isLandscapeLayout
    var lastPrimaryNavigationRoute by rememberSaveable { mutableStateOf(AppDestinations.Feed.route) }
    val navigationChromeRoute = when {
        currentRoute == AppDestinations.CreatePost.route -> AppDestinations.Feed.route
        currentRoute == AppDestinations.OfficialPostEditor.route -> AppDestinations.Official.route
        currentRoute != null && currentRoute in bottomRoutes -> currentRoute
        else -> lastPrimaryNavigationRoute
    }
    var appContentPadding by remember { mutableStateOf(QuataAppContentPadding()) }
    var createPostResetToken by rememberSaveable { mutableStateOf(0) }
    var createPostCancelUploadToken by rememberSaveable { mutableStateOf(0) }
    var feedResetToken by rememberSaveable { mutableStateOf(0) }
    var feedFocusedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var officialFocusedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var chatFocusedMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    var isAuthRequiredPromptOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(currentUserId) {
        startupDestination = if (currentUserId == null) {
            StartupDestination.Main
        } else {
            StartupDestination.Loading
            startupCoordinator.resolve(
                installedVersionCode = BuildConfig.VERSION_CODE.toLong(),
                languageTags = appContext.resources.configuration.locales.languageTags()
            )
        }
    }

    fun finishWhatsNewPresentation() {
        val releases = (startupDestination as? StartupDestination.WhatsNew)?.releases ?: return
        if (isCompletingWhatsNew) return
        isCompletingWhatsNew = true
        startupDestination = StartupDestination.Main
        appScope.launch {
            container.whatsNewRepository.markReleasesSeen(
                upToVersionCode = releases.maxOf { it.versionCode },
                installedVersionCode = BuildConfig.VERSION_CODE.toLong()
            )
            isCompletingWhatsNew = false
        }
    }
    fun requestAuthentication() {
        isAuthRequiredPromptOpen = true
    }

    LaunchedEffect(isAuthenticated) {
        ShareTargetAvailability.setEnabled(appContext, isAuthenticated)
    }

    fun navigateToFeed() {
        val poppedToFeed = navController.popBackStack(AppDestinations.Feed.route, inclusive = false)
        if (!poppedToFeed) {
            navController.navigate(AppDestinations.Feed.route) {
                launchSingleTop = true
            }
        }
    }

    fun navigateToChat(conversationId: String, focusedMessageId: String? = null) {
        if (!isAuthenticated) {
            requestAuthentication()
            navigateToFeed()
            return
        }
        chatFocusedMessageId = focusedMessageId
        navController.navigate(AppDestinations.Chat.createRoute(conversationId)) {
            launchSingleTop = true
        }
    }

    fun navigateBottomRoute(route: String) {
        val requiresAuthentication = route == AppDestinations.Conversations.route ||
            route == AppDestinations.Profile.route
        if (requiresAuthentication && !isAuthenticated) {
            requestAuthentication()
        } else if (route == AppDestinations.CreatePost.route) {
            createPostResetToken += 1
            navController.navigate(AppDestinations.CreatePost.route) {
                popUpTo(AppDestinations.Feed.route) { saveState = false }
                launchSingleTop = false
                restoreState = false
            }
        } else if (route == AppDestinations.Feed.route) {
            feedResetToken += 1
            navigateToFeed()
        } else {
            navController.navigate(route) {
                popUpTo(AppDestinations.Feed.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun handleBottomRoute(route: String) {
        if (currentRoute == AppDestinations.CreatePost.route && isCreatePostUploadInProgress) {
            pendingCreatePostUploadRoute = route
        } else {
            navigateBottomRoute(route)
        }
    }
    val globalProfileViewModel: NeighborhoodsAndroidViewModel = viewModel(
        key = "global_user_profile",
        factory = NeighborhoodsAndroidViewModel.factory(container.neighborhoodRepository)
    )
    val globalProfileState by globalProfileViewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppForeground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner, container.chatRepository) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isAppForeground = true
                    container.chatRepository.setAppForeground(true)
                    container.userPresenceRepository.setAppForeground(true)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    isAppForeground = false
                    container.chatRepository.setAppForeground(false)
                    container.userPresenceRepository.setAppForeground(false)
                }
                Lifecycle.Event.ON_DESTROY -> {
                    container.chatRepository.setAppForeground(false)
                    container.userPresenceRepository.setAppForeground(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(isAppForeground) {
        container.chatRepository.setAppForeground(isAppForeground)
        container.userPresenceRepository.setAppForeground(isAppForeground)
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != null && currentRoute in bottomRoutes) {
            lastPrimaryNavigationRoute = currentRoute
        }
        if (currentRoute != AppDestinations.CreatePost.route) {
            isCreatePostUploadInProgress = false
            pendingCreatePostUploadRoute = null
        }
    }

    LaunchedEffect(incomingLink, currentBackStackEntry) {
        // A cold deep link can arrive before NavHost has installed its graph.
        // Wait for an actual back-stack entry so navigation remains safe on all
        // launch paths (notification, share link, or external browser).
        if (incomingLink != null && currentBackStackEntry == null) return@LaunchedEffect
        val incomingLinkValue = incomingLink?.toString()
        if (incomingLinkValue?.isQuataRichTextEditorQaLink() == true) {
            navController.navigate(AppDestinations.RichTextEditorQa.route) {
                launchSingleTop = true
            }
            onIncomingLinkHandled()
            return@LaunchedEffect
        }

        val chatDeepLink = incomingLinkValue?.quataChatDeepLinkOrNull()
        if (chatDeepLink != null) {
            globalProfileViewModel.closeUserProfile()
            feedFocusedPostId = null
            officialFocusedPostId = null
            chatFocusedMessageId = null
            navigateToChat(chatDeepLink.conversationId, focusedMessageId = chatDeepLink.messageId)
            onIncomingLinkHandled()
            return@LaunchedEffect
        }

        val officialPostId = incomingLinkValue?.quataOfficialPostIdOrNull()
        if (officialPostId != null) {
            officialFocusedPostId = officialPostId
            feedFocusedPostId = null
            globalProfileViewModel.closeUserProfile()
            chatFocusedMessageId = null
            navController.navigate(AppDestinations.Official.route) {
                popUpTo(AppDestinations.Feed.route) { inclusive = false }
                launchSingleTop = true
            }
            onIncomingLinkHandled()
            return@LaunchedEffect
        }

        val postId = incomingLinkValue?.quataPostIdOrNull() ?: return@LaunchedEffect
        feedFocusedPostId = postId
        officialFocusedPostId = null
        globalProfileViewModel.closeUserProfile()
        chatFocusedMessageId = null
        navController.navigate(AppDestinations.Feed.route) {
            popUpTo(AppDestinations.Feed.route) { inclusive = false }
            launchSingleTop = true
        }
        onIncomingLinkHandled()
    }

    CompositionLocalProvider(
        LocalQuataNetworkImageState provides QuataNetworkImageState(
            isNetworkAvailable = isDeviceNetworkAvailable,
            reconnectToken = feedNetworkReconnectToken
        ),
        LocalUserPresence provides container.userPresenceRepository
    ) {
        QuataTranslatorModeProvider(
            registry = translatorRegistry,
            controller = translatorModeController
        ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(template.colors.background)
                .fluidTouchEffect(enabled = touchFlowEnabled)
        ) {
        Scaffold(
            containerColor = template.colors.background,
            contentColor = template.colors.textPrimary,
            topBar = {
                if (showAppChrome) {
                    Column {
                        if (!isLandscapeLayout) {
                            QuataAppTopSpacer(extraHeight = 68.dp)
                        }
                        if (!isAppOnline) {
                            AppOfflineBanner()
                        }
                    }
                }
            },
            bottomBar = {
                if (showBottomNavigation) {
                    QuataBottomBar(currentRoute = navigationChromeRoute, onDestinationClick = ::handleBottomRoute)
                }
            }
        ) { scaffoldPadding ->
            val scaffoldStartPadding = scaffoldPadding.calculateStartPadding(layoutDirection)
            val railStartSafePadding = WindowInsets.safeDrawing
                .asPaddingValues()
                .calculateStartPadding(layoutDirection)
            val padding = if (useNavigationRail) {
                PaddingValues(
                    start = maxOf(scaffoldStartPadding, railStartSafePadding) + QuataNavigationRailWidth,
                    top = scaffoldPadding.calculateTopPadding(),
                    end = scaffoldPadding.calculateEndPadding(layoutDirection),
                    bottom = scaffoldPadding.calculateBottomPadding()
                )
            } else {
                scaffoldPadding
            }
            val latestContentPadding = QuataAppContentPadding(
                start = padding.calculateStartPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
                bottom = padding.calculateBottomPadding()
            )
            SideEffect {
                if (appContentPadding != latestContentPadding) {
                    appContentPadding = latestContentPadding
                }
            }
            Box(Modifier.fillMaxSize()) {
                if (isWhatsNewStartupActive) {
                    when (val destination = startupDestination) {
                        StartupDestination.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(template.colors.background),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = template.colors.accent)
                            }
                        }
                        is StartupDestination.WhatsNew -> {
                            WhatsNewScreen(
                                releases = destination.releases,
                                isCompleting = isCompletingWhatsNew,
                                onComplete = ::finishWhatsNewPresentation,
                                onDismiss = ::finishWhatsNewPresentation,
                                padding = padding
                            )
                        }
                        StartupDestination.Main -> Unit
                    }
                } else {
                NavHost(navController = navController, startDestination = startDestination) {
                composable(AppDestinations.Login.route) {
                    LoginScreen(
                        padding = padding,
                        authRepository = container.authRepository,
                        onGoToRegister = { navController.navigate(AppDestinations.Register.route) },
                        onForgotPassword = { navController.navigate(AppDestinations.ForgotPassword.route) },
                        onLoginSuccess = {
                            navController.navigate(AppDestinations.Feed.route) {
                                popUpTo(AppDestinations.Feed.route) { inclusive = false }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(AppDestinations.Register.route) {
                    RegisterScreen(
                        padding = padding,
                        authRepository = container.authRepository,
                        onBack = { navController.popBackStack() },
                        onRegisterSuccess = {
                            navController.navigate(AppDestinations.Feed.route) {
                                popUpTo(AppDestinations.Feed.route) { inclusive = false }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(AppDestinations.ForgotPassword.route) {
                    ForgotPasswordScreen(
                        padding = padding,
                        authRepository = container.authRepository,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(AppDestinations.ReleaseHistory.route) {
                    ReleaseHistoryScreen(
                        repository = container.whatsNewRepository,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(AppDestinations.Feed.route) {
                    FeedScreen(
                        padding = padding,
                        feedRepository = container.feedRepository,
                        onOpenUserProfile = { userId ->
                            globalProfileViewModel.openUserProfile(userId)
                        },
                        currentUserId = container.sessionManager.currentSession()?.userId,
                        openingProfileUserId = globalProfileState.openingProfileUserId,
                        focusedPostId = feedFocusedPostId,
                        feedResetToken = feedResetToken,
                        networkReconnectToken = feedNetworkReconnectToken,
                        isNetworkAvailable = isDeviceNetworkAvailable,
                        isAppForeground = isAppForeground,
                        onFocusedPostHandled = { feedFocusedPostId = null },
                        onAuthRequired = { requestAuthentication() },
                        onCreatePost = {
                            if (isAuthenticated) navigateBottomRoute(AppDestinations.CreatePost.route) else requestAuthentication()
                        },
                        onReportComment = { commentId ->
                            appScope.launch {
                                container.moderationRepository.report(ModerationTarget.CommunityComment, commentId)
                                    .onSuccess { Toast.makeText(appContext, R.string.moderation_report_sent, Toast.LENGTH_SHORT).show() }
                                    .onFailure { Toast.makeText(appContext, it.toUserFacingMessage(appContext), Toast.LENGTH_LONG).show() }
                            }
                        },
                        onLandscapeCommentsOverlayActiveChange = { isFeedCommentsOverlayVisible = it }
                    )
                }

                composable(AppDestinations.Official.route) {
                    OfficialFeedScreen(
                        padding = padding,
                        repository = container.officialRepository,
                        currentUserId = container.sessionManager.currentSession()?.userId,
                        focusedPostId = officialFocusedPostId,
                        onFocusedPostHandled = { officialFocusedPostId = null },
                        onAuthRequired = { requestAuthentication() },
                        onOpenUserProfile = { userId ->
                            globalProfileViewModel.openUserProfile(userId)
                        },
                        onCreateOfficialPost = {
                            navController.navigate(AppDestinations.OfficialPostEditor.route)
                        },
                        onReportComment = { commentId ->
                            appScope.launch {
                                container.moderationRepository.report(ModerationTarget.OfficialComment, commentId)
                                    .onSuccess { Toast.makeText(appContext, R.string.moderation_report_sent, Toast.LENGTH_SHORT).show() }
                                    .onFailure { Toast.makeText(appContext, it.toUserFacingMessage(appContext), Toast.LENGTH_LONG).show() }
                            }
                        }
                    )
                }

                composable(AppDestinations.OfficialPostEditor.route) {
                    OfficialPostEditorRoute(
                        padding = padding,
                        repository = container.officialRepository,
                        onPublished = { postId ->
                            officialFocusedPostId = postId
                            navController.navigate(AppDestinations.Official.route) {
                                popUpTo(AppDestinations.Official.route) {
                                    inclusive = false
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                        onFullscreenEditorVisibilityChange = { isVideoEditorOpen = it }
                    )
                }

                composable(AppDestinations.RichTextEditorQa.route) {
                    RichTextEditorQaScreen(
                        padding = padding,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(AppDestinations.Neighborhoods.route) {
                    NeighborhoodsScreen(
                        padding = padding,
                        repository = container.neighborhoodRepository,
                        currentUserId = container.sessionManager.currentSession()?.userId,
                        openingProfileUserId = globalProfileState.openingProfileUserId,
                        onOpenConversation = { id ->
                            navigateToChat(id)
                        },
                        onOpenUserProfile = { userId ->
                            globalProfileViewModel.openUserProfile(userId)
                        },
                        onAuthRequired = { requestAuthentication() }
                    )
                }

                composable(AppDestinations.CreatePost.route) {
                    CreatePostScreen(
                        padding = padding,
                        repository = container.postComposerRepository,
                        resetToken = createPostResetToken,
                        cancelUploadToken = createPostCancelUploadToken,
                        canPublish = isAuthenticated,
                        onAuthRequired = { requestAuthentication() },
                        onPostCreated = { postId ->
                            isCreatePostUploadInProgress = false
                            isVideoEditorOpen = false
                            pendingCreatePostUploadRoute = null
                            feedFocusedPostId = postId
                            navController.navigate(AppDestinations.Feed.route) {
                                popUpTo(AppDestinations.Feed.route) {
                                    inclusive = false
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                        onVideoEditorVisibilityChange = { isVideoEditorOpen = it },
                        onUploadStateChange = { isCreatePostUploadInProgress = it }
                    )
                }

                composable(AppDestinations.Conversations.route) {
                    if (!isAuthenticated) {
                        LaunchedEffect(Unit) {
                            requestAuthentication()
                            navigateToFeed()
                        }
                    } else {
                        ConversationsScreen(
                            padding = padding,
                            repository = container.chatRepository,
                            openingProfileUserId = globalProfileState.openingProfileUserId,
                            onOpenUserProfile = { userId ->
                                globalProfileViewModel.openUserProfile(userId)
                            },
                            onOpenFavorites = {
                                navigateToChat(AppDestinations.FavoriteMessagesConversationId)
                            },
                            onOpenConversation = { id ->
                                navigateToChat(id)
                            }
                        )
                    }
                }

                composable(
                    route = AppDestinations.Chat.route,
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                ) { entry ->
                    val conversationId = entry.arguments?.getString("conversationId")?.let(Uri::decode) ?: ""
                    if (!isAuthenticated) {
                        LaunchedEffect(conversationId) {
                            requestAuthentication()
                            navigateToFeed()
                        }
                    } else {
                        ChatScreen(
                            padding = padding,
                            conversationId = conversationId,
                            repository = container.chatRepository,
                            openingProfileUserId = globalProfileState.openingProfileUserId,
                            onOpenUserProfile = { userId ->
                                globalProfileViewModel.openUserProfile(userId)
                            },
                            onOpenConversation = { id ->
                                navigateToChat(id)
                            },
                            focusedMessageId = chatFocusedMessageId,
                            onFocusedMessageHandled = { chatFocusedMessageId = null },
                            onOpenMessageConversation = { targetConversationId, messageId ->
                                navigateToChat(targetConversationId, focusedMessageId = messageId)
                            },
                            onBack = { navController.popBackStack() },
                            compactHeader = isLandscapeLayout
                        )
                    }
                }

                composable(AppDestinations.Notifications.route) {
                    NotificationsScreen(
                        padding = padding,
                        repository = container.notificationsRepository,
                        onBack = { navController.popBackStack() },
                        onOpenConversation = { id ->
                            navigateToChat(id)
                        }
                    )
                }

                composable(AppDestinations.Profile.route) {
                    if (!isAuthenticated) {
                        LaunchedEffect(Unit) {
                            requestAuthentication()
                            navigateToFeed()
                        }
                    } else {
                        ProfileScreen(
                            padding = padding,
                            repository = container.profileRepository,
                            profileId = currentUserId.orEmpty(),
                            touchFlowEnabled = touchFlowEnabled,
                            onTouchFlowEnabledChange = { enabled ->
                                container.touchFlowPreferences.setEnabled(currentUserId, enabled)
                            },
                            themeMode = themeMode,
                            onThemeModeChange = container.themePreferences::setThemeMode,
                            networkReconnectToken = feedNetworkReconnectToken,
                            onFullscreenEditorVisibilityChange = { isVideoEditorOpen = it },
                            onLogout = {
                                appScope.launch {
                                    container.authRepository.logout()
                                    navController.navigate(AppDestinations.Feed.route) {
                                        popUpTo(0)
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onDeactivateAccount = {
                                accountOperationError = null
                                pendingAccountAction = AccountLifecycleAction.Deactivate
                            },
                            onDeleteAccountData = {
                                accountOperationError = null
                                pendingAccountAction = AccountLifecycleAction.DeleteData
                            },
                            onProfileSaved = {
                                navController.navigate(AppDestinations.Feed.route) {
                                    popUpTo(AppDestinations.Feed.route) { inclusive = false }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }

            }
                if (useNavigationRail) {
                    QuataNavigationRail(
                        currentRoute = navigationChromeRoute,
                        onDestinationClick = ::handleBottomRoute,
                        notificationCount = notificationCount,
                        isNotificationBouncing = isNotificationBounceActive,
                        onNotificationsClick = {
                            navController.navigate(AppDestinations.Notifications.route) {
                                popUpTo(AppDestinations.Feed.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                    )
                }
                }
            }
        }

        if (showAppChrome && !isLandscapeLayout) {
            val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
            val statusBarTop = safeDrawingPadding.calculateTopPadding()
            QuataAppHeaderActions(
                notificationCount = notificationCount,
                isBouncing = isNotificationBounceActive,
                onLogoClick = { isAboutDialogOpen = true },
                onNotificationsClick = {
                    navController.navigate(AppDestinations.Notifications.route) {
                        popUpTo(AppDestinations.Feed.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = statusBarTop + 14.dp,
                        start = 16.dp + safeDrawingPadding.calculateStartPadding(layoutDirection)
                    )
            )
            GlobalSosButton(
                container = container,
                isAuthenticated = isAuthenticated,
                onAuthRequired = { requestAuthentication() },
                layoutPadding = appContentPadding.toPaddingValues(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = statusBarTop + 14.dp,
                        end = 16.dp + safeDrawingPadding.calculateEndPadding(layoutDirection)
                    )
            )
        } else if (showAppChrome) {
            val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
            val statusBarTop = safeDrawingPadding.calculateTopPadding()
            GlobalSosButton(
                container = container,
                isAuthenticated = isAuthenticated,
                onAuthRequired = { requestAuthentication() },
                layoutPadding = appContentPadding.toPaddingValues(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = statusBarTop,
                        end = 16.dp + safeDrawingPadding.calculateEndPadding(layoutDirection)
                    )
            )
        }

        globalProfileState.selectedProfile?.let { profile ->
            CommunityProfileScreen(
                padding = PaddingValues(0.dp),
                profile = profile,
                currentUserId = container.sessionManager.currentSession()?.userId,
                isOpeningChat = globalProfileState.openingPrivateChatUserId == profile.user.id,
                isRefreshingProfile = globalProfileState.refreshingProfileUserId == profile.user.id,
                followingUserId = globalProfileState.followingUserId,
                roleUpdatingUserId = globalProfileState.roleUpdatingUserId,
                currentUserIsAdmin = globalProfileState.currentUserIsAdmin,
                chatError = globalProfileState.error,
                onAuthRequired = { requestAuthentication() },
                onReportPost = { postId ->
                    if (isAuthenticated) globalProfileViewModel.reportProfilePost(postId) else requestAuthentication()
                },
                onReportProfile = { profileId ->
                    appScope.launch {
                        container.moderationRepository.report(ModerationTarget.Profile, profileId)
                            .onSuccess { Toast.makeText(appContext, R.string.moderation_report_sent, Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(appContext, it.toUserFacingMessage(appContext), Toast.LENGTH_LONG).show() }
                    }
                },
                onBlockProfile = { profileId ->
                    appScope.launch {
                        container.moderationRepository.blockProfile(profileId)
                            .onSuccess {
                                Toast.makeText(appContext, R.string.moderation_profile_blocked, Toast.LENGTH_SHORT).show()
                                globalProfileViewModel.closeUserProfile()
                            }
                            .onFailure { Toast.makeText(appContext, it.toUserFacingMessage(appContext), Toast.LENGTH_LONG).show() }
                    }
                },
                onBack = { globalProfileViewModel.closeUserProfile() },
                onFollow = {
                    if (isAuthenticated) globalProfileViewModel.toggleFollowUser(profile.user.id) else requestAuthentication()
                },
                onFollowUser = { userId ->
                    if (isAuthenticated) globalProfileViewModel.toggleFollowUser(userId) else requestAuthentication()
                },
                onSetUserRoles = { userId, isAdmin, isOfficial ->
                    if (isAuthenticated) globalProfileViewModel.setUserRoles(userId, isAdmin, isOfficial) else requestAuthentication()
                },
                onOpenPrivateChat = { userId ->
                    if (!isAuthenticated) {
                        requestAuthentication()
                    } else {
                        globalProfileViewModel.openPrivateChat(userId) { conversationId ->
                            globalProfileViewModel.closeUserProfile()
                            chatFocusedMessageId = null
                            if (currentRoute != AppDestinations.Chat.route || currentConversationId != conversationId) {
                                navigateToChat(conversationId)
                            }
                        }
                    }
                },
                onOpenUserProfile = { userId -> globalProfileViewModel.openUserProfile(userId) },
                openingProfileUserId = globalProfileState.openingProfileUserId
            )
        }

        if (isAuthRequiredPromptOpen) {
            AuthRequiredDialog(
                onDismiss = { isAuthRequiredPromptOpen = false },
                onCreateAccount = {
                    isAuthRequiredPromptOpen = false
                    navController.navigate(AppDestinations.Register.route) {
                        launchSingleTop = true
                    }
                },
                onLogin = {
                    isAuthRequiredPromptOpen = false
                    navController.navigate(AppDestinations.Login.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        if (
            incomingShare != null &&
            isAuthenticated &&
            ugcTermsAccepted == true &&
            !isWhatsNewStartupActive
        ) {
            ShareToQuataDialog(
                payload = incomingShare,
                repository = container.chatRepository,
                onDismiss = onIncomingShareHandled,
                onSent = { conversationId ->
                    Toast.makeText(appContext, R.string.share_to_quata_sent, Toast.LENGTH_SHORT).show()
                    onIncomingShareHandled()
                    conversationId?.let(::navigateToChat)
                }
            )
        }

        pendingCreatePostUploadRoute?.let { targetRoute ->
            AlertDialog(
                onDismissRequest = { pendingCreatePostUploadRoute = null },
                title = { Text(stringResource(R.string.composer_cancel_upload_title)) },
                text = { Text(stringResource(R.string.composer_cancel_upload_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingCreatePostUploadRoute = null
                            createPostCancelUploadToken += 1
                            isCreatePostUploadInProgress = false
                            navigateBottomRoute(targetRoute)
                        }
                    ) {
                        Text(stringResource(R.string.composer_cancel_upload_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCreatePostUploadRoute = null }) {
                        Text(stringResource(R.string.composer_cancel_upload_keep))
                    }
                }
            )
        }

        if (isAboutDialogOpen) {
            AboutQuataDialog(
                onDismiss = { isAboutDialogOpen = false },
                onOpenReleaseHistory = {
                    isAboutDialogOpen = false
                    navController.navigate(AppDestinations.ReleaseHistory.route)
                }
            )
        }

        pendingAccountAction?.let { action ->
            AccountLifecycleConfirmationDialog(
                action = action,
                isWorking = isAccountOperationInProgress,
                errorMessage = accountOperationError,
                onDismiss = {
                    if (!isAccountOperationInProgress) {
                        accountOperationError = null
                        pendingAccountAction = null
                    }
                },
                onConfirm = { password ->
                    if (!isAccountOperationInProgress) {
                        isAccountOperationInProgress = true
                        accountOperationError = null
                        appScope.launch {
                            val result = when (action) {
                                AccountLifecycleAction.Deactivate -> container.authRepository.deactivateAccount(password)
                                AccountLifecycleAction.DeleteData -> container.authRepository.deleteAccountData(password)
                            }
                            isAccountOperationInProgress = false
                            result.onSuccess {
                                val message = if (action == AccountLifecycleAction.Deactivate) {
                                    R.string.account_deactivate_success
                                } else {
                                    R.string.account_delete_success
                                }
                                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                                pendingAccountAction = null
                                isAboutDialogOpen = false
                                ugcTermsAccepted = null
                                navController.navigate(AppDestinations.Feed.route) { popUpTo(0) }
                            }.onFailure { error ->
                                accountOperationError = error.toUserFacingMessage(appContext)
                            }
                        }
                    }
                }
            )
        }

        if (currentUserId != null && ugcTermsAccepted == false) {
            UgcTermsDialog(
                isAccepting = isAcceptingUgcTerms,
                onAccept = {
                    if (!isAcceptingUgcTerms) {
                        isAcceptingUgcTerms = true
                        // Local acceptance always unlocks the app; synchronization is retried by WorkManager.
                        ugcTermsAccepted = true
                        appScope.launch {
                            container.moderationRepository.acceptTerms()
                            isAcceptingUgcTerms = false
                        }
                    }
                },
                onLogout = {
                    appScope.launch {
                        container.authRepository.logout()
                        ugcTermsAccepted = null
                        navController.navigate(AppDestinations.Feed.route) { popUpTo(0) }
                    }
                }
            )
        }

        if (isFeedCommentsOverlayVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.30f))
                    .zIndex(9_000f)
            )
        }

        if (isTranslatorOverlayMounted && translatorOverlaySource == QuataTranslatorOverlaySource.Chat) {
            AnimatedVisibility(
                visible = isTranslatorModeActive,
                enter = fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = fadeOut(animationSpec = tween(durationMillis = 120)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10_000f)
            ) {
                QuataTranslatorOverlay(
                    registry = translatorRegistry,
                    background = translatorBackground,
                    source = translatorOverlaySource,
                    onDismiss = {
                        isTranslatorModeActive = false
                        appScope.launch {
                            delay(140)
                            if (!isTranslatorModeActive) {
                                translatorBackground = null
                                isTranslatorOverlayMounted = false
                            }
                        }
                    }
                )
            }
        }

        if (isTranslatorOverlayMounted && translatorOverlaySource == QuataTranslatorOverlaySource.Comments) {
            AnimatedVisibility(
                visible = isTranslatorModeActive,
                enter = fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = fadeOut(animationSpec = tween(durationMillis = 120)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(9_999f)
            ) {
                QuataTranslatorOverlayBackdrop(
                    background = translatorBackground,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Dialog(
                onDismissRequest = {
                    isTranslatorModeActive = false
                    appScope.launch {
                        delay(140)
                        if (!isTranslatorModeActive) {
                            translatorBackground = null
                            isTranslatorOverlayMounted = false
                        }
                    }
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false,
                    decorFitsSystemWindows = false
                )
            ) {
                ConfigureTranslatorDialogWindow()
                AnimatedVisibility(
                    visible = isTranslatorModeActive,
                    enter = fadeIn(animationSpec = tween(durationMillis = 180)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 120)),
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10_000f)
                ) {
                    QuataTranslatorOverlay(
                        registry = translatorRegistry,
                        background = translatorBackground,
                        source = translatorOverlaySource,
                        onDismiss = {
                            isTranslatorModeActive = false
                            appScope.launch {
                                delay(140)
                                if (!isTranslatorModeActive) {
                                    translatorBackground = null
                                    isTranslatorOverlayMounted = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    }
    }
}

private fun android.os.LocaleList.languageTags(): List<String> =
    List(size()) { index -> get(index).toLanguageTag() }

@Composable
private fun ConfigureAppSystemBars() {
    val context = LocalContext.current
    val template = quataTheme()
    SideEffect {
        (context.findActivity() as? ComponentActivity)?.applyQuataSystemBars(template)
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun ConfigureTranslatorDialogWindow() {
    val view = LocalView.current
    val template = quataTheme()
    DisposableEffect(view, template.id) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window == null) {
            return@DisposableEffect onDispose {}
        }
        val originalAnimations = window.attributes.windowAnimations
        val originalDimAmount = window.attributes.dimAmount
        val originalNavigationBarContrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else {
            null
        }
        val systemBarsController = WindowInsetsControllerCompat(window, window.decorView)
        val originalLightNavigationBars = systemBarsController.isAppearanceLightNavigationBars
        val originalLightStatusBars = systemBarsController.isAppearanceLightStatusBars
        val originalHadDim = window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0
        val originalGravity = window.attributes.gravity
        val originalX = window.attributes.x
        val originalY = window.attributes.y
        val originalWidth = window.attributes.width
        val originalHeight = window.attributes.height
        val originalFlags = window.attributes.flags
        val originalCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode
        } else {
            null
        }
        val fullscreenLayout = {
            val attributes = window.attributes
            attributes.width = WindowManager.LayoutParams.MATCH_PARENT
            attributes.height = WindowManager.LayoutParams.MATCH_PARENT
            attributes.gravity = Gravity.TOP or Gravity.START
            attributes.x = 0
            attributes.y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            window.attributes = attributes
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.decorView.setPadding(0, 0, 0, 0)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setWindowAnimations(0)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        systemBarsController.isAppearanceLightNavigationBars = template.resolvedTheme == QuataResolvedTheme.Light
        systemBarsController.isAppearanceLightStatusBars = template.resolvedTheme == QuataResolvedTheme.Light
        window.setDimAmount(0f)
        window.setGravity(Gravity.TOP or Gravity.START)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        fullscreenLayout()
        window.decorView.post { fullscreenLayout() }
        onDispose {
            window.setWindowAnimations(originalAnimations)
            window.setDimAmount(originalDimAmount)
            val attributes = window.attributes
            attributes.gravity = originalGravity
            attributes.x = originalX
            attributes.y = originalY
            attributes.width = originalWidth
            attributes.height = originalHeight
            attributes.flags = originalFlags
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                attributes.layoutInDisplayCutoutMode = originalCutoutMode
            }
            window.attributes = attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (originalNavigationBarContrast != null) {
                    window.isNavigationBarContrastEnforced = originalNavigationBarContrast
                }
            }
            systemBarsController.isAppearanceLightNavigationBars = originalLightNavigationBars
            systemBarsController.isAppearanceLightStatusBars = originalLightStatusBars
            if (originalHadDim) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
    }
}

@Composable
private fun AboutQuataDialog(
    onDismiss: () -> Unit,
    onOpenReleaseHistory: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.about_title),
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(
                        R.string.about_version,
                        BuildConfig.VERSION_NAME
                    )
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.about_version_date,
                        BuildConfig.APP_VERSION_DATE
                    )
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.about_body))
                Spacer(Modifier.height(12.dp))
                LegalDocumentLinkButton(R.string.legal_privacy, LegalDocument.Privacy, context)
                LegalDocumentLinkButton(R.string.legal_child_safety, LegalDocument.ChildSafety, context)
            }
        },
        confirmButton = {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onOpenReleaseHistory) {
                    Text(stringResource(R.string.release_history_short))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
    )
}

private enum class AccountLifecycleAction { Deactivate, DeleteData }

@Composable
private fun AccountLifecycleConfirmationDialog(
    action: AccountLifecycleAction,
    isWorking: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var confirmation by remember(action) { mutableStateOf("") }
    var password by remember(action) { mutableStateOf("") }
    val isDeletion = action == AccountLifecycleAction.DeleteData
    val requiredConfirmation = stringResource(R.string.account_delete_confirmation_word)
    val canConfirm = !isWorking && password.isNotBlank() &&
        (!isDeletion || confirmation.trim().equals(requiredConfirmation, ignoreCase = true))
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !isWorking,
            dismissOnClickOutside = !isWorking
        ),
        title = {
            Text(
                stringResource(
                    if (isDeletion) R.string.account_delete_confirm_title
                    else R.string.account_deactivate_confirm_title
                ),
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(
                        if (isDeletion) R.string.account_delete_confirm_body
                        else R.string.account_deactivate_confirm_body
                    )
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.account_password_prompt))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !isWorking,
                    singleLine = true,
                    label = { Text(stringResource(R.string.auth_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isDeletion) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.account_delete_type_confirmation, requiredConfirmation))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        enabled = !isWorking,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                errorMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color.Red)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isWorking, onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        confirmButton = {
            TextButton(enabled = canConfirm, onClick = { onConfirm(password) }) {
                if (isWorking) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(
                            if (isDeletion) R.string.account_delete_confirm_action
                            else R.string.account_deactivate_confirm_action
                        )
                    )
                }
            }
        }
    )
}

@Composable
private fun UgcTermsDialog(
    isAccepting: Boolean,
    onAccept: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.ugc_terms_title), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(stringResource(R.string.ugc_terms_body))
                Spacer(Modifier.height(8.dp))
                LegalDocumentLinkButton(R.string.legal_child_safety, LegalDocument.ChildSafety, context)
                LegalDocumentLinkButton(R.string.legal_privacy, LegalDocument.Privacy, context)
            }
        },
        dismissButton = {
            TextButton(enabled = !isAccepting, onClick = onLogout) {
                Text(stringResource(R.string.ugc_terms_logout))
            }
        },
        confirmButton = {
            TextButton(enabled = !isAccepting, onClick = onAccept) {
                Text(stringResource(if (isAccepting) R.string.ugc_terms_accepting else R.string.ugc_terms_accept))
            }
        }
    )
}

@Composable
private fun LegalLinkButton(label: Int, url: String, context: Context) {
    TextButton(
        onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(label), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LegalDocumentLinkButton(label: Int, document: LegalDocument, context: Context) {
    val isDarkMode = quataTheme().resolvedTheme != QuataResolvedTheme.Light
    TextButton(
        onClick = { LegalDocuments.open(context, document, isDarkMode) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(label), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AuthRequiredDialog(
    onDismiss: () -> Unit,
    onCreateAccount: () -> Unit,
    onLogin: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.auth_required_title),
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.auth_required_intro))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.auth_required_send_messages))
                Text(stringResource(R.string.auth_required_comment_posts))
                Text(stringResource(R.string.auth_required_create_content))
                Text(stringResource(R.string.auth_required_follow_communities))
                Text(stringResource(R.string.auth_required_configure_sos))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.auth_required_outro))
            }
        },
        dismissButton = {
            TextButton(onClick = onCreateAccount) {
                Text(stringResource(R.string.auth_required_create_account))
            }
        },
        confirmButton = {
            TextButton(onClick = onLogin) {
                Text(stringResource(R.string.auth_required_login))
            }
        }
    )
}

@Composable
private fun rememberDeviceNetworkAvailable(): Boolean {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val initialAvailable = remember(context) { context.hasUsableNetwork() }
    var isAvailable by remember(context) { mutableStateOf(initialAvailable) }
    val reconciler = remember(context, lifecycleOwner) {
        ForegroundConnectivityReconciler(
            initialAvailable = initialAvailable,
            initiallyForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
    }
    DisposableEffect(context, lifecycleOwner, reconciler) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        if (connectivityManager == null) return@DisposableEffect onDispose {}
        val mainHandler = Handler(Looper.getMainLooper())
        var reconciliationGeneration = 0L
        var disposed = false

        fun publishNetworkObservation() {
            mainHandler.post {
                if (disposed) return@post
                reconciler.onNetworkObservation(context.hasUsableNetwork())?.let { observed ->
                    isAvailable = observed
                }
            }
        }

        fun reconcileOnForeground() {
            reconciliationGeneration += 1L
            val generation = reconciliationGeneration
            isAvailable = reconciler.onForeground(context.hasUsableNetwork())
            FOREGROUND_NETWORK_RECHECK_DELAYS_MILLIS.forEach { delayMillis ->
                mainHandler.postDelayed({
                    if (disposed || generation != reconciliationGeneration) return@postDelayed
                    reconciler.onNetworkObservation(context.hasUsableNetwork())?.let { observed ->
                        isAvailable = observed
                    }
                }, delayMillis)
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> reconcileOnForeground()
                Lifecycle.Event.ON_STOP -> {
                    reconciliationGeneration += 1L
                    reconciler.onBackground()
                }
                else -> Unit
            }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                publishNetworkObservation()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                publishNetworkObservation()
            }

            override fun onLost(network: Network) {
                publishNetworkObservation()
            }

            override fun onUnavailable() {
                publishNetworkObservation()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            reconcileOnForeground()
        } else {
            reconciler.onBackground()
        }
        val callbackRegistered = runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
        }.isSuccess
        onDispose {
            disposed = true
            reconciliationGeneration += 1L
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            if (callbackRegistered) {
                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            }
        }
    }
    return isAvailable
}

private val FOREGROUND_NETWORK_RECHECK_DELAYS_MILLIS = longArrayOf(250L, 1_000L, 2_500L)

private fun Context.hasUsableNetwork(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return true
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun QuataAppTopSpacer(extraHeight: Dp) {
    val template = quataTheme()
    val statusBarTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    Surface(
        color = template.colors.topChrome,
        contentColor = template.colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .height(statusBarTop + extraHeight)
    ) {}
}

@Composable
private fun AppOfflineBanner() {
    Surface(
        color = Color(0xFFB3261E),
        contentColor = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.app_offline_banner),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun QuataAppHeaderActions(
    notificationCount: Int,
    isBouncing: Boolean,
    onLogoClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    val scale = if (isBouncing) {
        val bounceTransition = rememberInfiniteTransition(label = "notification_bounce")
        val bounceScale by bounceTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 260),
                repeatMode = RepeatMode.Reverse
            ),
            label = "notification_bounce_scale"
        )
        bounceScale
    } else {
        1f
    }
    Box(
        modifier = modifier.size(width = 92.dp, height = 36.dp)
    ) {
        val badgeShape = RoundedCornerShape(10.dp)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(32.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = badgeShape,
                    ambientColor = Color(0x40FF6A00),
                    spotColor = Color(0x40FF6A00)
                )
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFF6A00), Color(0xFFFF7F1A))
                    ),
                    shape = badgeShape
                )
                .clickable(onClick = onLogoClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Q\u0308",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                lineHeight = 17.sp,
                letterSpacing = (-0.6).sp
            )
        }
        CompactIconButton(
            onClick = onNotificationsClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 54.dp, y = 0.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(36.dp)
        ) {
            BadgedBox(
                badge = {
                    if (notificationCount > 0) {
                        Badge(
                            containerColor = template.colors.sos,
                            modifier = Modifier.size(14.dp)
                        ) {
                            Text(notificationCount.coerceAtMost(99).toString(), color = Color.White, fontSize = template.textSizes.badge)
                        }
                    }
                }
            ) {
                CompactIcon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.notifications_title), tint = template.colors.textPrimary)
            }
        }
    }
}

@Composable
private fun GlobalSosButton(
    container: AppContainer,
    isAuthenticated: Boolean,
    onAuthRequired: () -> Unit,
    layoutPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
 ) {
    if (!isAuthenticated) {
        GlobalSosButtonSurface(
            modifier = modifier,
            isSendingSos = false,
            onClick = onAuthRequired
        )
        return
    }

    AuthenticatedGlobalSosButton(
        container = container,
        layoutPadding = layoutPadding,
        modifier = modifier
    )
}

@Composable
private fun AuthenticatedGlobalSosButton(
    container: AppContainer,
    layoutPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
    profileViewModel: ProfileAndroidViewModel = viewModel(
        key = "global_sos_profile",
        factory = ProfileAndroidViewModel.Factory(container.profileRepository)
    )
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by profileViewModel.uiState.collectAsState()
    var isConfigOpen by rememberSaveable { mutableStateOf(false) }
    var configProfile by remember { mutableStateOf<UserProfile?>(null) }
    var configContactIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var configMessage by rememberSaveable { mutableStateOf("") }
    var configMessageIsDefault by rememberSaveable { mutableStateOf(true) }
    var configCandidates by remember { mutableStateOf<List<EmergencyContactCandidate>>(emptyList()) }
    var isSavingSosConfig by rememberSaveable { mutableStateOf(false) }
    var pendingProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isSendingSos by rememberSaveable { mutableStateOf(false) }
    val sosPulseScale = if (isSendingSos) {
        val sosPulseTransition = rememberInfiniteTransition(label = "sos_pulse")
        val scale by sosPulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sos_pulse_scale"
        )
        scale
    } else {
        1f
    }

    fun buildSosMessage(profile: UserProfile, location: Location?, isLocationUpdate: Boolean = false): String =
        buildSosShortcode(
            kind = if (isLocationUpdate) SosShortcodeKind.LocationUpdate else SosShortcodeKind.Alert,
            senderName = profile.displayName,
            customMessage = profile.emergencyMessage.takeUnless { profile.emergencyMessageIsDefault },
            latitude = location?.latitude,
            longitude = location?.longitude,
            ageMillis = location?.sosAgeMillis(),
            accuracyMeters = location?.takeIf { it.hasAccuracy() }?.accuracy?.toDouble(),
            speedKmh = location?.takeIf { it.hasSpeed() }?.speed?.times(3.6f)?.toDouble()
        )

    fun sendSos(profile: UserProfile, location: Location?) {
        if (isSendingSos) return
        if (QuataProximityState.isNear()) {
            Toast.makeText(context, context.getString(R.string.sos_blocked_by_proximity), Toast.LENGTH_SHORT).show()
            pendingProfile = null
            return
        }
        isSendingSos = true
        scope.launch {
            if (QuataProximityState.isNear()) {
                Toast.makeText(context, context.getString(R.string.sos_blocked_by_proximity), Toast.LENGTH_SHORT).show()
                isSendingSos = false
                pendingProfile = null
                return@launch
            }
            val shouldRefreshPreciseLocation = location == null || location.isOlderThanSosFreshness()
            Log.d(
                SosLocationLogTag,
                "Sending immediate SOS hasLocation=${location != null} refreshPrecise=$shouldRefreshPreciseLocation"
            )
            container.chatRepository.sendSosMessage(
                contactIds = profile.emergencyContactIds,
                text = buildSosMessage(profile, location),
                lat = location?.latitude,
                lng = location?.longitude,
                accuracy = location?.takeIf { it.hasAccuracy() }?.accuracy?.toDouble()
            ).onSuccess { conversationId ->
                Toast.makeText(context, context.getString(R.string.sos_sent), Toast.LENGTH_SHORT).show()
                if (shouldRefreshPreciseLocation) {
                    Log.d(SosLocationLogTag, "Starting resilient SOS location recovery")
                    runCatching {
                        SosLocationRecoveryService.start(
                            context = context,
                            conversationId = conversationId,
                            expectedProfileId = container.sessionManager.currentSession()?.userId.orEmpty(),
                            senderName = profile.displayName,
                            customMessage = profile.emergencyMessage.takeUnless { profile.emergencyMessageIsDefault },
                            initialLocationTimeMillis = location?.time ?: 0L
                        )
                    }.onFailure { error ->
                        Log.w(SosLocationLogTag, "Could not start SOS location recovery", error)
                    }
                }
            }.onFailure { error ->
                val message = if (error is SosRateLimitException) {
                    context.getString(R.string.sos_recently_sent, error.remainingMillis.formatSosRemaining())
                } else {
                    error.toUserFacingMessage(context, R.string.sos_send_error)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            isSendingSos = false
        }
    }

    lateinit var locationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    fun requestLocation(profile: UserProfile) {
        pendingProfile = profile
        if (context.hasQuataLocationPermission()) {
            scope.launch {
                sendSos(profile, context.quataLastLocation())
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingProfile?.let { requestLocation(it) }
    }
    locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        pendingProfile?.let { profile ->
            scope.launch {
                sendSos(profile, if (granted) context.quataLastLocation() else null)
            }
            pendingProfile = null
        }
    }

    fun openSosConfig(profile: UserProfile, candidates: List<EmergencyContactCandidate>) {
        configProfile = profile
        configContactIds = profile.emergencyContactIds.distinct().take(5)
        configMessage = profile.emergencyMessage
        configMessageIsDefault = profile.emergencyMessageIsDefault
        configCandidates = candidates
        isSavingSosConfig = false
        isConfigOpen = true
    }

    fun continueSos(profile: UserProfile) {
        if (profile.emergencyContactIds.isEmpty()) return
        pendingProfile = profile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestLocation(profile)
        }
    }

    fun startSos(fallbackProfile: UserProfile) {
        if (isSendingSos) return
        isSendingSos = true
        scope.launch {
            val latestModel = container.profileRepository.getProfileEditModel().getOrNull()
            val latestProfile = latestModel?.profile ?: fallbackProfile
            val candidates = latestModel?.config?.emergencyCandidates ?: state.emergencyCandidates
            isSendingSos = false
            if (latestProfile.emergencyContactIds.isEmpty()) {
                openSosConfig(latestProfile, candidates)
                return@launch
            }
            continueSos(latestProfile)
        }
    }

    fun saveSosConfigAndMaybeSend(profile: UserProfile) {
        if (isSavingSosConfig) return
        val selectedIds = configContactIds.distinct().take(5)
        val message = configMessage
        val messageIsDefault = configMessageIsDefault
        isSavingSosConfig = true
        scope.launch {
            container.profileRepository.saveEmergencySettings(
                contactIds = selectedIds,
                message = message,
                messageIsDefault = messageIsDefault
            ).onSuccess {
                isSavingSosConfig = false
                isConfigOpen = false
                profileViewModel.onEvent(ProfileUiEvent.Refresh)
                val savedProfile = profile.copy(
                    emergencyContactIds = selectedIds,
                    emergencyMessage = message,
                    emergencyMessageIsDefault = messageIsDefault
                )
                if (selectedIds.isNotEmpty()) {
                    continueSos(savedProfile)
                } else {
                    Toast.makeText(context, context.getString(R.string.profile_emergency_contacts_updated), Toast.LENGTH_SHORT).show()
                }
            }.onFailure { error ->
                isSavingSosConfig = false
                Toast.makeText(
                    context,
                    error.toUserFacingMessage(context, R.string.profile_save_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun toggleSosConfigContact(contactId: String) {
        val selected = configContactIds.distinct().take(5).toMutableList()
        if (selected.contains(contactId)) {
            selected.remove(contactId)
        } else if (selected.size < 5) {
            selected.add(contactId)
        }
        configContactIds = selected
    }

    GlobalSosButtonSurface(
        modifier = modifier,
        isSendingSos = isSendingSos,
        pulseScale = sosPulseScale,
        onClick = {
            if (isSendingSos) return@GlobalSosButtonSurface
            val profile = state.profile ?: return@GlobalSosButtonSurface
            startSos(profile)
        }
    )

    val profile = configProfile
    if (isConfigOpen && profile != null) {
        EmergencyContactsDialog(
            layoutPadding = layoutPadding,
            candidates = configCandidates,
            selectedIds = configContactIds,
            message = configMessage,
            isSaving = isSavingSosConfig,
            onMessageChange = {
                configMessage = it
                configMessageIsDefault = false
            },
            onToggleContact = { toggleSosConfigContact(it.id) },
            onDismiss = {
                if (!isSavingSosConfig) {
                    isConfigOpen = false
                }
            },
            onSave = { saveSosConfigAndMaybeSend(profile) }
        )
    }
}

@Composable
private fun GlobalSosButtonSurface(
    modifier: Modifier,
    isSendingSos: Boolean,
    pulseScale: Float = 1f,
    onClick: () -> Unit
) {
    val template = quataTheme()
    Surface(
        color = template.colors.sos,
        contentColor = Color.White,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .size(width = 70.dp, height = 34.dp)
            .graphicsLayer {
                val scale = if (isSendingSos) pulseScale else 1f
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.clickableNoRipple(onClick)
        ) {
            Text(stringResource(R.string.sos_button), fontWeight = FontWeight.ExtraBold, fontSize = template.textSizes.caption)
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.padding(PaddingValues()).let { Modifier.clickable(onClick = onClick) })

private data class QuataAppContentPadding(
    val start: Dp = 0.dp,
    val top: Dp = 0.dp,
    val end: Dp = 0.dp,
    val bottom: Dp = 0.dp
) {
    fun toPaddingValues(): PaddingValues =
        PaddingValues(start = start, top = top, end = end, bottom = bottom)
}

private fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun Location.isOlderThanSosFreshness(nowMillis: Long = System.currentTimeMillis()): Boolean =
    sosAgeMillis(nowMillis) > 60_000L

private fun Location.sosAgeMillis(nowMillis: Long = System.currentTimeMillis()): Long =
    (nowMillis - time).coerceAtLeast(0L)

private fun Context.playDefaultNotificationSound() {
    runCatching {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(applicationContext, uri)?.play()
    }
}

private fun Long.formatSosRemaining(): String {
    val totalSeconds = (coerceAtLeast(0L) + 999L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private const val SosLocationLogTag = "QuataSosLocation"
