package com.quata.feature.official.presentation

import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialRepository
import com.quata.feature.postcomposer.data.ComposerPreparedMedia
import com.quata.feature.postcomposer.data.ComposerUploadedMedia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosOfficialEditorMediaContractTest {
    private val file = PlatformFile("file:///tmp/quata_gallery_7.jpg", "notice.jpg", "image/jpeg")

    @Test fun photosPickerMapsToOpaqueHandleWithMetadata() = runBlocking {
        val picker = RecordingPicker(PlatformResult.Success(listOf(file)))
        val gateway = gateway(picker = picker)
        val media = assertNotNull(gateway.pick(OfficialMediaType.Image))

        assertEquals(FilePickerSource.Gallery, picker.requests.single().source)
        assertEquals(listOf("image/*"), picker.requests.single().acceptedMimeTypes)
        assertEquals("ios-official-media-7", media.preparedHandle)
        assertEquals("notice.jpg", media.displayName)
        assertEquals("image/jpeg", media.mimeType)
        assertEquals("local://ios-official-media-7", media.url)
        assertFalse(media.url.contains(file.reference))
    }

    @Test fun unsupportedPhotosPickerFallsBackToFilesAndCancelProducesNoHandle() = runBlocking {
        val picker = RecordingPicker(PlatformResult.Unsupported, PlatformResult.Cancelled)
        val gateway = gateway(picker = picker)

        assertNull(gateway.pick(OfficialMediaType.Video))
        assertEquals(listOf(FilePickerSource.Gallery, FilePickerSource.Documents), picker.requests.map { it.source })
        assertEquals(listOf("video/*"), picker.requests.last().acceptedMimeTypes)
    }

    @Test fun discardRemovesPreparedSelectionAndOwnedTemporaryFile() = runBlocking {
        val cleaned = mutableListOf<PlatformFile>()
        val gateway = gateway(cleanup = cleaned::add)
        val media = assertNotNull(gateway.pick(OfficialMediaType.Image))

        gateway.discard(media)
        gateway.discard(media)

        assertEquals(listOf(file), cleaned)
    }

    @Test fun submitPreparesUploadsRewritesDraftAndReleases() = runBlocking {
        val transport = RecordingTransport()
        val cleaned = mutableListOf<PlatformFile>()
        val gateway = gateway(transport = transport, cleanup = cleaned::add)
        val media = assertNotNull(gateway.pick(OfficialMediaType.Image))
        val repository = RecordingRepository()

        assertTrue(gateway.submit(repository, listOf(draft(media))).isSuccess)

        assertEquals(listOf("prepare-image:${file.reference}", "upload-image:profile-7:notice.jpg", "release:notice.jpg"), transport.calls)
        assertEquals("https://cdn.example/notice.jpg", repository.created.single().mediaUrl)
        assertEquals(listOf(file), cleaned)
    }

    @Test fun failedInsertRollsBackRemoteUploadThenReleasesAndCleansLocal() = runBlocking {
        val transport = RecordingTransport()
        val cleaned = mutableListOf<PlatformFile>()
        val gateway = gateway(transport = transport, cleanup = cleaned::add)
        val media = assertNotNull(gateway.pick(OfficialMediaType.Video))
        val repository = RecordingRepository(createFailure = IllegalStateException("insert_failed"))

        assertTrue(gateway.submit(repository, listOf(draft(media))).isFailure)

        assertEquals(
            listOf("prepare-video:${file.reference}", "upload-video:profile-7:notice.jpg", "rollback:remote-token", "release:notice.jpg"),
            transport.calls,
        )
        assertEquals(listOf(file), cleaned)
    }

    @Test fun cancellationAfterUploadRollsBackAndCleansBothLifecycles() = runBlocking {
        val transport = RecordingTransport()
        val cleaned = mutableListOf<PlatformFile>()
        val gateway = gateway(transport = transport, cleanup = cleaned::add)
        val media = assertNotNull(gateway.pick(OfficialMediaType.Image))

        assertTrue(gateway.submit(RecordingRepository(CancellationException("cancelled")), listOf(draft(media))).isFailure)
        assertTrue("rollback:remote-token" in transport.calls)
        assertTrue("release:notice.jpg" in transport.calls)
        assertEquals(listOf(file), cleaned)
    }

    @Test fun prepareFailureStillCleansPickerCopyWithoutPretendingItWasPrepared() = runBlocking {
        val transport = RecordingTransport(prepareFailure = IllegalStateException("decode_failed"))
        val cleaned = mutableListOf<PlatformFile>()
        val gateway = gateway(transport = transport, cleanup = cleaned::add)
        val media = assertNotNull(gateway.pick(OfficialMediaType.Image))

        assertTrue(gateway.submit(RecordingRepository(), listOf(draft(media))).isFailure)
        assertEquals(listOf("prepare-image:${file.reference}"), transport.calls)
        assertEquals(listOf(file), cleaned)
    }

    @Test fun iosMountInstallsPickerPreviewAndDiscardSlotsIntoCommonRootContract() {
        val slots = iosOfficialEditorPlatformSlots(OfficialPostEditorStrings.forLanguage("en"), gateway())
        assertNotNull(slots.imagePicker)
        assertNotNull(slots.videoPicker)
        assertNotNull(slots.mediaPreview)
        assertNotNull(slots.discardMedia)
    }

    private fun gateway(
        picker: RecordingPicker = RecordingPicker(PlatformResult.Success(listOf(file))),
        transport: RecordingTransport = RecordingTransport(),
        cleanup: (PlatformFile) -> Unit = {},
    ) = IosOfficialEditorMediaGateway(picker, transport, { "ios-official-media-7" }, cleanup)

    private fun draft(media: OfficialEditorMedia) = OfficialPostDraft(
        title = "Notice", summary = "Summary", contentHtml = "<p>Body</p>",
        type = OfficialPostType.Announcement, mediaUrl = media.preparedHandle, mediaType = media.type,
    )
}

