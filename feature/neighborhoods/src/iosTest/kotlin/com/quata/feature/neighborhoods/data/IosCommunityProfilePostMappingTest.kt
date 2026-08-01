package com.quata.feature.neighborhoods.data

import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import kotlin.test.Test
import kotlin.test.assertEquals

class IosCommunityProfilePostMappingTest {
    @Test fun `profile post maps backend media into visible attachments`() {
        val post = mapOf(
            "id" to "post_1",
            "body" to "hola",
            "image_url" to "https://cdn.example/image.jpg",
            "video_url" to "https://cdn.example/video.mp4",
            "created_at" to "2026-01-01T00:00:00Z",
        ).toIosCommunityProfilePost(NeighborhoodUser("profile_1", "Ana", "", "Centro"))

        assertEquals("hola", post.text)
        assertEquals(2, post.toIosProfileAttachments().size)
        assertEquals("image/*", post.toIosProfileAttachments().first().mimeType)
    }

    @Test fun `moderation payloads match Android RPC arguments`() {
        assertEquals(
            "{\"p_actor_profile_id\":\"me\",\"p_target_type\":\"profile\",\"p_target_id\":\"peer\",\"p_reason\":\"other\"}",
            iosNeighborhoodReportPayload("me", "profile", "peer"),
        )
        assertEquals("{\"p_actor_profile_id\":\"me\",\"p_profile_id\":\"peer\"}", iosNeighborhoodBlockPayload("me", "peer"))
    }
}
