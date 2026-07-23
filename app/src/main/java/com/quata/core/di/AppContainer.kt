package com.quata.core.di

import android.content.Context
import com.quata.core.auth.GoogleAuthHelper
import com.quata.core.camera.CameraCaptureManager
import com.quata.core.camera.ImageCompressor
import com.quata.core.camera.ImagePickerManager
import com.quata.core.common.AppDispatchers
import com.quata.core.config.AppConfig
import com.quata.core.media.MediaUploadOptimizer
import com.quata.core.moderation.ModerationRepository
import com.quata.core.moderation.UgcTermsAcceptanceStore
import com.quata.core.network.NetworkModule
import com.quata.core.notifications.NotificationChannels
import com.quata.core.notifications.PushTokenManager
import com.quata.core.preferences.SessionPreferences
import com.quata.core.preferences.ThemePreferences
import com.quata.core.preferences.TouchFlowPreferences
import com.quata.core.presence.UserPresenceRepository
import com.quata.core.presence.UserPresenceRepositoryImpl
import com.quata.core.session.SessionManager
import com.quata.feature.auth.data.AuthRepositoryImpl
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.chat.data.ChatMessageStateAckManager
import com.quata.feature.chat.data.ChatRemoteDataSource
import com.quata.feature.chat.data.ChatRepositoryImpl
import com.quata.feature.chat.data.ChatTypingIndicatorManager
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.feed.data.FeedRemoteDataSource
import com.quata.feature.feed.data.FeedRepositoryImpl
import com.quata.feature.feed.domain.FeedRepository
import com.quata.feature.notifications.data.NotificationsRepositoryImpl
import com.quata.feature.notifications.domain.NotificationsRepository
import com.quata.feature.official.data.OfficialRepositoryImpl
import com.quata.feature.official.domain.OfficialRepository
import com.quata.feature.neighborhoods.data.NeighborhoodRepositoryImpl
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.postcomposer.data.PostComposerRepositoryImpl
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.profile.data.ProfileRepositoryImpl
import com.quata.feature.profile.data.ProfileRemoteDataSource
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.whatsnew.data.WhatsNewRepositoryImpl

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val dispatchers = AppDispatchers()
    val sessionPreferences = SessionPreferences(appContext)
    val sessionManager = SessionManager(sessionPreferences, useMockBackend = AppConfig.USE_MOCK_BACKEND)
    val networkModule = NetworkModule(appContext, sessionManager)
    val supabaseCommunityApi = networkModule.supabaseCommunityApi
    val supabaseRealtimeClient = networkModule.supabaseRealtimeClient
    val quataWordPressClient = networkModule.quataWordPressClient

    val touchFlowPreferences = TouchFlowPreferences(appContext)
    val themePreferences = ThemePreferences(appContext)

    val imagePickerManager = ImagePickerManager()
    val cameraCaptureManager = CameraCaptureManager()
    val imageCompressor = ImageCompressor()
    val mediaUploadOptimizer = MediaUploadOptimizer(appContext)
    val notificationChannels = NotificationChannels(appContext).also { it.ensureChannels() }
    val pushTokenManager = PushTokenManager(
        appContext = appContext,
        supabaseApi = networkModule.supabaseCommunityApi,
        sessionManager = sessionManager
    )
    val chatRemoteDataSource = ChatRemoteDataSource(networkModule.supabaseCommunityApi)
    val moderationRepository = ModerationRepository(
        api = networkModule.supabaseCommunityApi,
        sessionManager = sessionManager,
        termsAcceptanceStore = UgcTermsAcceptanceStore(appContext),
        appContext = appContext
    )
    val chatMessageStateAckManager = ChatMessageStateAckManager(
        appContext = appContext,
        remote = chatRemoteDataSource,
        sessionManager = sessionManager
    )
    val userPresenceRepository: UserPresenceRepository = UserPresenceRepositoryImpl(
        realtimeClient = networkModule.supabasePresenceRealtimeClient,
        sessionManager = sessionManager
    )
    private val chatTypingIndicatorManager = ChatTypingIndicatorManager(
        realtimeClient = networkModule.supabaseTypingRealtimeClient,
        sessionManager = sessionManager
    )

    val authRepository: AuthRepository = AuthRepositoryImpl(
        appContext = appContext,
        supabaseApi = networkModule.supabaseCommunityApi,
        sessionManager = sessionManager,
        googleAuthHelper = GoogleAuthHelper(),
        pushTokenManager = pushTokenManager
    )

    val feedRepository: FeedRepository = FeedRepositoryImpl(
        appContext = appContext,
        remote = FeedRemoteDataSource(networkModule.supabaseCommunityApi),
        profileRemote = ProfileRemoteDataSource(networkModule.supabaseCommunityApi),
        wordpressClient = networkModule.quataWordPressClient,
        sessionManager = sessionManager
    )

    val postComposerRepository: PostComposerRepository = PostComposerRepositoryImpl(
        appContext = appContext,
        supabaseApi = networkModule.supabaseCommunityApi,
        wordpressClient = networkModule.quataWordPressClient,
        sessionManager = sessionManager,
        mediaUploadOptimizer = mediaUploadOptimizer
    )

    val officialRepository: OfficialRepository = OfficialRepositoryImpl(
        appContext = appContext,
        supabaseApi = networkModule.supabaseCommunityApi,
        wordpressClient = networkModule.quataWordPressClient,
        sessionManager = sessionManager,
        mediaUploadOptimizer = mediaUploadOptimizer
    )

    val chatRepository: ChatRepository = ChatRepositoryImpl(
        appContext = appContext,
        remote = chatRemoteDataSource,
        supabaseRealtimeClient = networkModule.supabaseRealtimeClient,
        sessionManager = sessionManager,
        mediaUploadOptimizer = mediaUploadOptimizer,
        messageStateAckManager = chatMessageStateAckManager,
        typingIndicatorManager = chatTypingIndicatorManager
    )

    val notificationsRepository: NotificationsRepository = NotificationsRepositoryImpl(
        appContext = appContext,
        chatRepository = chatRepository,
        supabaseApi = networkModule.supabaseCommunityApi,
        sessionManager = sessionManager
    )

    val whatsNewRepository: WhatsNewRepositoryImpl = WhatsNewRepositoryImpl(
        appContext = appContext,
        api = networkModule.supabaseCommunityApi,
        sessionManager = sessionManager
    )

    val profileRepository: ProfileRepository = ProfileRepositoryImpl(
        remote = ProfileRemoteDataSource(networkModule.supabaseCommunityApi),
        sessionManager = sessionManager,
        context = appContext,
        mediaUploadOptimizer = mediaUploadOptimizer
    )

    val neighborhoodRepository: NeighborhoodRepository = NeighborhoodRepositoryImpl(
        appContext = appContext,
        supabaseApi = networkModule.supabaseCommunityApi,
        chatRepository = chatRepository,
        profileRemote = ProfileRemoteDataSource(networkModule.supabaseCommunityApi),
        sessionManager = sessionManager
    )
}
