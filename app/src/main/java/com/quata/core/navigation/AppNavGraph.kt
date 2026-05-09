package com.quata.core.navigation

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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
import com.quata.feature.profile.presentation.ProfileScreen

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
                    onLogout = {
                        navController.navigate(AppDestinations.Login.route) {
                            popUpTo(0)
                        }
                    }
                )
            }
        }
    }
}
