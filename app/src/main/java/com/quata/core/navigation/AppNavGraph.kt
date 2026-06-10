package com.quata.core.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.location.Location
import android.location.LocationManager
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.quata.core.di.AppContainer
import com.quata.core.session.AuthState
import com.quata.core.ui.components.LocalQuataNetworkImageState
import com.quata.core.ui.components.QuataBottomBar
import com.quata.core.ui.components.QuataNetworkImageState
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.effects.fluidTouchEffect
import com.quata.feature.chat.domain.ChatPollingMode
import com.quata.feature.auth.presentation.login.LoginScreen
import com.quata.feature.auth.presentation.recovery.ForgotPasswordScreen
import com.quata.feature.auth.presentation.register.RegisterScreen
import com.quata.feature.chat.domain.SosRateLimitException
import com.quata.feature.chat.presentation.chat.ChatScreen
import com.quata.feature.chat.presentation.conversations.ConversationsScreen
import com.quata.feature.feed.presentation.FeedScreen
import com.quata.feature.neighborhoods.presentation.CommunityProfileScreen
import com.quata.feature.neighborhoods.presentation.NeighborhoodsScreen
import com.quata.feature.neighborhoods.presentation.NeighborhoodsViewModel
import com.quata.feature.notifications.presentation.NotificationsScreen
import com.quata.feature.postcomposer.presentation.CreatePostScreen
import com.quata.feature.profile.domain.UserProfile
import com.quata.feature.profile.presentation.EmergencyContactsDialog
import com.quata.feature.profile.presentation.ProfileUiEvent
import com.quata.feature.profile.presentation.ProfileViewModel
import com.quata.feature.profile.presentation.ProfileScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.quata.BuildConfig
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.designsystem.theme.quataTheme

