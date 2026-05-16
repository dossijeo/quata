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
import com.quata.feature.auth.data.AuthRemoteDataSource
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
import com.quata.feature.postcomposer.data.PostComposerRemoteDataSource
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
        remoteDataSource = AuthRemoteDataSource(networkModule.wordpressApi),
        sessionManager = sessionManager,
        googleAuthHelper = GoogleAuthHelper()
    )

    val feedRepository: FeedRepository = FeedRepositoryImpl(
        remote = FeedRemoteDataSource(networkModule.wordpressApi, networkModule.supabaseApi),
        profileRemote = ProfileRemoteDataSource(networkModule.supabaseApi)
    )

    val postComposerRepository: PostComposerRepository = PostComposerRepositoryImpl(
        remote = PostComposerRemoteDataSource(networkModule.wordpressApi, networkModule.supabaseApi),
        sessionManager = sessionManager
    )

    val chatRepository: ChatRepository = ChatRepositoryImpl(
        remote = ChatRemoteDataSource(networkModule.supabaseApi),
        sessionManager = sessionManager
    )

    val notificationsRepository: NotificationsRepository = NotificationsRepositoryImpl(chatRepository)

    val profileRepository: ProfileRepository = ProfileRepositoryImpl(
        remote = ProfileRemoteDataSource(networkModule.supabaseApi),
        sessionManager = sessionManager,
        context = appContext
    )

    val neighborhoodRepository: NeighborhoodRepository = NeighborhoodRepositoryImpl(
        profileRemote = ProfileRemoteDataSource(networkModule.supabaseApi),
        chatRemote = ChatRemoteDataSource(networkModule.supabaseApi),
        sessionManager = sessionManager
    )
}
