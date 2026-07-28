package com.quata.feature.feed.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class IosAnonymousFeedTransportContractTest {
    @Test
    fun publicReadsUseOnlyPublishableKeyAndJsonAcceptHeaders() {
        val headers = iosFeedPublicHeaders("public-key")

        assertEquals("public-key", headers["apikey"])
        assertEquals("application/json", headers["Accept"])
        assertFalse(headers.keys.any { it?.toString()?.equals("Authorization", ignoreCase = true) == true })
    }

    @Test
    fun everyPublicFeedEndpointUsesTheAnonymousGetRequestHelper() {
        val requests = listOf(
            iosPublicFeedRequest(
                baseUrl = "https://deployment.invalid/",
                publishableKey = "public-key",
                table = "community_posts",
                query = mapOf("select" to "id", "order" to "created_at.desc"),
            ),
            iosPublicFeedRequest(
                baseUrl = "https://deployment.invalid/",
                publishableKey = "public-key",
                table = "community_posts",
                query = mapOf("select" to "id", "id" to "eq.post-7"),
            ),
            iosPublicFeedRequest(
                baseUrl = "https://deployment.invalid/",
                publishableKey = "public-key",
                table = "community_comments",
                query = mapOf("select" to "id", "post_id" to "in.(post-7)"),
            ),
            iosPublicFeedRequest(
                baseUrl = "https://deployment.invalid/",
                publishableKey = "public-key",
                table = "community_post_likes",
                query = mapOf("select" to "post_id", "post_id" to "in.(post-7)"),
            ),
            iosPublicFeedRequest(
                baseUrl = "https://deployment.invalid/",
                publishableKey = "public-key",
                table = "community_profiles",
                query = mapOf("select" to "id", "id" to "in.(profile-7)"),
            ),
        )

        assertEquals(
            listOf(
                "community_posts",
                "community_posts",
                "community_comments",
                "community_post_likes",
                "community_profiles",
            ),
            requests.map { it.url.substringAfter("/rest/v1/").substringBefore('?') },
        )
        requests.forEach { request ->
            assertEquals("GET", request.method)
            assertEquals("public-key", request.headers["apikey"])
            assertEquals("application/json", request.headers["Accept"])
            assertFalse(request.headers.keys.any { it?.toString()?.equals("Authorization", ignoreCase = true) == true })
        }
    }
}