@Composable
fun AppNavGraph(
    container: AppContainer,
    themeMode: QuataThemeMode,
    incomingLink: Uri? = null,
    onIncomingLinkHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = currentBackStackEntry?.destination?.route
    val currentConversationId = currentBackStackEntry?.arguments?.getString("conversationId")
    val authState by container.sessionManager.authState.collectAsState()
    val currentUserId = (authState as? AuthState.LoggedIn)?.userId
    val isAuthenticated = currentUserId != null
    val touchFlowEnabled by remember(currentUserId, container.touchFlowPreferences) {
        container.touchFlowPreferences.observeEnabled(currentUserId)
    }.collectAsState(initial = container.touchFlowPreferences.isEnabled(currentUserId))
    val startDestination = AppDestinations.Feed.route
    var isVideoEditorOpen by rememberSaveable { mutableStateOf(false) }
    var isCreatePostUploadInProgress by rememberSaveable { mutableStateOf(false) }
    var pendingCreatePostUploadRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val routeShowsAppChrome = currentRoute != null &&
        currentRoute != AppDestinations.Login.route &&
        currentRoute != AppDestinations.Register.route &&
        currentRoute != AppDestinations.ForgotPassword.route
    val showAppChrome = routeShowsAppChrome && !isVideoEditorOpen
    val observedNotificationCount by container.notificationsRepository.observeNotificationCount().collectAsState<Int, Int?>(initial = null)
    val notificationCount = observedNotificationCount ?: 0
    val isDeviceNetworkAvailable = rememberDeviceNetworkAvailable()
    val isAppOnline = isDeviceNetworkAvailable
    var feedNetworkReconnectToken by rememberSaveable { mutableLongStateOf(0L) }
    var previousDeviceNetworkAvailable by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val appContext = LocalContext.current
    var hasObservedNotificationCount by rememberSaveable { mutableStateOf(false) }
    var previousNotificationCount by rememberSaveable { mutableStateOf(0) }
    var isNotificationBounceActive by rememberSaveable { mutableStateOf(false) }
    var isAboutDialogOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isDeviceNetworkAvailable) {
        container.chatRepository.setDeviceNetworkAvailable(isDeviceNetworkAvailable)
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
        AppDestinations.CreatePost.route,
        AppDestinations.Conversations.route,
        AppDestinations.Profile.route
    )
    var createPostResetToken by rememberSaveable { mutableStateOf(0) }
    var createPostCancelUploadToken by rememberSaveable { mutableStateOf(0) }
    var feedFocusedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var chatFocusedMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    var isAuthRequiredPromptOpen by rememberSaveable { mutableStateOf(false) }
    fun requestAuthentication() {
        isAuthRequiredPromptOpen = true
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
    val globalProfileViewModel: NeighborhoodsViewModel = viewModel(
        key = "global_user_profile",
        factory = NeighborhoodsViewModel.factory(container.neighborhoodRepository)
    )
    val globalProfileState by globalProfileViewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppForeground by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner, container.chatRepository, showAppChrome, currentRoute) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    isAppForeground = true
                    container.chatRepository.setPollingMode(chatPollingModeFor(showAppChrome, currentRoute, isForeground = true))
                }
                Lifecycle.Event.ON_STOP -> {
                    isAppForeground = false
                    container.chatRepository.setPollingMode(chatPollingModeFor(showAppChrome, currentRoute, isForeground = false))
                }
                Lifecycle.Event.ON_DESTROY -> container.chatRepository.setPollingMode(ChatPollingMode.MINIMAL)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(showAppChrome, currentRoute, isAppForeground) {
        container.chatRepository.setPollingMode(chatPollingModeFor(showAppChrome, currentRoute, isAppForeground))
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != AppDestinations.CreatePost.route) {
            isCreatePostUploadInProgress = false
            pendingCreatePostUploadRoute = null
        }
    }

    LaunchedEffect(incomingLink) {
        val conversationId = incomingLink?.quataConversationIdOrNull()
        if (conversationId != null) {
            globalProfileViewModel.closeUserProfile()
            feedFocusedPostId = null
            chatFocusedMessageId = null
            navigateToChat(conversationId)
            onIncomingLinkHandled()
            return@LaunchedEffect
        }

        val postId = incomingLink?.quataPostIdOrNull() ?: return@LaunchedEffect
        feedFocusedPostId = postId
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
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .fluidTouchEffect(enabled = touchFlowEnabled)
        ) {
        Scaffold(
            topBar = {
                if (showAppChrome) {
                    Column {
                        QuataAppTopSpacer()
                        if (!isAppOnline) {
                            AppOfflineBanner()
                        }
                    }
                }
            },
            bottomBar = {
                if (currentRoute in bottomRoutes && !isVideoEditorOpen) {
                    QuataBottomBar(currentRoute = currentRoute, onDestinationClick = ::handleBottomRoute)
                }
            }
        ) { padding ->
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
                        networkReconnectToken = feedNetworkReconnectToken,
                        isNetworkAvailable = isDeviceNetworkAvailable,
                        onFocusedPostHandled = { feedFocusedPostId = null },
                        onAuthRequired = { requestAuthentication() }
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
                            onBack = { navController.popBackStack() }
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
                            sessionManager = container.sessionManager,
                            repository = container.profileRepository,
                            touchFlowEnabled = touchFlowEnabled,
                            onTouchFlowEnabledChange = { enabled ->
                                container.touchFlowPreferences.setEnabled(currentUserId, enabled)
                            },
                            themeMode = themeMode,
                            onThemeModeChange = container.themePreferences::setThemeMode,
                            networkReconnectToken = feedNetworkReconnectToken,
                            onLogout = {
                                navController.navigate(AppDestinations.Feed.route) {
                                    popUpTo(0)
                                    launchSingleTop = true
                                }
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
        }

        if (showAppChrome) {
            val topChromePlacement = rememberTopChromePlacement()
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
                    .padding(top = 14.dp, start = topChromePlacement.logoStartPadding)
            )
            GlobalSosButton(
                container = container,
                isAuthenticated = isAuthenticated,
                onAuthRequired = { requestAuthentication() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = topChromePlacement.sosEndPadding)
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
                chatError = globalProfileState.error,
                onAuthRequired = { requestAuthentication() },
                onReportPost = { postId ->
                    if (isAuthenticated) globalProfileViewModel.reportProfilePost(postId) else requestAuthentication()
                },
                onBack = { globalProfileViewModel.closeUserProfile() },
                onFollow = {
                    if (isAuthenticated) globalProfileViewModel.toggleFollowUser(profile.user.id) else requestAuthentication()
                },
                onFollowUser = { userId ->
                    if (isAuthenticated) globalProfileViewModel.toggleFollowUser(userId) else requestAuthentication()
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
                onDismiss = { isAboutDialogOpen = false }
            )
        }
    }
    }
}

