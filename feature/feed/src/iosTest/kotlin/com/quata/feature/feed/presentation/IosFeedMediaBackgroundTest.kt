package com.quata.feature.feed.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class IosFeedMediaBackgroundTest {
    @Test
    fun video_url_is_the_gradient_seed_even_when_no_frame_can_be_decoded() {
        assertEquals(
            "https://cdn.example.test/broken-video.mp4",
            iosFeedMediaBackgroundSeed(
                videoUrl = "https://cdn.example.test/broken-video.mp4",
                imageUrl = "https://cdn.example.test/thumbnail.jpg",
            ),
        )
    }

    @Test
    fun image_url_is_the_gradient_seed_when_post_has_no_video() {
        assertEquals(
            "https://cdn.example.test/photo.jpg",
            iosFeedMediaBackgroundSeed(
                videoUrl = "",
                imageUrl = "https://cdn.example.test/photo.jpg",
            ),
        )
    }
}
