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
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
import com.quata.feature.postcomposer.data.ComposerPreparedMedia
import com.quata.feature.postcomposer.data.ComposerUploadedMedia
import com.quata.feature.postcomposer.data.IosPostComposerTransport
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.darwin.NSObject
import platform.posix.unlink
import kotlin.random.Random

/** iOS-only ownership boundary for picker files. Tokens are opaque outside this class. */
class IosOfficialEditorMediaGateway(
    private val picker: FilePickerService,
    private val transport: IosPostComposerTransport,
) {
    private val selections = mutableMapOf<String, PlatformFile>()

    suspend fun pick(type: OfficialMediaType): OfficialEditorMedia? = when (val result = picker.pick(
        FilePickerRequest(listOf(if (type == OfficialMediaType.Image) "image/*" else "video/*"), false, FilePickerSource.Gallery),
    )) {
        is PlatformResult.Success -> result.value.firstOrNull()?.let { file -> register(file, type) }
        else -> null
    }

    private fun register(file: PlatformFile, type: OfficialMediaType): OfficialEditorMedia {
        val handle = "ios-official-media-${Random.nextLong().toString(16)}"
        selections[handle] = file
        return iosOfficialPickedMedia(handle, file, type)
    }

    suspend fun discard(media: OfficialEditorMedia) {
        val file = media.preparedHandle?.let(selections::remove) ?: return
        deleteOnlyOwnedTemporaryFile(file)
    }

    suspend fun submit(repository: OfficialRepository, drafts: List<OfficialPostDraft>): Result<OfficialPostItem?> = runCatching {
        val handle = drafts.firstOrNull()?.mediaUrl ?: return@runCatching repository.createPosts(drafts).getOrThrow()
        val file = selections[handle] ?: return@runCatching repository.createPosts(drafts).getOrThrow()
        val type = drafts.first().mediaType ?: error("ios_official_media_type_missing")
        val prepared = ComposerPreparedMedia(file.reference, file.displayName ?: fallbackName(type), file.mimeType ?: fallbackMime(type))
        var uploaded: ComposerUploadedMedia? = null
        try {
            uploaded = when (type) {
                OfficialMediaType.Image -> transport.uploadImage(repository.refreshCurrentUser().getOrThrow()?.id ?: error("ios_official_profile_missing"), prepared).getOrThrow()
                OfficialMediaType.Video -> transport.uploadVideo(repository.refreshCurrentUser().getOrThrow()?.id ?: error("ios_official_profile_missing"), prepared).getOrThrow()
            }
            repository.createPosts(drafts.map { it.copy(mediaUrl = uploaded.publicUrl) }).getOrThrow()
        } catch (failure: Throwable) {
            uploaded?.let { transport.rollbackUploadedMedia(it).exceptionOrNull()?.let(failure::addSuppressed) }
            throw failure
        } finally {
            withContext(NonCancellable) { transport.releasePreparedMedia(prepared); discard(OfficialEditorMedia("", type, handle)) }
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

private fun fallbackName(type: OfficialMediaType) = if (type == OfficialMediaType.Image) "official-image.jpg" else "official-video.mp4"
private fun fallbackMime(type: OfficialMediaType) = if (type == OfficialMediaType.Image) "image/jpeg" else "video/mp4"

/** PhotosUI copies into NSTemporaryDirectory; never delete arbitrary document/provider references. */
@OptIn(ExperimentalForeignApi::class)
private fun deleteOnlyOwnedTemporaryFile(file: PlatformFile) {
    val url = NSURL(string = file.reference)?.takeIf { it.isFileURL() } ?: return
    val path = url.path ?: return
    if (!path.contains("/tmp/quata_gallery_")) return
    NSFileManager.defaultManager.removeItemAtURL(url, error = null)
}
