package com.quata.feature.official.presentation

import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.data.toFoundationData
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.VideoThumbnailService
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
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
        val media = (gateway.pick(OfficialMediaType.Image) as IosOfficialMediaPickResult.Success).media

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

        assertTrue(gateway.pick(OfficialMediaType.Video) is IosOfficialMediaPickResult.Cancelled)
        assertEquals(listOf(FilePickerSource.Gallery, FilePickerSource.Documents), picker.requests.map { it.source })
        assertEquals(listOf("video/*"), picker.requests.last().acceptedMimeTypes)
    }

    @Test fun failureAndUnsupportedRemainDistinctVisibleUiStates() = runBlocking {
        val failure = gateway(RecordingPicker(PlatformResult.Failure("permission_denied"))).pick(OfficialMediaType.Image)
        val unsupported = gateway(RecordingPicker(PlatformResult.Unsupported, PlatformResult.Unsupported)).pick(OfficialMediaType.Image)
        assertEquals(IosOfficialMediaPickResult.Failure("permission_denied"), failure)
        assertTrue(unsupported is IosOfficialMediaPickResult.Unsupported)
    }

    @Test fun videoPreviewUsesGeneratedThumbnailInsteadOfOriginalMovie() = runBlocking {
        val thumbnail = PlatformFile("file:///tmp/quata_video_thumb_7.png", "thumb.png", "image/png")
        val gateway = IosOfficialEditorMediaGateway(
            picker = RecordingPicker(PlatformResult.Success(listOf(file.copy(displayName = "clip.mp4", mimeType = "video/mp4")))),
            transport = RecordingTransport(), videoThumbnails = RecordingThumbnailService(thumbnail),
            handleFactory = { "ios-official-media-7" }, cleanup = {},
        )
        val media = (gateway.pick(OfficialMediaType.Video) as IosOfficialMediaPickResult.Success).media
        assertEquals(thumbnail, gateway.previewFile(media))
    }

    @Test fun discardRemovesPreparedSelectionAndOwnedTemporaryFile() = runBlocking {
        val cleaned = mutableListOf<PlatformFile>()
        val gateway = gateway(cleanup = cleaned::add)
        val media = (gateway.pick(OfficialMediaType.Image) as IosOfficialMediaPickResult.Success).media

        gateway.discard(media)
        gateway.discard(media)

        assertEquals(listOf(file), cleaned)
    }

    @Test fun submitPreparesUploadsRewritesDraftAndReleases() = runBlocking {
        val transport = RecordingTransport()
        val cleaned = mutableListOf<PlatformFile>()
        val gateway = gateway(transport = transport, cleanup = cleaned::add)
        val media = (gateway.pick(OfficialMediaType.Image) as IosOfficialMediaPickResult.Success).media
        val repository = RecordingRepository()

        assertTrue(gateway.submit(repository, listOf(draft(media))).isSuccess)

        assertEquals(listOf("prepare-image:${file.reference}", "upload-image:profile-7:notice.jpg", "release:notice.jpg"), transport.calls)
        assertTrue(repository.created.all { it.mediaUrl == "https://cdn.example/notice.jpg" })
        assertEquals(listOf(file), cleaned)
    }

    @Test fun failedInsertRollsBackRemoteUploadThenReleasesAndCleansLocal() = runBlocking {
        val transport = RecordingTransport()
        val cleaned = mutableListOf<PlatformFile>()
        val gateway = gateway(transport = transport, cleanup = cleaned::add)
        val media = (gateway.pick(OfficialMediaType.Video) as IosOfficialMediaPickResult.Success).media
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
        val media = (gateway.pick(OfficialMediaType.Image) as IosOfficialMediaPickResult.Success).media

        assertTrue(gateway.submit(RecordingRepository(CancellationException("cancelled")), listOf(draft(media))).isFailure)
        assertTrue("rollback:remote-token" in transport.calls)
        assertTrue("release:notice.jpg" in transport.calls)
        assertEquals(listOf(file), cleaned)
    }

    @Test fun prepareFailureStillCleansPickerCopyWithoutPretendingItWasPrepared() = runBlocking {
        val transport = RecordingTransport(prepareFailure = IllegalStateException("decode_failed"))
        val cleaned = mutableListOf<PlatformFile>()
        val gateway = gateway(transport = transport, cleanup = cleaned::add)
        val media = (gateway.pick(OfficialMediaType.Image) as IosOfficialMediaPickResult.Success).media

        assertTrue(gateway.submit(RecordingRepository(), listOf(draft(media))).isFailure)
        assertEquals(listOf("prepare-image:${file.reference}"), transport.calls)
        assertEquals(listOf(file), cleaned)
    }

    @Test fun realCoroutineCancellationRunsRollbackInNonCancellableCleanup() = runBlocking {
        val enteredInsert = CompletableDeferred<Unit>()
        val transport = RecordingTransport()
        val cleaned = mutableListOf<PlatformFile>()
        val gateway = gateway(transport = transport, cleanup = cleaned::add)
        val media = (gateway.pick(OfficialMediaType.Image) as IosOfficialMediaPickResult.Success).media
        val repository = RecordingRepository(createBlock = { enteredInsert.complete(Unit); awaitCancellation() })

        val job = launch { gateway.submit(repository, listOf(draft(media))) }
        enteredInsert.await()
        job.cancelAndJoin()

        assertTrue("rollback:remote-token" in transport.calls)
        assertTrue("release:notice.jpg" in transport.calls)
        assertEquals(listOf(file), cleaned)
    }

    @Test fun everyTranslationDraftReceivesTheSingleUploadedUrl() = runBlocking {
        val gateway = gateway()
        val media = (gateway.pick(OfficialMediaType.Image) as IosOfficialMediaPickResult.Success).media
        val repository = RecordingRepository()

        gateway.submit(repository, listOf(draft(media), draft(media).copy(title = "Translation"))).getOrThrow()

        assertEquals(2, repository.created.size)
        assertTrue(repository.created.all { it.mediaUrl == "https://cdn.example/notice.jpg" })
    }

    @Test fun iosMountInstallsPickerPreviewAndDiscardSlotsIntoCommonRootContract() {
        val slots = iosOfficialEditorPlatformSlots(OfficialPostEditorStrings.forLanguage("en"), gateway())
        assertNotNull(slots.imagePicker)
        assertNotNull(slots.videoPicker)
        assertNotNull(slots.mediaPreview)
        assertNotNull(slots.discardMedia)
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test fun externalDisposalDeletesEveryOwnedOriginalAndThumbnailAndIsIdempotent() = runBlocking {
        val temporary = NSTemporaryDirectory()
        val imagePath = temporary + "quata_gallery_logout_owned.jpg"
        val videoPath = temporary + "quata_gallery_logout_owned.mp4"
        val thumbnailPath = temporary + "quata_video_thumbnail_logout_owned_123.png"
        val manager = NSFileManager.defaultManager
        listOf(imagePath, videoPath, thumbnailPath).forEach { path ->
            assertTrue(manager.createFileAtPath(path, "owned".encodeToByteArray().toFoundationData(), null))
        }
        val image = PlatformFile(NSURL.fileURLWithPath(imagePath).absoluteString!!, "owned.jpg", "image/jpeg")
        val video = PlatformFile(NSURL.fileURLWithPath(videoPath).absoluteString!!, "owned.mp4", "video/mp4")
        val thumbnail = PlatformFile(NSURL.fileURLWithPath(thumbnailPath).absoluteString!!, "owned.png", "image/png")
        val handles = ArrayDeque(listOf("ios-official-image", "ios-official-video"))
        val gateway = IosOfficialEditorMediaGateway(
            picker = RecordingPicker(PlatformResult.Success(listOf(image)), PlatformResult.Success(listOf(video))),
            transport = RecordingTransport(), videoThumbnails = RecordingThumbnailService(thumbnail),
            handleFactory = { handles.removeFirst() },
        )
        gateway.pick(OfficialMediaType.Image)
        gateway.pick(OfficialMediaType.Video)

        gateway.discardAll()
        gateway.discardAll()

        assertFalse(manager.fileExistsAtPath(imagePath))
        assertFalse(manager.fileExistsAtPath(videoPath))
        assertFalse(manager.fileExistsAtPath(thumbnailPath))
    }

    private fun gateway(
        picker: RecordingPicker = RecordingPicker(PlatformResult.Success(listOf(file))),
        transport: RecordingTransport = RecordingTransport(),
        cleanup: (PlatformFile) -> Unit = {},
    ) = IosOfficialEditorMediaGateway(
        picker = picker, transport = transport, handleFactory = { "ios-official-media-7" }, cleanup = cleanup,
    )

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

private class RecordingThumbnailService(private val thumbnail: PlatformFile) : VideoThumbnailService {
    override suspend fun createThumbnail(video: PlatformFile, maxWidth: Int) = PlatformResult.Success(thumbnail)
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
    override suspend fun rollbackUploadedMedia(media: ComposerUploadedMedia): Result<Unit> {
        // This suspension would abort immediately in the cancelled parent Job unless the gateway
        // invokes rollback inside NonCancellable.
        yield()
        calls += "rollback:${media.rollbackToken}"
        return Result.success(Unit)
    }
    override suspend fun releasePreparedMedia(media: ComposerPreparedMedia) = Result.success(Unit).also { calls += "release:${media.name}" }
}

private class RecordingRepository(
    private val createFailure: Throwable? = null,
    private val createBlock: (suspend () -> Unit)? = null,
) : OfficialRepository {
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
        createBlock?.invoke()
        return createFailure?.let { Result.failure(it) } ?: Result.success(null)
    }
    override suspend fun deletePost(postId: String) = Result.success(Unit)
    override suspend fun toggleLike(postId: String) = Result.success<OfficialPostItem?>(null)
    override suspend fun addComment(postId: String, comment: PostComment) = Result.success<OfficialPostItem?>(null)
    override suspend fun reportComment(commentId: String) = Result.success(Unit)
}
