package com.quata.feature.chat.presentation.chat

import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.session.IosSupabaseAuthRuntimeConfiguration
import com.quata.core.session.IosSupabaseAuthSessionRefresher
import com.quata.feature.chat.data.IosChatAttachmentUploader
import com.quata.feature.chat.data.IosChatAuthenticatedUserProvider
import com.quata.feature.chat.data.IosChatPostgrestTransport
import com.quata.feature.chat.data.IosChatRuntimeConfiguration
import com.quata.feature.chat.data.PostgrestChatRepository
import com.quata.feature.chat.domain.ChatRepository

/**
 * Authenticated runtime composition for Chat. It shares the iOS Keychain owner with Auth/Feed
 * when injected by the launcher, and does not create a placeholder repository when no session
 * exists. A future UIKit router can request [repository] only after its current route requires it.
 */
class IosChatRuntimeBootstrap(
    private val configuration: IosChatRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession = IosRenewableAuthSession(
        IosSupabaseAuthSessionRefresher(
            IosSupabaseAuthRuntimeConfiguration(
                supabaseUrl = configuration.supabaseUrl,
                supabasePublishableKey = configuration.supabasePublishableKey,
            ),
        ),
    ),
) {
    private val chatRepository: ChatRepository by lazy {
        PostgrestChatRepository(
            transport = IosChatPostgrestTransport(configuration, authSession),
            authenticatedUser = IosChatAuthenticatedUserProvider(authSession),
            attachmentUploader = IosChatAttachmentUploader(configuration, authSession),
        )
    }

    /** One repository instance preserves the common polling/state flows across route transitions. */
    fun repository(): ChatRepository = chatRepository

    fun authSessionForInteractiveLogin(): IosRenewableAuthSession = authSession
}

/** Swift-facing factory avoiding Kotlin default-argument export ambiguity. */
fun createIosChatRuntimeBootstrap(
    configuration: IosChatRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
): IosChatRuntimeBootstrap = IosChatRuntimeBootstrap(configuration, authSession)
