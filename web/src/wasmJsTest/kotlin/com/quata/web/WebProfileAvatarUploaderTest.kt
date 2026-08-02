package com.quata.web

import com.quata.feature.profile.data.ProfileAvatarUploader
import com.quata.feature.postcomposer.imageeditor.AvatarImageEditorTransform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.math.abs
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

class WebProfileAvatarUploaderTest {
    private val configuration = WebRuntimeConfiguration(
        supabaseUrl = "https://project.supabase.co",
        supabasePublishableKey = "public-key",
    )

    @Test
    fun avatarPlanUsesTheExistingCommunityPostsAvatarPathAndAuthenticatedHeaders() {
        val plan = webProfileAvatarUploadPlan(
            baseUrl = "https://project.supabase.co/",
            publishableKey = "public-key",
            accessToken = "session-token",
            profileId = "profile_1",
            token = "abc-123",
        )

        assertEquals("avatars/profile_1/abc123.jpg", plan.path)
        assertEquals(
            "https://project.supabase.co/storage/v1/object/community-posts/avatars/profile_1/abc123.jpg",
            plan.request.url,
        )
        assertEquals("Bearer session-token", plan.request.headers["Authorization"])
        assertEquals("public-key", plan.request.headers["apikey"])
        assertEquals("image/jpeg", plan.request.headers["Content-Type"])
        assertEquals("https://project.supabase.co/storage/v1/object/public/community-posts/avatars/profile_1/abc123.jpg", plan.publicUrl)
    }

    @Test
    fun avatarPlanRejectsAnUnsafeActorBeforeAStoragePathExists() {
        val failure = assertFailsWith<IllegalArgumentException> {
            webProfileAvatarUploadPlan("https://project.supabase.co", "key", "token", "profile/other", "token")
        }
        assertEquals("web_profile_avatar_actor_invalid", failure.message)
    }

    @Test
    fun persistedRemoteAvatarPassesThroughWithoutProcessingOrRelease() = runTest {
        val binary = RecordingBinary()
        val refs = RecordingReferences()
        val uploader = uploader(binary, refs)

        assertEquals("https://cdn.example/avatar.jpg", uploader.uploadIfNeeded("profile-1", " https://cdn.example/avatar.jpg "))
        assertTrue(binary.uploads.isEmpty())
        assertTrue(refs.released.isEmpty())
        assertEquals("https://cdn.example/avatar.jpg", webProfileAvatarUploadReference("https://cdn.example/avatar.jpg"))
    }

    @Test
    fun browserBlobIsSquaredUploadedWithTheAuthenticatedActorAndThenReleased() = runTest {
        val binary = RecordingBinary()
        val refs = RecordingReferences()
        val uploader = uploader(binary, refs)

        val result = uploader.uploadIfNeeded("profile-1", "blob:https://quata.example/input")

        assertEquals("https://project.supabase.co/storage/v1/object/public/community-posts/avatars/profile-1/fixedtoken.jpg", result)
        assertEquals(listOf("blob:https://quata.example/input"), binary.prepared)
        assertEquals(listOf("blob:https://quata.example/square"), binary.uploads.map { it.reference })
        assertEquals("Bearer access", binary.uploads.single().headers["Authorization"])
        assertEquals(listOf("blob:https://quata.example/square"), binary.revoked)
        assertEquals(listOf("blob:https://quata.example/input"), refs.released)
    }

    @Test
    fun confirmedEditorTransformIsPassedToTheJpegExportAndIsBounded() = runTest {
        val binary = RecordingBinary()
        val refs = RecordingReferences().apply {
            transforms["blob:https://quata.example/input"] = AvatarImageEditorTransform.Default
                .withZoom(2.25f).withPan(-0.4f, 0.7f).rotateClockwise()
        }
        val uploader = uploader(binary, refs)

        uploader.uploadIfNeeded("profile-1", "blob:https://quata.example/input")

        assertEquals(refs.transforms["blob:https://quata.example/input"], binary.transforms.single())
        assertEquals(2.25f, binary.transforms.single().zoom)
        assertEquals(1, binary.transforms.single().quarterTurns)
    }

