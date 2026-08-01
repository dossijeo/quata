package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.IosFilePickerService
import com.quata.core.platform.IosViewControllerProvider
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.VideoThumbnailService
import com.quata.core.platform.IosVideoThumbnailService
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
import com.quata.feature.official.data.IosOfficialRuntimeConfiguration
import com.quata.feature.postcomposer.data.ComposerPreparedMedia
import com.quata.feature.postcomposer.data.ComposerUploadedMedia
import com.quata.feature.postcomposer.data.IosPostComposerTransport
import com.quata.feature.postcomposer.data.IosPostComposerRuntimeConfiguration
import com.quata.feature.postcomposer.presentation.IosComposerLocalImagePreview
import com.quata.feature.postcomposer.presentation.releaseIosComposerVideoThumbnail
import com.quata.core.session.IosRenewableAuthSession
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random

interface IosOfficialEditorMediaTransport {
    suspend fun prepareImage(reference: String): Result<ComposerPreparedMedia>
    suspend fun prepareVideo(reference: String): Result<ComposerPreparedMedia>
    suspend fun uploadImage(actorProfileId: String, media: ComposerPreparedMedia): Result<ComposerUploadedMedia>
    suspend fun uploadVideo(actorProfileId: String, media: ComposerPreparedMedia): Result<ComposerUploadedMedia>
    suspend fun rollbackUploadedMedia(media: ComposerUploadedMedia): Result<Unit>
    suspend fun releasePreparedMedia(media: ComposerPreparedMedia): Result<Unit>
}

sealed interface IosOfficialMediaPickResult {
    data class Success(val media: OfficialEditorMedia) : IosOfficialMediaPickResult
    data class Failure(val reason: String) : IosOfficialMediaPickResult
    data object Cancelled : IosOfficialMediaPickResult
    data object Unsupported : IosOfficialMediaPickResult
}

private class CreatePostOfficialMediaTransport(private val delegate: IosPostComposerTransport) : IosOfficialEditorMediaTransport {
    override suspend fun prepareImage(reference: String) = delegate.prepareImage(reference)
    override suspend fun prepareVideo(reference: String) = delegate.prepareVideo(reference)
    override suspend fun uploadImage(actorProfileId: String, media: ComposerPreparedMedia) = delegate.uploadImage(actorProfileId, media)
    override suspend fun uploadVideo(actorProfileId: String, media: ComposerPreparedMedia) = delegate.uploadVideo(actorProfileId, media)
    override suspend fun rollbackUploadedMedia(media: ComposerUploadedMedia) = delegate.rollbackUploadedMedia(media)
    override suspend fun releasePreparedMedia(media: ComposerPreparedMedia) = delegate.releasePreparedMedia(media)
}

/** Swift-facing factory that mounts both real PhotosUI and Files adapters. */
fun createIosOfficialEditorMediaGateway(
    presenterProvider: IosViewControllerProvider,
    configuration: IosOfficialRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
    videoThumbnails: VideoThumbnailService = IosVideoThumbnailService(),
): IosOfficialEditorMediaGateway {
    val picker = IosFilePickerService().apply {
        attachGalleryPicker(presenterProvider)
        attachDocumentPicker(presenterProvider)
    }
    val composerConfiguration = IosPostComposerRuntimeConfiguration(
        configuration.supabaseUrl, configuration.supabasePublishableKey, configuration.wordpressBaseUrl,
    )
    return IosOfficialEditorMediaGateway(
        picker, CreatePostOfficialMediaTransport(IosPostComposerTransport(composerConfiguration, authSession)), videoThumbnails,
    )
}

