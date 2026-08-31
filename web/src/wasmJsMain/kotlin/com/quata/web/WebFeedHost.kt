package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.quata.core.language.BrowserTranslationHttpTransport
import com.quata.core.language.FangTranslationService
import com.quata.core.platform.ShareService
import com.quata.core.ui.components.communityEmojiCatalogState
import com.quata.core.ui.components.communityEmojiSelectorEvidenceCatalogState
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.designsystem.translation.FangTextTranslatorGateway
import com.quata.designsystem.translation.quataTranslatorPreferredLanguage
import com.quata.designsystem.translation.quataTranslatorStringsForLanguage
import com.quata.feature.feed.presentation.FeedScreenHost
import com.quata.feature.feed.presentation.FeedScreenPlatformSlots
import com.quata.feature.feed.presentation.FeedUserPresence

/** Browser route adapter. Product rendering is the common [FeedScreenHost], including details. */
@Composable
fun WebFeedHost(
    repository: WebFeedRepository,
    shareService: ShareService,
    presence: FeedUserPresence? = null,
    sharedPostId: String? = null,
    currentUserId: String? = null,
    openingProfileUserId: String? = null,
    onAuthRequired: () -> Unit = {},
    onCreatePost: () -> Unit = {},
    onOpenUserProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(sharedPostId) { setWebFeedDetailMarker(sharedPostId) }
    val windowLayout = rememberQuataWindowLayoutInfo()
    val languageTag = browserCapabilityLanguageTag()
    val commentsTranslationGateway = remember {
        FangTextTranslatorGateway(
            identifier = BrowserFastTextLanguageIdentifier,
            translator = FangTranslationService(transport = BrowserTranslationHttpTransport()),
            preferredLanguage = quataTranslatorPreferredLanguage(languageTag),
        )
    }
    FeedScreenHost(
        padding = PaddingValues(),
        repository = repository,
        focusedPostId = sharedPostId,
        isLandscape = windowLayout.isLandscape,
        slots = FeedScreenPlatformSlots(
            media = { post, isCurrent, initialPositionMs, onPositionChanged, isFeedMuted, onFeedMuteChange ->
                BrowserFeedMediaContent(
                    post = post,
                    isCurrent = isCurrent,
                    isMuted = isFeedMuted,
                    initialPositionMs = initialPositionMs,
                    onPositionChanged = onPositionChanged,
                    onMuteChange = onFeedMuteChange,
                )
            },
            avatar = { post -> BrowserFeedAuthorAvatar(post, onOpenUserProfile, isLoading = openingProfileUserId == post.author.id) },
            rankingAvatar = { item -> BrowserFeedRankingAvatar(item) },
            avatarWithPresence = { post, isOnline -> BrowserFeedAuthorAvatar(post, onOpenUserProfile, isOnline, openingProfileUserId == post.author.id) },
            rankingAvatarWithPresence = { item, isOnline -> BrowserFeedRankingAvatar(item, isOnline) },
            share = shareService::share,
            showComposeMessage = true,
            commentsTranslationGateway = commentsTranslationGateway,
            commentsTranslatorStrings = quataTranslatorStringsForLanguage(languageTag),
            communityEmojiCatalog = { labels, onRetry ->
                communityEmojiSelectorEvidenceCatalogState(
                    labels = labels,
                    onRetry = onRetry,
                    optIn = webCommunityEmojiSelectorEvidenceValue("optIn"),
                    mode = webCommunityEmojiSelectorEvidenceValue("mode"),
                    message = webCommunityEmojiSelectorEvidenceValue("message"),
                ) ?: communityEmojiCatalogState(labels, onRetry = onRetry)
            },
        ),
        presence = presence,
        currentUserId = currentUserId,
        onAuthRequired = onAuthRequired,
        onCreatePost = onCreatePost,
        modifier = modifier,
    )
}

private fun setWebFeedDetailMarker(postId: String?) {
    js("globalThis.document?.documentElement?.setAttribute('data-quata-feed-detail', postId || '')")
}

private fun webCommunityEmojiSelectorEvidenceValue(key: String): String? = when (key) {
    "optIn" -> webCommunityEmojiSelectorEvidenceOptIn()
    "mode" -> webCommunityEmojiSelectorEvidenceMode()
    "message" -> webCommunityEmojiSelectorEvidenceMessage()
    else -> null
}

private fun webCommunityEmojiSelectorEvidenceOptIn(): String? =
    js("globalThis.localStorage?.getItem('quata.communityEmojiSelector.optIn') ?? null")

private fun webCommunityEmojiSelectorEvidenceMode(): String? =
    js("globalThis.localStorage?.getItem('quata.communityEmojiSelector.mode') ?? null")

private fun webCommunityEmojiSelectorEvidenceMessage(): String? =
    js("globalThis.localStorage?.getItem('quata.communityEmojiSelector.message') ?? null")
