package com.quata.feature.postcomposer.imageeditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class PostImageEditorTransformTest {
    @Test
    fun post_crop_geometry_applies_zoom_rotation_and_normalized_pan() {
        val transform = PostImageEditorTransform.Default
            .withZoom(2f)
            .rotateClockwise()
        val geometry = postImageEditorGeometry(
            sourceWidth = 1600,
            sourceHeight = 900,
            transform = transform,
            outputSpec = ImageEditorPostOutputSpec,
        )

        assertClose(3840f, geometry.outputDrawnWidth)
        assertClose(6826.6665f, geometry.outputDrawnHeight)
        assertClose(1380f, geometry.maxPanX)
        assertClose(2453.3333f, geometry.maxPanY)

        val panned = postImageEditorPanAfterDrag(transform, geometry, dragX = 690f, dragY = -1226.6666f)
        assertClose(0.5f, panned.panX)
        assertClose(-0.5f, panned.panY)
    }

    @Test
    fun post_and_avatar_outputs_keep_distinct_aspect_contracts() {
        val transform = PostImageEditorTransform.Default.withZoom(1.5f)
        val post = postImageEditorGeometry(1200, 800, transform, ImageEditorPostOutputSpec)
        val avatar = postImageEditorGeometry(1200, 800, transform, ImageEditorAvatarOutputSpec)

        assertEquals(1080f / 1920f, ImageEditorPostOutputSpec.aspectRatio)
        assertEquals(1f, ImageEditorAvatarOutputSpec.aspectRatio)
        assertTrue(post.maxPanX > avatar.maxPanX)
        assertTrue(post.maxPanY > avatar.maxPanY)
    }

    @Test
    fun transform_bounds_are_clamped_for_every_platform_exporter() {
        val bounded = PostImageEditorTransform.Default
            .withZoom(20f)
            .withPan(-4f, 7f)

        assertEquals(MaximumPostImageEditorZoom, bounded.zoom)
        assertEquals(-1f, bounded.panX)
        assertEquals(1f, bounded.panY)
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.01f, "expected=$expected actual=$actual")
    }
}
