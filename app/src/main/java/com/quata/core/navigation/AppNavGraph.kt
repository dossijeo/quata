package com.quata.core.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.location.Location
import android.location.LocationManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quata.core.di.AppContainer
import com.quata.core.ui.components.QuataBottomBar
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.effects.fluidTouchEffect
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

@Composable
fun AppNavGraph(container: AppContainer) {
    val navController = rememberNavController()
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = currentBackStackEntry?.destination?.route
    val currentConversationId = currentBackStackEntry?.arguments?.getString("conversationId")
    val startDestination = if (container.sessionManager.isLoggedIn()) AppDestinations.Feed.route else AppDestinations.Login.route
    val showAppChrome = currentRoute != null &&
        currentRoute != AppDestinations.Login.route &&
        currentRoute != AppDestinations.Register.route &&
        currentRoute != AppDestinations.ForgotPassword.route
    val observedNotificationCount by container.notificationsRepository.observeNotificationCount().collectAsState<Int, Int?>(initial = null)
    val notificationCount = observedNotificationCount ?: 0
    val appContext = LocalContext.current
    var hasObservedNotificationCount by rememberSaveable { mutableStateOf(false) }
    var previousNotificationCount by rememberSaveable { mutableStateOf(0) }
    var isNotificationBounceActive by rememberSaveable { mutableStateOf(false) }
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
    var feedFocusedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var chatFocusedMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    val globalProfileViewModel: NeighborhoodsViewModel = viewModel(
        key = "global_user_profile",
        factory = NeighborhoodsViewModel.factory(container.neighborhoodRepository)
    )
    val globalProfileState by globalProfileViewModel.uiState.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .fluidTouchEffect()
    ) {
        Scaffold(
            topBar = {
                if (showAppChrome) {
                    QuataAppTopSpacer()
                }
            },
            bottomBar = {
                if (currentRoute in bottomRoutes) {
                    QuataBottomBar(currentRoute = currentRoute) { route ->
                        if (route == AppDestinations.CreatePost.route && currentRoute == AppDestinations.CreatePost.route) {
                            createPostResetToken += 1
                        } else if (route == AppDestinations.Feed.route) {
                            val poppedToFeed = navController.popBackStack(AppDestinations.Feed.route, inclusive = false)
                            if (!poppedToFeed) {
                                navController.navigate(AppDestinations.Feed.route) {
                                    launchSingleTop = true
                                }
                            }
                        } else {
                            navController.navigate(route) {
                                popUpTo(AppDestinations.Feed.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
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
                                popUpTo(AppDestinations.Login.route) { inclusive = true }
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
                                popUpTo(AppDestinations.Login.route) { inclusive = true }
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
                        focusedPostId = feedFocusedPostId,
                        onFocusedPostHandled = { feedFocusedPostId = null }
                    )
                }

                composable(AppDestinations.Neighborhoods.route) {
                    NeighborhoodsScreen(
                        padding = padding,
                        repository = container.neighborhoodRepository,
                        currentUserId = container.sessionManager.currentSession()?.userId,
                        onOpenConversation = { id ->
                            chatFocusedMessageId = null
                            navController.navigate(AppDestinations.Chat.createRoute(id))
                        },
                        onOpenUserProfile = { userId ->
                            globalProfileViewModel.openUserProfile(userId)
                        }
                    )
                }

                composable(AppDestinations.CreatePost.route) {
                    CreatePostScreen(
                        padding = padding,
                        repository = container.postComposerRepository,
                        resetToken = createPostResetToken,
                        onPostCreated = { postId ->
                            feedFocusedPostId = postId
                            navController.navigate(AppDestinations.Feed.route) {
                                popUpTo(AppDestinations.Feed.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(AppDestinations.Conversations.route) {
                    ConversationsScreen(
                        padding = padding,
                        repository = container.chatRepository,
                        onOpenUserProfile = { userId ->
                            globalProfileViewModel.openUserProfile(userId)
                        },
                        onOpenFavorites = {
                            chatFocusedMessageId = null
                            navController.navigate(AppDestinations.Chat.createRoute(AppDestinations.FavoriteMessagesConversationId))
                        },
                        onOpenConversation = { id ->
                            chatFocusedMessageId = null
                            navController.navigate(AppDestinations.Chat.createRoute(id))
                        }
                    )
                }

                composable(
                    route = AppDestinations.Chat.route,
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                ) { entry ->
                    val conversationId = entry.arguments?.getString("conversationId")?.let(Uri::decode) ?: ""
                    ChatScreen(
                        padding = padding,
                        conversationId = conversationId,
                        repository = container.chatRepository,
                        onOpenUserProfile = { userId ->
                            globalProfileViewModel.openUserProfile(userId)
                        },
                        onOpenConversation = { id ->
                            chatFocusedMessageId = null
                            navController.navigate(AppDestinations.Chat.createRoute(id))
                        },
                        focusedMessageId = chatFocusedMessageId,
                        onFocusedMessageHandled = { chatFocusedMessageId = null },
                        onOpenMessageConversation = { targetConversationId, messageId ->
                            chatFocusedMessageId = messageId
                            navController.navigate(AppDestinations.Chat.createRoute(targetConversationId))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(AppDestinations.Notifications.route) {
                    NotificationsScreen(
                        padding = padding,
                        repository = container.notificationsRepository,
                        onBack = { navController.popBackStack() },
                        onOpenConversation = { id ->
                            chatFocusedMessageId = null
                            navController.navigate(AppDestinations.Chat.createRoute(id))
                        }
                    )
                }

                composable(AppDestinations.Profile.route) {
                    ProfileScreen(
                        padding = padding,
                        sessionManager = container.sessionManager,
                        repository = container.profileRepository,
                        onLogout = {
                            navController.navigate(AppDestinations.Login.route) {
                                popUpTo(0)
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

        if (showAppChrome) {
            val topChromePlacement = rememberTopChromePlacement()
            QuataAppHeaderActions(
                notificationCount = notificationCount,
                isBouncing = isNotificationBounceActive,
                onNotificationsClick = {
                    navController.navigate(AppDestinations.Notifications.route) {
                        popUpTo(AppDestinations.Feed.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 20.dp, start = topChromePlacement.logoStartPadding)
            )
            GlobalSosButton(
                container = container,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = topChromePlacement.sosEndPadding)
            )
        }

        globalProfileState.selectedProfile?.let { profile ->
            CommunityProfileScreen(
                padding = PaddingValues(0.dp),
                profile = profile,
                currentUserId = container.sessionManager.currentSession()?.userId,
                isOpeningChat = globalProfileState.isOpeningChat,
                chatError = globalProfileState.error,
                onReportPost = { postId -> globalProfileViewModel.reportProfilePost(postId) },
                onBack = { globalProfileViewModel.closeUserProfile() },
                onFollow = { globalProfileViewModel.toggleFollowUser(profile.user.id) },
                onFollowUser = { userId -> globalProfileViewModel.toggleFollowUser(userId) },
                onOpenPrivateChat = { userId ->
                    globalProfileViewModel.openPrivateChat(userId) { conversationId ->
                        globalProfileViewModel.closeUserProfile()
                        chatFocusedMessageId = null
                        if (currentRoute != AppDestinations.Chat.route || currentConversationId != conversationId) {
                            navController.navigate(AppDestinations.Chat.createRoute(conversationId))
                        }
                    }
                },
                onOpenUserProfile = { userId -> globalProfileViewModel.openUserProfile(userId) }
            )
        }
    }
}

@Composable
private fun QuataAppTopSpacer() {
    Surface(
        color = Color(0xFF0B1220),
        contentColor = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
    ) {}
}

@Composable
private fun QuataAppHeaderActions(
    notificationCount: Int,
    isBouncing: Boolean,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
    val scale = if (isBouncing) bounceScale else 1f
    Box(
        modifier = modifier.size(width = 160.dp, height = 48.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.quata_logo_header),
            contentDescription = stringResource(R.string.quata_logo_content_description),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-18).dp, y = (-2).dp)
                .width(120.dp)
        )
        IconButton(
            onClick = onNotificationsClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 82.dp, y = 4.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(48.dp)
        ) {
            BadgedBox(
                badge = {
                    if (notificationCount > 0) {
                        Badge(containerColor = Color(0xFFE0303B)) {
                            Text(notificationCount.coerceAtMost(99).toString(), color = Color.White)
                        }
                    }
                }
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.notifications_title), tint = Color.White)
            }
        }
    }
}

@Composable
private fun rememberTopChromePlacement(): TopChromePlacement {
    val view = LocalView.current
    val density = LocalDensity.current
    val logoWidthPx = with(density) { 144.dp.roundToPx() }
    val sosWidthPx = with(density) { 94.dp.roundToPx() }
    val logoHeightPx = with(density) { 84.dp.roundToPx() }
    val sosHeightPx = with(density) { 48.dp.roundToPx() }
    val logoTopPx = with(density) { 4.dp.roundToPx() }
    val sosTopPx = with(density) { 20.dp.roundToPx() }
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

    fun buildSosMessage(profile: UserProfile, location: Location?): String {
        val locationText = if (location == null) {
            context.getString(R.string.sos_location_unavailable)
        } else {
            context.getString(R.string.sos_location, "https://maps.google.com/?q=${location.latitude},${location.longitude}")
        }
        return "${profile.emergencyMessage}\n$locationText"
    }

    fun sendSos(profile: UserProfile, location: Location?) {
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

    Surface(
        color = Color(0xFFE0303B),
        contentColor = Color.White,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .size(width = 94.dp, height = 48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.clickableNoRipple {
                val profile = state.profile ?: return@clickableNoRipple
                startSos(profile)
            }
        ) {
            Text(stringResource(R.string.sos_button), fontWeight = FontWeight.ExtraBold)
        }
    }

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