    @Test
    fun quarterTurnUsesRotatedOutputAxesForLandscapeSourceSoHorizontalPanCannotExposeAStripe() {
        val geometry = webProfileAvatarExportGeometry(
            sourceWidth = 1600,
            sourceHeight = 900,
            transform = AvatarImageEditorTransform.Default.rotateClockwise(),
        )

        assertFloatClose(1080f, geometry.outputDrawnWidth)
        assertFloatClose(1920f, geometry.outputDrawnHeight)
        assertFloatClose(0f, geometry.maxPanX)
        assertFloatClose(420f, geometry.maxPanY)
    }

    @Test
    fun quarterTurnUsesRotatedOutputAxesForPortraitSourceSoVerticalPanCannotExposeAStripe() {
        val geometry = webProfileAvatarExportGeometry(
            sourceWidth = 900,
            sourceHeight = 1600,
            transform = AvatarImageEditorTransform.Default.rotateClockwise().rotateClockwise().rotateClockwise(),
        )

        assertFloatClose(1920f, geometry.outputDrawnWidth)
        assertFloatClose(1080f, geometry.outputDrawnHeight)
        assertFloatClose(420f, geometry.maxPanX)
        assertFloatClose(0f, geometry.maxPanY)
    }

    @Test
    fun previewAndExportShareTheSameCropForPortraitLandscapeZoomAndEveryQuarterTurn() {
        val sources = listOf(1200 to 800, 800 to 1200)
        val transforms = listOf(
            AvatarImageEditorTransform.Default,
            AvatarImageEditorTransform.Default.withZoom(2f).withPan(-0.35f, 0.6f),
        )
        val turns = listOf(0, 1, 3)

        sources.forEach { (width, height) ->
            transforms.forEach { base ->
                turns.forEach { turnsClockwise ->
                    val transform = (0 until turnsClockwise).fold(base) { value, _ -> value.rotateClockwise() }
                    val preview = webProfileAvatarExportGeometry(width, height, transform, outputSide = 280)
                    val export = webProfileAvatarExportGeometry(width, height, transform, outputSide = 1080)

                    assertFloatClose(export.outputDrawnWidth / 1080f, preview.outputDrawnWidth / 280f)
                    assertFloatClose(export.outputDrawnHeight / 1080f, preview.outputDrawnHeight / 280f)
                    assertFloatClose(export.maxPanX / 1080f, preview.maxPanX / 280f)
                    assertFloatClose(export.maxPanY / 1080f, preview.maxPanY / 280f)
                    assertFloatClose(
                        export.maxPanX * transform.panX / 1080f,
                        preview.maxPanX * transform.panX / 280f,
                    )
                    assertFloatClose(
                        export.maxPanY * transform.panY / 1080f,
                        preview.maxPanY * transform.panY / 280f,
                    )
                }
            }
        }
    }

    @Test
    fun previewDragMapsToExportNormalizedPanAndKeepsAZeroOverflowAxisLocked() {
        val transform = AvatarImageEditorTransform.Default.rotateClockwise()
        val preview = webProfileAvatarExportGeometry(1200, 800, transform, outputSide = 280)
        val afterDrag = webProfileAvatarPanAfterDrag(transform, preview, dragX = 100f, dragY = 35f)
        val export = webProfileAvatarExportGeometry(1200, 800, afterDrag, outputSide = 1080)

        assertFloatClose(0f, preview.maxPanX)
        assertFloatClose(0f, afterDrag.panX)
        assertFloatClose(35f, afterDrag.panY * preview.maxPanY)
        assertFloatClose(afterDrag.panY * export.maxPanY / 1080f, 35f / 280f)
        val reset = AvatarImageEditorTransform.Default
        assertEquals(1f, reset.zoom)
        assertEquals(0f, reset.panX)
        assertEquals(0f, reset.panY)
        assertEquals(0, reset.quarterTurns)
    }

    @Test
    fun avatarSourceMenuCentersUnderTheActualControlOnDesktopAndClampsOrFlipsOnMobile() {
        val desktop = webCenteredAvatarActionMenuOffset(
            anchorBounds = IntRect(left = 420, top = 300, right = 860, bottom = 348),
            windowSize = IntSize(1280, 720),
            popupContentSize = IntSize(240, 112),
        )
        assertEquals(IntOffset(520, 348), desktop)

        val mobile = webCenteredAvatarActionMenuOffset(
            anchorBounds = IntRect(left = 4, top = 580, right = 316, bottom = 628),
            windowSize = IntSize(320, 640),
            popupContentSize = IntSize(240, 112),
        )
        assertEquals(IntOffset(40, 468), mobile)
    }