@Composable
private fun AboutQuataDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.about_title),
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column {
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
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

private fun chatPollingModeFor(
    showAppChrome: Boolean,
    currentRoute: String?,
    isForeground: Boolean
): ChatPollingMode = when {
    !showAppChrome -> ChatPollingMode.MINIMAL
    !isForeground -> ChatPollingMode.RELAXED
    currentRoute == AppDestinations.Chat.route -> ChatPollingMode.AGGRESSIVE
    currentRoute == AppDestinations.Feed.route -> ChatPollingMode.RELAXED
    else -> ChatPollingMode.MEDIUM
}

@Composable
private fun rememberDeviceNetworkAvailable(): Boolean {
    val context = LocalContext.current.applicationContext
    var isAvailable by remember(context) { mutableStateOf(context.hasUsableNetwork()) }
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        if (connectivityManager == null) return@DisposableEffect onDispose {}
        val mainHandler = Handler(Looper.getMainLooper())
        fun refresh() {
            mainHandler.post {
                isAvailable = context.hasUsableNetwork()
            }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refresh()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                refresh()
            }

            override fun onLost(network: Network) {
                refresh()
            }

            override fun onUnavailable() {
                refresh()
            }
        }
        refresh()
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }
    return isAvailable
}

private fun Context.hasUsableNetwork(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return true
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
private fun QuataAppTopSpacer() {
    val template = quataTheme()
    Surface(
        color = template.colors.topChrome,
        contentColor = template.colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
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
private fun rememberTopChromePlacement(): TopChromePlacement {
    val view = LocalView.current
    val density = LocalDensity.current
    val logoWidthPx = with(density) { 92.dp.roundToPx() }
    val sosWidthPx = with(density) { 70.dp.roundToPx() }
    val logoHeightPx = with(density) { 40.dp.roundToPx() }
    val sosHeightPx = with(density) { 34.dp.roundToPx() }
    val logoTopPx = with(density) { 4.dp.roundToPx() }
    val sosTopPx = with(density) { 14.dp.roundToPx() }
    val marginPx = with(density) { 16.dp.roundToPx() }
    val screenWidthPx = view.rootView.width.takeIf { it > 0 } ?: view.resources.displayMetrics.widthPixels
    return remember(view, screenWidthPx, logoWidthPx, sosWidthPx, logoHeightPx, sosHeightPx, logoTopPx, sosTopPx, marginPx, density.density) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@remember TopChromePlacement()
        val windowInsets = view.rootWindowInsets ?: return@remember TopChromePlacement()
        val displayCutout = windowInsets.displayCutout ?: return@remember TopChromePlacement()
        val cutouts = displayCutout.boundingRects
        if (cutouts.isEmpty()) return@remember TopChromePlacement()
        var logoStartPx = marginPx
        var sosEndPx = marginPx

        cutouts.forEach { cutout ->
            val logoRect = Rect(logoStartPx, logoTopPx, logoStartPx + logoWidthPx, logoTopPx + logoHeightPx)
            if (logoRect.intersects(cutout)) {
                logoStartPx = (cutout.right + marginPx).coerceAtMost(screenWidthPx - logoWidthPx - marginPx)
            }

            val sosLeft = screenWidthPx - sosEndPx - sosWidthPx
            val sosRect = Rect(sosLeft, sosTopPx, sosLeft + sosWidthPx, sosTopPx + sosHeightPx)
            if (sosRect.intersects(cutout)) {
                val shiftedEnd = screenWidthPx - cutout.left + marginPx
                sosEndPx = shiftedEnd.coerceAtMost(screenWidthPx - sosWidthPx - marginPx)
            }
        }

        TopChromePlacement(
            logoStartPadding = with(density) { logoStartPx.toDp() },
            sosEndPadding = with(density) { sosEndPx.toDp() }
        )
    }
}

private data class TopChromePlacement(
    val logoStartPadding: Dp = 16.dp,
    val sosEndPadding: Dp = 16.dp
)

private fun Rect.intersects(other: Rect): Boolean =
    left < other.right && right > other.left && top < other.bottom && bottom > other.top

@Composable
private fun GlobalSosButton(
    container: AppContainer,
    isAuthenticated: Boolean,
    onAuthRequired: () -> Unit,
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

    AuthenticatedGlobalSosButton(container = container, modifier = modifier)
}

@Composable
private fun AuthenticatedGlobalSosButton(
    container: AppContainer,
    modifier: Modifier = Modifier,
    profileViewModel: ProfileViewModel = viewModel(
        key = "global_sos_profile",
        factory = ProfileViewModel.Factory(container.profileRepository)
    )
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by profileViewModel.uiState.collectAsState()
    var isConfigOpen by rememberSaveable { mutableStateOf(false) }
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

    fun buildSosMessage(profile: UserProfile, location: Location?): String {
        val locationText = if (location == null) {
            context.getString(R.string.sos_location_unavailable)
        } else {
            context.getString(R.string.sos_location, "https://maps.google.com/?q=${location.latitude},${location.longitude}")
        }
        return "${profile.emergencyMessage}\n$locationText"
    }

    fun sendSos(profile: UserProfile, location: Location?) {
        if (isSendingSos) return
        isSendingSos = true
        scope.launch {
            container.chatRepository.sendSosMessage(
                contactIds = profile.emergencyContactIds,
                text = buildSosMessage(profile, location)
            ).onSuccess {
                Toast.makeText(context, context.getString(R.string.sos_sent), Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                val message = if (error is SosRateLimitException) {
                    context.getString(R.string.sos_recently_sent, error.remainingMillis.formatSosRemaining())
                } else {
                    error.message ?: context.getString(R.string.sos_send_error)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            isSendingSos = false
        }
    }

    lateinit var locationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    fun requestLocation(profile: UserProfile) {
        pendingProfile = profile
        if (context.hasLocationPermission()) {
            sendSos(profile, context.lastKnownLocation())
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
            sendSos(profile, if (granted) context.lastKnownLocation() else null)
            pendingProfile = null
        }
    }

    fun startSos(profile: UserProfile) {
        if (profile.emergencyContactIds.isEmpty()) {
            isConfigOpen = true
            return
        }
        pendingProfile = profile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestLocation(profile)
        }
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

    val profile = state.profile
    if (isConfigOpen && profile != null) {
        EmergencyContactsDialog(
            candidates = state.emergencyCandidates,
            selectedIds = profile.emergencyContactIds,
            message = profile.emergencyMessage,
            onMessageChange = { profileViewModel.onEvent(ProfileUiEvent.EmergencyMessageChanged(it)) },
            onToggleContact = { profileViewModel.onEvent(ProfileUiEvent.EmergencyContactToggled(it.id)) },
            onDismiss = { isConfigOpen = false },
            onSave = {
                profileViewModel.onEvent(ProfileUiEvent.Save)
                isConfigOpen = false
                if (profile.emergencyContactIds.isNotEmpty()) {
                    requestLocation(profile)
                } else {
                    Toast.makeText(context, context.getString(R.string.profile_emergency_contacts_updated), Toast.LENGTH_SHORT).show()
                }
            }
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

private fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Suppress("MissingPermission")
private fun Context.lastKnownLocation(): Location? {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.getProviders(true)
        .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
}

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
