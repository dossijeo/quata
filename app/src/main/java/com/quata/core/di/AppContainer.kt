package com.quata.core.di

import android.content.Context
import com.quata.core.auth.GoogleAuthHelper
import com.quata.core.camera.CameraCaptureManager
import com.quata.core.camera.ImageCompressor
import com.quata.core.camera.ImagePickerManager
import com.quata.core.common.AppDispatchers
import com.quata.core.network.NetworkModule
import com.quata.core.notifications.NotificationChannels
import com.quata.core.notifications.PushTokenManager
import com.quata.core.preferences.SessionPreferences
import com.quata.core.session.SessionManager
import com.quata.feature.auth.data.AuthRepositoryImpl
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.chat.data.ChatRemoteDataSource
import com.quata.feature.chat.data.ChatRepositoryImpl
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.feed.data.FeedRemoteDataSource
import com.quata.feature.feed.data.FeedRepositoryImpl
import com.quata.feature.feed.domain.FeedRepository
import com.quata.feature.notifications.data.NotificationsRepositoryImpl
import com.quata.feature.notifications.domain.NotificationsRepository
import com.quata.feature.neighborhoods.data.NeighborhoodRepositoryImpl
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.postcomposer.data.PostComposerRepositoryImpl
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.profile.data.ProfileRepositoryImpl
import com.quata.feature.profile.data.ProfileRemoteDataSource
import com.quata.feature.profile.domain.ProfileRepository

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val dispatchers = AppDispatchers()
    val networkModule = NetworkModule(appContext)
    val supabaseCommunityApi = networkModule.supabaseCommunityApi
    val supabaseRealtimeClient = networkModule.supabaseRealtimeClient
    val betterMessagesClient = networkModule.betterMessagesClient
    val betterMessagesRepository = networkModule.betterMessagesRepository
    val quataWordPressClient = networkModule.quataWordPressClient

    val sessionPreferences = SessionPreferences(appContext)
    val sessionManager = SessionManager(sessionPreferences)

    val imagePickerManager = ImagePickerManager()
    val cameraCaptureManager = CameraCaptureManager()
    val imageCompressor = ImageCompressor()
    val notificationChannels = NotificationChannels(appContext).also { it.ensureChannels() }
    val pushTokenManager = PushTokenManager(networkModule.supabaseApi)

    val authRepository: AuthRepository = AuthRepositoryImpl(
        appContext = appContext,
        supabaseApi = networkModule.supabaseCommunityApi,
        wordpressClient = networkModule.quataWordPressClient,
        betterMessagesRepository = networkModule.betterMessagesRepository,
        sessionManager = sessionManager,
        googleAuthHelper = GoogleAuthHelper()
    )

    val feedRepository: FeedRepository = FeedRepositoryImpl(
        appContext = appContext,
        remote = FeedRemoteDataSource(networkModule.supabaseCommunityApi),
        profileRemote = ProfileRemoteDataSource(networkModule.supabaseCommunityApi),
        sessionManager = sessionManager
    )

    val postComposerRepository: PostComposerRepository = PostComposerRepositoryImpl(
        appContext = appContext,
        supabaseApi = networkModule.supabaseCommunityApi,
        wordpressClient = networkModule.quataWordPressClient,
        sessionManager = sessionManager
    )

    val chatRepository: ChatRepository = ChatRepositoryImpl(
        appContext = appContext,
        remote = ChatRemoteDataSource(networkModule.supabaseCommunityApi),
        betterMessagesRepository = networkModule.betterMessagesRepository,
        sessionManager = sessionManager
    )

    val notificationsRepository: NotificationsRepository = NotificationsRepositoryImpl(
        appContext = appContext,
        chatRepository = chatRepository,
        supabaseApi = networkModule.supabaseCommunityApi,
        sessionManager = sessionManager
    )

    val profileRepository: ProfileRepository = ProfileRepositoryImpl(
        remote = ProfileRemoteDataSource(networkModule.supabaseCommunityApi),
        sessionManager = sessionManager,
        context = appContext
    )

    val neighborhoodRepository: NeighborhoodRepository = NeighborhoodRepositoryImpl(
        appContext = appContext,
        supabaseApi = networkModule.supabaseCommunityApi,
        betterMessagesRepository = networkModule.betterMessagesRepository,
        profileRemote = ProfileRemoteDataSource(networkModule.supabaseCommunityApi),
        sessionManager = sessionManager
    )
}
