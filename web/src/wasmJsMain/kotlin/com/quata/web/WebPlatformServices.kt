package com.quata.web

import com.quata.core.platform.BrowserPreferenceStore
import com.quata.core.platform.BrowserPermissionService
import com.quata.core.platform.BrowserCameraCaptureService
import com.quata.core.platform.BrowserClipboardService
import com.quata.core.platform.BrowserDocumentTextReader
import com.quata.core.platform.BrowserDocumentOpenService
import com.quata.core.platform.BrowserContactPickerService
import com.quata.core.platform.BrowserAudioPlayerService
import com.quata.core.platform.BrowserAudioRecorderService
import com.quata.core.platform.BrowserAudioCacheService
import com.quata.core.platform.BrowserFilePickerService
import com.quata.core.platform.BrowserFileCacheService
import com.quata.core.platform.BrowserLocationService
import com.quata.core.platform.BrowserShareService
import com.quata.core.platform.BrowserVideoThumbnailService
import com.quata.core.platform.BrowserImageMetadataService
import com.quata.core.platform.ClipboardService
import com.quata.core.platform.DocumentTextReader
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.ContactPickerService
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.AudioCacheService
import com.quata.core.platform.AudioRecordingReferenceReleaser
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.FileCacheService
import com.quata.core.platform.LocationService
import com.quata.core.platform.PermissionService
import com.quata.core.platform.PreferenceStore
import com.quata.core.platform.ShareService
import com.quata.core.platform.VideoThumbnailService
import com.quata.core.platform.ImageMetadataService
import com.quata.core.platform.PlatformServices

/** Services constructed by the browser launcher and passed to shared feature composition. */
data class WebPlatformServices(
    override val preferences: PreferenceStore = BrowserPreferenceStore(),
    override val clipboard: ClipboardService = BrowserClipboardService(),
    override val share: ShareService = BrowserShareService(),
    override val filePicker: FilePickerService = BrowserFilePickerService(),
    /** IndexedDB binary cache, kept separate from the small key/value [PreferenceStore] boundary. */
    private val browserFileCache: BrowserFileCacheService = BrowserFileCacheService(),
    val fileCache: FileCacheService = browserFileCache,
    override val contacts: ContactPickerService = BrowserContactPickerService(),
    override val location: LocationService = BrowserLocationService(),
    override val permissions: PermissionService = BrowserPermissionService(),
    /** Real still capture via MediaDevices; callers release returned Blob URLs after consumption. */
    val cameraCapture: BrowserCameraCaptureService = BrowserCameraCaptureService(),
      /** Browser decoder/canvas thumbnail boundary; unsupported codecs return an explicit result. */
      val videoThumbnails: VideoThumbnailService = BrowserVideoThumbnailService(),
      /** Browser text reader exposed for a future shared document-preview host; Office/PDF remain unsupported. */
      val documentTextReader: DocumentTextReader = BrowserDocumentTextReader(),
      /** Native PDF viewing and browser download boundary for RTF/Office attachments. */
      override val documentOpener: DocumentOpenService = BrowserDocumentOpenService(),
      /** Read-only metadata for files returned by the browser gallery picker. */
      val imageMetadata: ImageMetadataService = BrowserImageMetadataService(),
    /**
     * IndexedDB-backed audio cache with explicit release of its issued Blob URLs.
     * Browser storage quotas and eviction still apply; callers must tolerate cache misses.
     */
    val audioCache: AudioCacheService = BrowserAudioCacheService(browserFileCache),
    /** Not part of PlatformServices yet; exposed for Web Chat host injection. */
    val audioPlayer: AudioPlayerService = BrowserAudioPlayerService(),
    private val browserAudioRecorder: BrowserAudioRecorderService = BrowserAudioRecorderService(),
    /** Real MediaRecorder adapter; its returned Blob URLs are released through [audioRecordingReferences]. */
    val audioRecorder: AudioRecorderService = browserAudioRecorder,
    val audioRecordingReferences: AudioRecordingReferenceReleaser = browserAudioRecorder,
) : PlatformServices
