package com.quata.feature.neighborhoods.presentation

import com.quata.core.model.Post
import com.quata.core.model.User
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommunityProfilePostPreviewTest {
    @Test
    fun `shared profile preview owns the unloaded video affordance`() {
        val author = User("author", "", "Author")
        val video = Post("video", author, "", videoUrl = "https://example.invalid/video.mp4", createdAt = "now")
        val image = Post("image", author, "", imageUrl = "https://example.invalid/image.jpg", createdAt = "now")

        assertTrue(shouldShowProfileVideoStart(video, isVideoLoaded = false))
        assertFalse(shouldShowProfileVideoStart(video, isVideoLoaded = true))
        assertFalse(shouldShowProfileVideoStart(image, isVideoLoaded = false))
    }
}