private class RecordingPicker(private vararg val outcomes: PlatformResult<List<PlatformFile>>) : FilePickerService {
    val requests = mutableListOf<FilePickerRequest>()
    override suspend fun pickFiles(acceptedMimeTypes: List<String>, allowMultiple: Boolean) = PlatformResult.Unsupported
    override suspend fun pick(request: FilePickerRequest): PlatformResult<List<PlatformFile>> {
        requests += request
        return outcomes[requests.lastIndex.coerceAtMost(outcomes.lastIndex)]
    }
}

private class RecordingTransport(private val prepareFailure: Throwable? = null) : IosOfficialEditorMediaTransport {
    val calls = mutableListOf<String>()
    private val prepared = ComposerPreparedMedia("file:///tmp/quata_gallery_7.jpg", "notice.jpg", "image/jpeg")
    private val uploaded = ComposerUploadedMedia("https://cdn.example/notice.jpg", "remote-token")
    override suspend fun prepareImage(reference: String): Result<ComposerPreparedMedia> {
        calls += "prepare-image:$reference"
        return prepareFailure?.let { Result.failure(it) } ?: Result.success(prepared)
    }
    override suspend fun prepareVideo(reference: String): Result<ComposerPreparedMedia> {
        calls += "prepare-video:$reference"
        return prepareFailure?.let { Result.failure(it) } ?: Result.success(prepared)
    }
    override suspend fun uploadImage(actorProfileId: String, media: ComposerPreparedMedia) = Result.success(uploaded).also { calls += "upload-image:$actorProfileId:${media.name}" }
    override suspend fun uploadVideo(actorProfileId: String, media: ComposerPreparedMedia) = Result.success(uploaded).also { calls += "upload-video:$actorProfileId:${media.name}" }
    override suspend fun rollbackUploadedMedia(media: ComposerUploadedMedia) = Result.success(Unit).also { calls += "rollback:${media.rollbackToken}" }
    override suspend fun releasePreparedMedia(media: ComposerPreparedMedia) = Result.success(Unit).also { calls += "release:${media.name}" }
}

private class RecordingRepository(private val createFailure: Throwable? = null) : OfficialRepository {
    val created = mutableListOf<OfficialPostDraft>()
    private val user = User("profile-7", "official@example.com", "Official", isOfficial = true)
    override fun observeOfficialFeed(): Flow<Result<List<OfficialPostItem>>> = flowOf(Result.success(emptyList()))
    override suspend fun getOfficialFeed() = Result.success(emptyList<OfficialPostItem>())
    override suspend fun refreshOfficialFeed() = Result.success(emptyList<OfficialPostItem>())
    override suspend fun loadOlderOfficialFeedPage(beforePublishedAt: String?, limit: Int) = Result.success(emptyList<OfficialPostItem>())
    override suspend fun getOfficialPost(postId: String) = Result.success<OfficialPostItem?>(null)
    override suspend fun refreshCurrentUser() = Result.success<User?>(user)
    override suspend fun createPost(draft: OfficialPostDraft) = createPosts(listOf(draft))
    override suspend fun createPosts(drafts: List<OfficialPostDraft>): Result<OfficialPostItem?> {
        created += drafts
        return createFailure?.let { Result.failure(it) } ?: Result.success(null)
    }
    override suspend fun deletePost(postId: String) = Result.success(Unit)
    override suspend fun toggleLike(postId: String) = Result.success<OfficialPostItem?>(null)
    override suspend fun addComment(postId: String, comment: PostComment) = Result.success<OfficialPostItem?>(null)
    override suspend fun reportComment(commentId: String) = Result.success(Unit)
}
