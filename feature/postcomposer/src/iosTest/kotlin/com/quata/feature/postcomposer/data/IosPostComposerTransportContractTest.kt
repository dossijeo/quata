package com.quata.feature.postcomposer.data

import kotlin.test.Test
import kotlin.test.assertEquals

class IosPostComposerTransportContractTest {
    private val configuration = IosPostComposerRuntimeConfiguration(
        supabaseUrl = "https://project.supabase.co/",
        supabasePublishableKey = "publishable-key",
        wordpressBaseUrl = "https://egquata.com/",
    )

    @Test
    fun wallFallbackAndPostInsertUseCanonicalResources() {
        assertEquals("community_walls_stats?select=id&is_active=eq.true&order=sort_order.asc&limit=1", iosComposerWallFallbackPath())
        assertEquals("https://project.supabase.co/rest/v1/community_posts", configuration.iosComposerPostsUrl())
    }

    @Test
    fun authenticatedInsertContractHasBearerApiKeyJsonAndRepresentation() {
        assertEquals(
            IosComposerHttpContract(
                "https://project.supabase.co/rest/v1/community_posts", "POST",
                mapOf(
                    "Accept" to "application/json", "Content-Type" to "application/json",
                    "apikey" to "publishable-key", "Authorization" to "Bearer access-token",
                    "Prefer" to "return=representation",
                ),
            ),
            iosComposerHttpContract(
                configuration.iosComposerPostsUrl(), "POST", "application/json",
                "publishable-key", "access-token", upsert = false, prefer = "return=representation",
            ),
        )
    }

    @Test
    fun storageUploadAndRollbackUseCommunityPostsActorPath() {
        val objectUrl = configuration.iosComposerStorageObjectUrl("profile 7/img-1.png")
        assertEquals("https://project.supabase.co/storage/v1/object/community-posts/profile%207/img-1.png", objectUrl)
        assertEquals("https://project.supabase.co/storage/v1/object/public/community-posts/profile%207/img-1.png", configuration.iosComposerStoragePublicUrl("profile 7/img-1.png"))
        assertEquals(
            mapOf(
                "Accept" to "application/json", "Content-Type" to "image/png",
                "apikey" to "publishable-key", "Authorization" to "Bearer access-token", "x-upsert" to "true",
            ),
            iosComposerHttpContract(objectUrl, "POST", "image/png", "publishable-key", "access-token", true, null).headers,
        )
        assertEquals(
            mapOf("Accept" to "application/json", "apikey" to "publishable-key", "Authorization" to "Bearer access-token"),
            iosComposerHttpContract(objectUrl, "DELETE", null, "publishable-key", "access-token", false, null).headers,
        )
    }

    @Test
    fun wordpressVideoUploadAndDeleteUsePublicWordpressContracts() {
        assertEquals("https://egquata.com/wp-json/quqos/v1/upload-video", configuration.iosComposerWordpressVideoUploadUrl())
        assertEquals("https://egquata.com/wp-admin/admin-ajax.php", configuration.iosComposerWordpressAdminAjaxUrl())
        assertEquals(
            mapOf("Accept" to "application/json", "Content-Type" to "multipart/form-data; boundary=boundary-1"),
            iosComposerHttpContract(configuration.iosComposerWordpressVideoUploadUrl(), "POST", "multipart/form-data; boundary=boundary-1", null, null, false, null).headers,
        )
        assertEquals(
            mapOf("Accept" to "application/json", "Content-Type" to "application/x-www-form-urlencoded"),
            iosComposerHttpContract(configuration.iosComposerWordpressAdminAjaxUrl(), "POST", "application/x-www-form-urlencoded", null, null, false, null).headers,
        )
    }
}