    @Test
    fun prepareFailureReleasesTheOriginalBlobAndDoesNotUploadOrMutate() = runTest {
        val binary = RecordingBinary(failPrepare = true)
        val refs = RecordingReferences()
        val uploader = uploader(binary, refs)

        val failure = runCatching { uploader.uploadIfNeeded("profile-1", "blob:https://quata.example/input") }.exceptionOrNull()

        assertEquals("prepare_failed", failure?.message)
        assertTrue(binary.uploads.isEmpty())
        assertTrue(binary.revoked.isEmpty())
        assertEquals(listOf("blob:https://quata.example/input"), refs.released)
    }

    @Test
    fun mismatchedAuthenticatedActorReleasesTheBlobBeforeAnyPrepareOrUpload() = runTest {
        val binary = RecordingBinary()
        val refs = RecordingReferences()
        val uploader = uploader(binary, refs, sessionUserId = "another-profile")

        val failure = runCatching { uploader.uploadIfNeeded("profile-1", "blob:https://quata.example/input") }.exceptionOrNull()

        assertEquals("web_profile_avatar_actor_mismatch", failure?.message)
        assertTrue(binary.prepared.isEmpty())
        assertTrue(binary.uploads.isEmpty())
        assertTrue(binary.revoked.isEmpty())
        assertEquals(listOf("blob:https://quata.example/input"), refs.released)
    }

    @Test
    fun uploadFailureRevokesProcessedBlobAndReleasesOriginalWithoutReturningABlobReference() = runTest {
        val binary = RecordingBinary(failUpload = true)
        val refs = RecordingReferences()
        val uploader = uploader(binary, refs)

        val result = runCatching { uploader.uploadIfNeeded("profile-1", "blob:https://quata.example/input") }

        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
        assertEquals(listOf("blob:https://quata.example/square"), binary.revoked)
        assertEquals(listOf("blob:https://quata.example/input"), refs.released)
        assertFalse(result.getOrNull()?.startsWith("blob:") == true)
    }

    private fun uploader(
        binary: WebProfileAvatarBinaryTransport,
        refs: WebProfileAvatarReferenceStore,
        sessionUserId: String = "profile-1",
    ): ProfileAvatarUploader =
        WebProfileAvatarUploader(
            configuration = configuration,
            sessionForAuthenticatedRequest = {
                WebLocalSession("access", "refresh", "web-session", sessionUserId, expiresAt = Long.MAX_VALUE)
            },
            references = refs,
            binary = binary,
            token = { "fixed-token" },
        )

    /** 0.01px is far below one physical output pixel but accommodates Wasm Float division. */
    private fun assertFloatClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.01f, "Expected <$expected>, actual <$actual>.")
    }

    private class RecordingReferences : WebProfileAvatarReferenceStore {
        val released = mutableListOf<String>()
        val transforms = mutableMapOf<String, AvatarImageEditorTransform>()
        override suspend fun release(reference: String?) { reference?.let(released::add) }
        override fun editorTransform(reference: String) = transforms[reference] ?: AvatarImageEditorTransform.Default
    }

    private class RecordingBinary(
        private val failPrepare: Boolean = false,
        private val failUpload: Boolean = false,
    ) : WebProfileAvatarBinaryTransport {
        data class Upload(val reference: String, val url: String, val headers: Map<String, String>)
        val prepared = mutableListOf<String>()
        val transforms = mutableListOf<AvatarImageEditorTransform>()
        val uploads = mutableListOf<Upload>()
        val revoked = mutableListOf<String>()
        override suspend fun prepareSquareJpeg(reference: String, transform: AvatarImageEditorTransform): WebProfileAvatarPreparedImage {
            prepared += reference
            transforms += transform
            if (failPrepare) error("prepare_failed")
            return WebProfileAvatarPreparedImage("blob:https://quata.example/square")
        }
        override suspend fun upload(reference: String, url: String, key: String, token: String, mimeType: String) {
            uploads += Upload(reference, url, webComposerStorageUploadContract(url, key, token, mimeType).headers)
            if (failUpload) error("upload_failed")
        }
        override fun revokePrepared(reference: String) { revoked += reference }
    }
}
