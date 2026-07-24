package com.quata.web

import com.quata.core.platform.BrowserPreferenceStore
import com.quata.core.platform.BrowserPermissionService
import com.quata.core.platform.BrowserClipboardService
import com.quata.core.platform.BrowserAudioPlayerService
import com.quata.core.platform.BrowserAudioRecorderService
import com.quata.core.platform.BrowserFilePickerService
import com.quata.core.platform.BrowserLocationService
import com.quata.core.platform.BrowserShareService
import com.quata.core.platform.ClipboardService
import com.quata.core.platform.AudioPlayerService
import com.quata.core.platform.AudioRecorderService
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.LocationService
import com.quata.core.platform.PermissionService
import com.quata.core.platform.PreferenceStore
import com.quata.core.platform.ShareService
import com.quata.core.platform.PlatformServices

/** Services constructed by the browser launcher and passed to shared feature composition. */
data class WebPlatformServices(
    override val preferences: PreferenceStore = BrowserPreferenceStore(),
    override val clipboard: ClipboardService = BrowserClipboardService(),
    override val share: ShareService = BrowserShareService(),
    override val filePicker: FilePickerService = BrowserFilePickerService(),
    override val location: LocationService = BrowserLocationService(),
    override val permissions: PermissionService = BrowserPermissionService(),
    /** Not part of PlatformServices yet; exposed for future Web Chat host injection. */
    val audioPlayer: AudioPlayerService = BrowserAudioPlayerService(),
    /** Not part of PlatformServices yet; exposed for future Web Chat host injection. */
    val audioRecorder: AudioRecorderService = BrowserAudioRecorderService(),
) : PlatformServices
