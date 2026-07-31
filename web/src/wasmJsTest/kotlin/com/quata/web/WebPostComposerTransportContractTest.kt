package com.quata.web

import com.quata.feature.postcomposer.data.ComposerPostInsert
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
}
