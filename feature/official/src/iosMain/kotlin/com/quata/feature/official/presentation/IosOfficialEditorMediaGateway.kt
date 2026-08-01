package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.FilePickerSource
import com.quata.core.platform.IosFilePickerService
import com.quata.core.platform.IosViewControllerProvider
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
import com.quata.feature.official.data.IosOfficialRuntimeConfiguration
import com.quata.feature.postcomposer.data.ComposerPreparedMedia
import com.quata.feature.postcomposer.data.ComposerUploadedMedia
import com.quata.feature.postcomposer.data.IosPostComposerTransport
import com.quata.feature.postcomposer.data.IosPostComposerRuntimeConfiguration
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
): IosOfficialEditorMediaGateway {
    val picker = IosFilePickerService().apply {
        attachGalleryPicker(presenterProvider)
        attachDocumentPicker(presenterProvider)
    }
    val composerConfiguration = IosPostComposerRuntimeConfiguration(
        configuration.supabaseUrl, configuration.supabasePublishableKey, configuration.wordpressBaseUrl,
    )
    return IosOfficialEditorMediaGateway(picker, CreatePostOfficialMediaTransport(IosPostComposerTransport(composerConfiguration, authSession)))
}

/** iOS-only ownership boundary for picker files. Tokens are opaque outside this class. */
class IosOfficialEditorMediaGateway(
    private val picker: FilePickerService,
    private val transport: IosOfficialEditorMediaTransport,
    private val handleFactory: () -> String = { "ios-official-media-${Random.nextLong().toString(16)}" },
    private val cleanup: (PlatformFile) -> Unit = ::deleteOnlyOwnedTemporaryFile,
) {
    private val selections = mutableMapOf<String, PlatformFile>()

    suspend fun pick(type: OfficialMediaType): OfficialEditorMedia? {
        val mime = if (type == OfficialMediaType.Image) "image/*" else "video/*"
        val gallery = picker.pick(FilePickerRequest(listOf(mime), false, FilePickerSource.Gallery))
        val result = if (gallery == PlatformResult.Unsupported) {
            picker.pick(FilePickerRequest(listOf(mime), false, FilePickerSource.Documents))
        } else gallery
        return when (result) {
        is PlatformResult.Success -> result.value.firstOrNull()?.let { file -> register(file, type) }
        else -> null
        }
    }

    private fun register(file: PlatformFile, type: OfficialMediaType): OfficialEditorMedia {
        val handle = handleFactory()
        selections[handle] = file
        return iosOfficialPickedMedia(handle, file, type)
    }

    suspend fun discard(media: OfficialEditorMedia) {
        val file = media.preparedHandle?.let(selections::remove) ?: return
        cleanup(file)
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
            uploaded?.let { transport.rollbackUploadedMedia(it).exceptionOrNull()?.let(failure::addSuppressed) }
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
    Button(modifier = modifier, onClick = { scope.launch { gateway.pick(type)?.let(onPicked) } }) { Text(label) }
}

/** Both PhotosUI and UIDocumentPicker(asCopy=true) provide app-owned temporary representations. */
@OptIn(ExperimentalForeignApi::class)
private fun deleteOnlyOwnedTemporaryFile(file: PlatformFile) {
    val url = NSURL(string = file.reference)?.takeIf { it.isFileURL() } ?: return
    val path = url.path ?: return
    if (!path.startsWith(NSTemporaryDirectory())) return
    NSFileManager.defaultManager.removeItemAtURL(url, error = null)
}
