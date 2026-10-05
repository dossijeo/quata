package com.quata.feature.official.presentation

import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.core.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfficialPostMediaContractTest {
    @Test fun video_has_the_shared_inline_play_affordance() {
        officialInlineMediaContract(OfficialMediaType.Video).also { contract ->
            assertTrue(contract.showPlayButton)
            assertTrue(contract.requiresStillThumbnail)
        }
    }

    @Test fun image_has_no_video_play_affordance() {
        officialInlineMediaContract(OfficialMediaType.Image).also { contract ->
            assertFalse(contract.showPlayButton)
            assertFalse(contract.requiresStillThumbnail)
        }
    }

    @Test fun export_descriptor_refines_image_and_video_mime_from_the_exact_storage_object() {
        val image = officialPost("https://cdn.example/post.png?download=1", OfficialMediaType.Image)
        val video = officialPost("https://cdn.example/post.mp4", OfficialMediaType.Video)

        assertEquals("image/png", officialMediaFileExportDescriptor(image)?.mimeType)
        assertEquals("Qüata official.png", officialMediaFileExportDescriptor(image)?.displayName)
        assertEquals("video/mp4", officialMediaFileExportDescriptor(video)?.mimeType)
        assertEquals("Qüata official.mp4", officialMediaFileExportDescriptor(video)?.displayName)
    }
}

private fun officialPost(url: String, type: OfficialMediaType) = OfficialPostItem(
    id = "official-export",
    author = User("official-author", "official@example.invalid", "Official"),
    title = "Qüata official",
    summary = "summary",
    contentHtml = "<p>body</p>",
    contentPlain = "body",
    mediaUrl = url,
    mediaType = type,
    createdAt = "2026-10-05T00:00:00Z",
)