/** iOS-only ownership boundary for picker files. Tokens are opaque outside this class. */
class IosOfficialEditorMediaGateway(
    private val picker: FilePickerService,
    private val transport: IosOfficialEditorMediaTransport,
    private val videoThumbnails: VideoThumbnailService? = null,
    private val handleFactory: () -> String = { "ios-official-media-${Random.nextLong().toString(16)}" },
    private val cleanup: (PlatformFile) -> Unit = ::deleteOnlyOwnedTemporaryFile,
    private val previewCleanup: (PlatformFile) -> Unit = { releaseIosComposerVideoThumbnail(it); Unit },
) {
    private val selections = mutableMapOf<String, PlatformFile>()
    private val previews = mutableMapOf<String, PlatformFile>()

    suspend fun pick(type: OfficialMediaType): IosOfficialMediaPickResult {
        val mime = if (type == OfficialMediaType.Image) "image/*" else "video/*"
        val gallery = picker.pick(FilePickerRequest(listOf(mime), false, FilePickerSource.Gallery))
        val result = if (gallery == PlatformResult.Unsupported) {
            picker.pick(FilePickerRequest(listOf(mime), false, FilePickerSource.Documents))
        } else gallery
        return when (result) {
            is PlatformResult.Success -> result.value.firstOrNull()?.let { file ->
                val media = register(file, type)
                if (type == OfficialMediaType.Video) {
                    when (val thumbnail = videoThumbnails?.createThumbnail(file)) {
                        is PlatformResult.Success -> previews[media.preparedHandle!!] = thumbnail.value
                        else -> Unit
                    }
                }
                IosOfficialMediaPickResult.Success(media)
            } ?: IosOfficialMediaPickResult.Failure("ios_official_picker_empty")
            is PlatformResult.Failure -> IosOfficialMediaPickResult.Failure(result.reason ?: "ios_official_picker_failed")
            PlatformResult.Cancelled -> IosOfficialMediaPickResult.Cancelled
            PlatformResult.Unsupported -> IosOfficialMediaPickResult.Unsupported
        }
    }

    private fun register(file: PlatformFile, type: OfficialMediaType): OfficialEditorMedia {
        val handle = handleFactory()
        selections[handle] = file
        return iosOfficialPickedMedia(handle, file, type)
    }

    suspend fun discard(media: OfficialEditorMedia) {
        val handle = media.preparedHandle ?: return
        previews.remove(handle)?.let(previewCleanup)
        val file = selections.remove(handle) ?: return
        cleanup(file)
    }

    /** Idempotent owner shutdown used when UIKit removes the whole editor route (including logout). */
    suspend fun discardAll() = withContext(NonCancellable) {
        val ownedPreviews = previews.values.toList()
        val ownedSelections = selections.values.toList()
        previews.clear()
        selections.clear()
        ownedPreviews.forEach { runCatching { previewCleanup(it) } }
        ownedSelections.forEach { runCatching { cleanup(it) } }
    }

    fun previewFile(media: OfficialEditorMedia): PlatformFile? {
        val handle = media.preparedHandle ?: return null
        return previews[handle] ?: selections[handle]?.takeIf { media.type == OfficialMediaType.Image }
    }

    suspend fun submit(repository: OfficialRepository, drafts: List<OfficialPostDraft>): Result<OfficialPostItem?> = runCatching {
        val handle = drafts.firstOrNull()?.mediaUrl ?: return@runCatching repository.createPosts(drafts).getOrThrow()
        val file = selections[handle] ?: return@runCatching repository.createPosts(drafts).getOrThrow()
        val type = drafts.first().mediaType ?: error("ios_official_media_type_missing")
        var prepared: ComposerPreparedMedia? = null
        var uploaded: ComposerUploadedMedia? = null
        try {
            val currentPrepared = when (type) {
                OfficialMediaType.Image -> transport.prepareImage(file.reference).getOrThrow()
                OfficialMediaType.Video -> transport.prepareVideo(file.reference).getOrThrow()
            }
            prepared = currentPrepared
            val profileId = repository.refreshCurrentUser().getOrThrow()?.id ?: error("ios_official_profile_missing")
            uploaded = when (type) {
                OfficialMediaType.Image -> transport.uploadImage(profileId, currentPrepared).getOrThrow()
                OfficialMediaType.Video -> transport.uploadVideo(profileId, currentPrepared).getOrThrow()
            }
            repository.createPosts(drafts.map { it.copy(mediaUrl = uploaded.publicUrl) }).getOrThrow()
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                uploaded?.let { transport.rollbackUploadedMedia(it).exceptionOrNull()?.let(failure::addSuppressed) }
            }
            throw failure
        } finally {
            withContext(NonCancellable) {
                prepared?.let { transport.releasePreparedMedia(it) }
                discard(OfficialEditorMedia("", type, handle))
            }
        }
    }
}

/** Pure picker-result mapping: the shared tree sees metadata and an opaque token, never bytes. */
internal fun iosOfficialPickedMedia(handle: String, file: PlatformFile, type: OfficialMediaType) = OfficialEditorMedia(
    url = "local://$handle", type = type, preparedHandle = handle,
    displayName = file.displayName, mimeType = file.mimeType,
)

@Composable
internal fun IosOfficialMediaPickerButton(label: String, type: OfficialMediaType, gateway: IosOfficialEditorMediaGateway, onPicked: (OfficialEditorMedia) -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var error by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    Column(modifier) {
        Button(modifier = Modifier.fillMaxWidth(), onClick = { scope.launch {
            when (val result = gateway.pick(type)) {
                is IosOfficialMediaPickResult.Success -> { error = null; onPicked(result.media) }
                is IosOfficialMediaPickResult.Failure -> error = result.reason
                IosOfficialMediaPickResult.Cancelled -> error = "Selection cancelled. Tap to retry."
                IosOfficialMediaPickResult.Unsupported -> error = "Media picker unavailable."
            }
        } }) { Text(if (error == null) label else "Retry $label") }
        error?.let { Text(it) }
    }
}

/** Both PhotosUI and UIDocumentPicker(asCopy=true) provide app-owned temporary representations. */
@OptIn(ExperimentalForeignApi::class)
private fun deleteOnlyOwnedTemporaryFile(file: PlatformFile) {
    val url = NSURL(string = file.reference)?.takeIf { it.isFileURL() } ?: return
    val path = url.path ?: return
    if (!path.startsWith(NSTemporaryDirectory())) return
    NSFileManager.defaultManager.removeItemAtURL(url, error = null)
}
