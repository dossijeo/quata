package com.quata.web

import com.quata.core.platform.PreferenceStore
import com.quata.feature.postcomposer.data.ComposerPostInsert
import com.quata.feature.postcomposer.data.composerModerationFields
import com.quata.feature.postcomposer.data.toRemoteText
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WebPostComposerTransportContractTest {
    private val configuration = WebRuntimeConfiguration(
        supabaseUrl = "https://project.supabase.co",
        supabasePublishableKey = "publishable-key",
        wordpressBaseUrl = "https://egquata.com/",
    )

    @Test
    fun wallFallbackUsesCanonicalPluralViewAndStableQuery() {
        assertEquals("community_walls_stats", WEB_COMPOSER_WALL_STATS_TABLE)
        assertEquals(
            mapOf("select" to "id", "is_active" to "eq.true", "order" to "sort_order.asc", "limit" to "1"),
            webComposerWallFallbackQuery(),
        )
    }

    @Test
    fun persistedRealWebSessionCarriesDisplayNameIntoActorModerationAndInsert() = runTest {
        val preferences = MemoryPreferences(mapOf(
            WebAuthStorage.AccessToken to "access-token",
            WebAuthStorage.RefreshToken to "refresh-token",
            WebAuthStorage.WebSessionToken to "web-session-token",
            WebAuthStorage.UserId to "profile-7",
            WebAuthStorage.ExpiresAt to "4102444800",
            WebAuthStorage.DisplayName to "Ada Lovelace",
        ))
        val auth = WebAuthRepository(configuration, preferences)
        val actor = WebPostComposerTransport(configuration, auth).renewableSession()
        val draft = PostComposerDraft(PostComposerType.Text, text = "Hola", textPatternId = "paper")

        assertEquals("Ada Lovelace", actor?.displayName)
        assertEquals("Ada Lovelace", composerModerationFields(requireNotNull(actor), draft, "", "", "web://post")["display_name"])
        assertEquals(
            "{\"wall_id\":\"wall-1\",\"profile_id\":\"profile-7\",\"body\":\"[CANAL:feed]\\n[PATRON_TEXTO:paper]\\nHola\"}",
            webComposerPostBody(ComposerPostInsert(actor.profileId, "wall-1", draft.toRemoteText())),
        )
    }

    @Test
    fun legacySessionWithoutDisplayNameStillRestoresAndUsesRemoteProfileContract() = runTest {
        val preferences = MemoryPreferences(mapOf(
            WebAuthStorage.AccessToken to "access-token",
            WebAuthStorage.RefreshToken to "refresh-token",
            WebAuthStorage.WebSessionToken to "web-session-token",
            WebAuthStorage.UserId to "profile-7",
            WebAuthStorage.ExpiresAt to "4102444800",
        ))

        assertEquals(null, WebAuthRepository(configuration, preferences).restoreLocalSession()?.displayName)
        assertEquals("community_profiles", WEB_COMPOSER_PROFILES_TABLE)
        assertEquals(mapOf("select" to "display_name", "id" to "eq.profile-7", "limit" to "1"), webComposerProfileDisplayNameQuery("profile-7"))
    }

    @Test
    fun storageUploadAndRollbackContractsRetainSupabaseAuthAndPaths() {
        val objectUrl = webComposerStorageObjectUrl("https://project.supabase.co/", "profile 7/img-1.png")
        assertEquals("https://project.supabase.co/storage/v1/object/community-posts/profile%207/img-1.png", objectUrl)
        assertEquals(
            WebComposerHttpContract("POST", objectUrl, mapOf(
                "apikey" to "publishable-key", "Authorization" to "Bearer access-token",
                "Content-Type" to "image/png", "x-upsert" to "true",
            )),
            webComposerStorageUploadContract(objectUrl, "publishable-key", "access-token", "image/png"),
        )
        assertEquals(
            WebComposerHttpContract("DELETE", objectUrl, mapOf("apikey" to "publishable-key", "Authorization" to "Bearer access-token")),
            webComposerStorageDeleteContract(objectUrl, "publishable-key", "access-token"),
        )
        assertEquals("community_posts", WEB_COMPOSER_POSTS_TABLE)
        assertEquals(
            "{\"wall_id\":\"wall-1\",\"profile_id\":\"profile-7\",\"body\":\"hello\",\"image_url\":\"https://cdn/image.png\"}",
            webComposerPostBody(ComposerPostInsert("profile-7", "wall-1", "hello", imageUrl = "https://cdn/image.png")),
        )
        assertEquals(
            "https://project.supabase.co/storage/v1/object/public/community-posts/profile%207/img-1.png",
            webComposerStoragePublicUrl("https://project.supabase.co", "profile 7/img-1.png"),
        )
    }

    @Test
    fun wordpressEndpointsUseSameOriginProxyOnlyInDevelopment() {
        assertEquals("/wordpress-proxy/wp-admin/admin-ajax.php", webComposerWordpressUrl(configuration, "wp-admin/admin-ajax.php", true))
        assertEquals("https://egquata.com/wp-admin/admin-ajax.php", webComposerWordpressUrl(configuration, "wp-admin/admin-ajax.php", false))
        assertEquals("/wordpress-proxy/wp-json/quqos/v1/upload-video", webComposerWordpressUrl(configuration, "wp-json/quqos/v1/upload-video", true))
        assertEquals("https://egquata.com/wp-json/quqos/v1/upload-video", webComposerWordpressUrl(configuration, "wp-json/quqos/v1/upload-video", false))
        assertEquals(
            mapOf("action" to "quqos_delete_post_video", "url" to "https://egquata.com/video.mp4"),
            webComposerVideoDeleteFields("https://egquata.com/video.mp4"),
        )
    }

    @Test
    fun blobPickerReferencesKeepUploadSafeFallbackNamesAndMimeTypes() {
        assertEquals("video.mp4", webPrepared("blob:http://127.0.0.1:9000/abc-123", "video/mp4", "video.mp4").name)
        assertEquals("video/mp4", webPrepared("blob:http://127.0.0.1:9000/abc-123", "video/mp4", "video.mp4").mimeType)
        assertEquals("imagen.jpg", webPrepared("blob:http://127.0.0.1:9000/def-456", "image/jpeg", "imagen.jpg").name)
        assertEquals("clip.mov", webPrepared("https://example.invalid/uploads/clip.mov", "video/mp4", "video.mp4").name)
        assertEquals("video/quicktime", webPrepared("https://example.invalid/uploads/clip.mov", "video/mp4", "video.mp4").mimeType)
    }

    private class MemoryPreferences(initial: Map<String, String>) : PreferenceStore {
        private val values = initial.toMutableMap()
        override suspend fun getString(key: String): String? = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
}
