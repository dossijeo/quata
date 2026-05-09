package com.quata.core.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quata.core.di.AppContainer
import com.quata.core.ui.components.QuataBottomBar
import com.quata.feature.auth.presentation.login.LoginScreen
import com.quata.feature.auth.presentation.register.RegisterScreen
import com.quata.feature.chat.presentation.chat.ChatScreen
import com.quata.feature.chat.presentation.conversations.ConversationsScreen
import com.quata.feature.feed.presentation.FeedScreen
import com.quata.feature.notifications.presentation.NotificationsScreen
import com.quata.feature.postcomposer.presentation.CreatePostScreen
import com.quata.feature.profile.domain.UserProfile
import com.quata.feature.profile.presentation.EmergencyContactsDialog
import com.quata.feature.profile.presentation.ProfileUiEvent
import com.quata.feature.profile.presentation.ProfileViewModel
import com.quata.feature.profile.presentation.ProfileScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(container: AppContainer) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val startDestination = if (container.sessionManager.isLoggedIn()) AppDestinations.Feed.route else AppDestinations.Login.route
    val bottomRoutes = setOf(
        AppDestinations.Feed.route,
        AppDestinations.CreatePost.route,
        AppDestinations.Conversations.route,
        AppDestinations.Notifications.route,
        AppDestinations.Profile.route
    )

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (currentRoute in bottomRoutes) {
                    QuataBottomBar(currentRoute = currentRoute) { route ->
                        if (route == AppDestinations.Feed.route) {
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

                composable(AppDestinations.Feed.route) {
                    FeedScreen(
                        padding = padding,
                        feedRepository = container.feedRepository
                    )
                }

                composable(AppDestinations.CreatePost.route) {
                    CreatePostScreen(
                        padding = padding,
                        repository = container.postComposerRepository,
                        onPostCreated = {
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
                        onOpenConversation = { id -> navController.navigate(AppDestinations.Chat.createRoute(id)) }
                    )
                }

                composable(
                    route = AppDestinations.Chat.route,
                    arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                ) { entry ->
                    val conversationId = entry.arguments?.getString("conversationId") ?: ""
                    ChatScreen(
                        padding = padding,
                        conversationId = conversationId,
                        repository = container.chatRepository,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(AppDestinations.Notifications.route) {
                    NotificationsScreen(
                        padding = padding,
                        repository = container.notificationsRepository
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
                        }
                    )
                }
            }
        }

        val showSosButton = currentRoute != null &&
            currentRoute != AppDestinations.Login.route &&
            currentRoute != AppDestinations.Register.route
        if (showSosButton) {
            val sosAlignment = rememberSosButtonAlignment()
            GlobalSosButton(
                container = container,
                modifier = Modifier
                    .align(sosAlignment)
                    .padding(top = 20.dp, start = 16.dp, end = 16.dp)
            )
        }
    }
}

@Composable
private fun rememberSosButtonAlignment(): Alignment {
    val view = LocalView.current
    val density = LocalDensity.current
    val buttonWidthPx = with(density) { 94.dp.roundToPx() }
    val marginPx = with(density) { 16.dp.roundToPx() }
    return remember(view, buttonWidthPx, marginPx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@remember Alignment.TopEnd
        val windowInsets = view.rootWindowInsets ?: return@remember Alignment.TopEnd
        val displayCutout = windowInsets.displayCutout ?: return@remember Alignment.TopEnd
        val cutout = displayCutout.boundingRects.minByOrNull { it.top } ?: return@remember Alignment.TopEnd
        val screenWidth = view.rootView.width.takeIf { it > 0 } ?: view.resources.displayMetrics.widthPixels
        val rightSpace = screenWidth - cutout.right
        val leftSpace = cutout.left
        when {
            rightSpace >= buttonWidthPx + marginPx -> Alignment.TopEnd
            leftSpace >= buttonWidthPx + marginPx -> Alignment.TopStart
            rightSpace >= leftSpace -> Alignment.TopEnd
            else -> Alignment.TopStart
        }
    }
}

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
            "📍 Ubicación no disponible"
        } else {
            "📍 Ubicación: https://maps.google.com/?q=${location.latitude},${location.longitude}"
        }
        return "${profile.emergencyMessage}\n$locationText"
    }

    fun sendSos(profile: UserProfile, location: Location?) {
        scope.launch {
            container.chatRepository.sendSosMessage(
                contactIds = profile.emergencyContactIds,
                text = buildSosMessage(profile, location)
            ).onSuccess {
                Toast.makeText(context, "SOS enviado", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, error.message ?: "No se pudo enviar el SOS", Toast.LENGTH_SHORT).show()
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
        if (profile.emergencyContactIds.size < 5) {
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
            Text("SOS 🚨", fontWeight = FontWeight.ExtraBold)
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
                Toast.makeText(context, "Contactos de emergencia actualizados", Toast.LENGTH_SHORT).show()
                isConfigOpen = false
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
