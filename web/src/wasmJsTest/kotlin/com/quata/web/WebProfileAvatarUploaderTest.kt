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

        assertEquals(1080f, geometry.outputDrawnWidth)
        assertEquals(1920f, geometry.outputDrawnHeight)
        assertEquals(0f, geometry.maxPanX)
        assertEquals(420f, geometry.maxPanY)
    }

    @Test
    fun quarterTurnUsesRotatedOutputAxesForPortraitSourceSoVerticalPanCannotExposeAStripe() {
        val geometry = webProfileAvatarExportGeometry(
            sourceWidth = 900,
            sourceHeight = 1600,
            transform = AvatarImageEditorTransform.Default.rotateClockwise().rotateClockwise().rotateClockwise(),
        )

        assertEquals(1920f, geometry.outputDrawnWidth)
        assertEquals(1080f, geometry.outputDrawnHeight)
        assertEquals(420f, geometry.maxPanX)
        assertEquals(0f, geometry.maxPanY)
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
