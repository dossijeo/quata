package com.quata.core.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.telephony.PhoneNumberUtils
import android.app.NotificationManager
import android.media.MediaRecorder
import java.io.File
import kotlin.math.abs
import java.util.Locale
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Android composition of the context-free shared platform contracts. Activity-bound picker and
 * permission prompts are still attached by the Android launcher, so features never retain it.
 */
class AndroidPlatformServices(
    override val preferences: PreferenceStore,
    override val clipboard: ClipboardService,
    override val share: ShareService,
    override val filePicker: FilePickerService,
    /** Defaults to the explicit unsupported result for compatibility with existing Android clients. */
    override val documentOpener: DocumentOpenService = UnsupportedDocumentOpenService,
    override val contacts: ContactPickerService = UnsupportedContactPickerService,
    override val location: LocationService,
    override val permissions: PermissionService,
) : PlatformServices

/**
 * Android-specific request passed from the context-free [FilePickerService] boundary to an
 * Activity-owned host. Keeping the Activity Result API out of this adapter lets features depend
 * only on the common contract.
 */
data class AndroidFilePickerRequest(
    val acceptedMimeTypes: List<String>,
    val allowMultiple: Boolean,
    val source: FilePickerSource,
)

fun interface AndroidFilePickerHost {
    suspend fun pick(request: AndroidFilePickerRequest): PlatformResult<List<Uri>>
}

/**
 * Resolves picked content URIs into the portable [PlatformFile] model. An Activity must attach an
 * [AndroidFilePickerHost] before requests can be launched; without an Activity result registry a
 * picker cannot be presented, so the service returns [PlatformResult.Unsupported] explicitly.
 */
class AndroidFilePickerService(context: Context) : FilePickerService {
    private val applicationContext = context.applicationContext
    private val requests = Mutex()

    @Volatile
    private var host: AndroidFilePickerHost? = null

    fun attachHost(host: AndroidFilePickerHost) {
        this.host = host
    }

    fun detachHost(host: AndroidFilePickerHost) {
        if (this.host === host) this.host = null
    }

    override suspend fun pickFiles(
        acceptedMimeTypes: List<String>,
        allowMultiple: Boolean,
    ): PlatformResult<List<PlatformFile>> = pick(
        FilePickerRequest(
            acceptedMimeTypes = acceptedMimeTypes,
            allowMultiple = allowMultiple,
            source = FilePickerSource.Documents,
        ),
    )

    override suspend fun pick(request: FilePickerRequest): PlatformResult<List<PlatformFile>> {
        if (request.source == FilePickerSource.Camera) return PlatformResult.Unsupported
        return requests.withLock {
            val activeHost = host ?: return@withLock PlatformResult.Unsupported
            when (
                val result = activeHost.pick(
                    AndroidFilePickerRequest(
                        acceptedMimeTypes = request.acceptedMimeTypes,
                        allowMultiple = request.allowMultiple,
                        source = request.source,
                    ),
                )
            ) {
                is PlatformResult.Success -> PlatformResult.Success(result.value.map(::platformFile))
                is PlatformResult.Failure -> result
                PlatformResult.Cancelled -> PlatformResult.Cancelled
                PlatformResult.Unsupported -> PlatformResult.Unsupported
            }
        }
    }

    private fun platformFile(uri: Uri): PlatformFile {
        var displayName: String? = null
        var sizeBytes: Long? = null
        runCatching {
            applicationContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    .takeIf { it >= 0 }
                    ?.let { displayName = cursor.getString(it) }
                cursor.getColumnIndex(OpenableColumns.SIZE)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { sizeBytes = cursor.getLong(it) }
            }
        }
        return PlatformFile(
            reference = uri.toString(),
            displayName = displayName,
            mimeType = applicationContext.contentResolver.getType(uri),
            sizeBytes = sizeBytes,
        )
    }
}

/**
 * Android address-book import behind the shared contact picker contract.
 *
 * The SOS UI owns the user gesture and the separate permission request. This adapter only reads
 * contacts when READ_CONTACTS is already granted and returns an explicit failure otherwise.
 */
class AndroidContactPickerService(context: Context) : ContactPickerService {
    private val applicationContext = context.applicationContext

    override suspend fun pickContacts(): PlatformResult<List<PlatformContact>> {
        if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return PlatformResult.Failure("contacts_permission_denied")
        }
        return runCatching {
            PlatformResult.Success(readContacts())
        }.getOrElse { error ->
            PlatformResult.Failure(error.message)
        }
    }

    private fun readContacts(): List<PlatformContact> {
        val contacts = linkedMapOf<String, MutablePlatformContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
        )
        applicationContext.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val contactIdColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val lookupKeyColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            val nameColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val normalizedColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(contactIdColumn)
                val lookupKey = cursor.getString(lookupKeyColumn).orEmpty()
                val id = lookupKey.ifBlank { contactId.toString() }
                val displayName = cursor.getString(nameColumn)?.trim().orEmpty()
                val displayPhone = cursor.getString(numberColumn)?.trim().orEmpty()
                val providerNormalizedPhone = cursor.getString(normalizedColumn).orEmpty()
                val internationalPhone = providerNormalizedPhone.takeIf { it.startsWith("+") }
                    ?: PhoneNumberUtils.formatNumberToE164(displayPhone, Locale.getDefault().country)
                val phones = listOf(displayPhone, providerNormalizedPhone, internationalPhone.orEmpty())
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                val contact = contacts.getOrPut(id) {
                    MutablePlatformContact(displayName.ifBlank { displayPhone })
                }
                contact.phones += phones
            }
        }
        return contacts.values.map { contact ->
            PlatformContact(
                displayName = contact.displayName.takeIf(String::isNotBlank),
                phones = contact.phones.distinct(),
            )
        }
    }

    private data class MutablePlatformContact(
        val displayName: String,
        val phones: MutableList<String> = mutableListOf(),
    )
}

class AndroidClipboardService(context: Context) : ClipboardService {
    private val applicationContext = context.applicationContext
    private val clipboard = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override suspend fun readText(): String? = clipboard.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(applicationContext)
        ?.toString()

    override suspend fun writeText(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("quata", text))
    }
}

class AndroidPreferenceStore(context: Context, name: String = "quata_platform") : PreferenceStore {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override suspend fun getString(key: String): String? = preferences.getString(key, null)

    override suspend fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override suspend fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }
}

/**
 * Context-only Android permission adapter. It reports real grant state without retaining an
 * Activity; hosts that own an Activity Result launcher can inject [requestFromHost] to perform
 * the prompt. Without that callback, requesting an ungranted permission is explicitly
 * unavailable instead of pretending that a prompt was shown.
 */
class AndroidPermissionService(
    context: Context,
    requestFromHost: (suspend (PlatformPermission) -> PermissionStatus)? = null
) : PermissionService {
    private val applicationContext = context.applicationContext
    private var requestFromHost: (suspend (PlatformPermission) -> PermissionStatus)? = requestFromHost

    fun attachHost(host: suspend (PlatformPermission) -> PermissionStatus) {
        requestFromHost = host
    }

    fun detachHost() {
        requestFromHost = null
    }

    override suspend fun status(permission: PlatformPermission): PermissionStatus = when (permission) {
        PlatformPermission.Files -> PermissionStatus.Granted // SAF grants URI access without a runtime permission.
        PlatformPermission.Notifications -> notificationStatus()
        PlatformPermission.Location -> permissionStatus(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        else -> permissionStatus(*permission.androidRuntimePermissions().toTypedArray())
    }

    override suspend fun request(permission: PlatformPermission): PermissionStatus {
        requestFromHost?.let { return it(permission) }
        return status(permission).takeIf { it == PermissionStatus.Granted } ?: PermissionStatus.Unavailable
    }

    private fun notificationStatus(): PermissionStatus {
        val notifications = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notifications.areNotificationsEnabled()) return PermissionStatus.Denied
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PermissionStatus.Granted
        } else {
            permissionStatus(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun permissionStatus(vararg permissions: String): PermissionStatus =
        if (permissions.any { ContextCompat.checkSelfPermission(applicationContext, it) != PackageManager.PERMISSION_GRANTED }) {
            PermissionStatus.Denied
        } else {
            PermissionStatus.Granted
        }
}

private fun PlatformPermission.androidRuntimePermissions(): List<String> = when (this) {
    PlatformPermission.Camera -> listOf(android.Manifest.permission.CAMERA)
    PlatformPermission.Microphone -> listOf(android.Manifest.permission.RECORD_AUDIO)
    PlatformPermission.Photos -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(android.Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    PlatformPermission.Videos -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(android.Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    PlatformPermission.Contacts -> listOf(android.Manifest.permission.READ_CONTACTS)
    PlatformPermission.Location,
    PlatformPermission.Notifications,
    PlatformPermission.Files -> emptyList()
}

class AndroidShareService(context: Context) : ShareService {
    private val applicationContext = context.applicationContext

    override suspend fun share(payload: SharePayload): PlatformResult<Unit> = runCatching {
        val files = payload.files.mapNotNull(::shareableUri)
        if (payload.files.isNotEmpty() && files.size != payload.files.size) return PlatformResult.Unsupported
        val intent = when {
            files.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files))
            }
            files.size == 1 -> Intent(Intent.ACTION_SEND).apply {
                type = payload.files.first().mimeType ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, files.first())
            }
            else -> Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
        }.apply {
            payload.text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            payload.title?.let { putExtra(Intent.EXTRA_TITLE, it) }
            if (files.isNotEmpty()) {
                clipData = ClipData.newRawUri("quata_attachment", files.first()).apply {
                    files.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        applicationContext.startActivity(Intent.createChooser(intent, payload.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        PlatformResult.Success(Unit)
    }.getOrElse { PlatformResult.Failure(it.message) }

    private fun shareableUri(file: PlatformFile): Uri? {
        val uri = Uri.parse(file.reference)
        if (uri.scheme == "content") return uri
        if (uri.scheme != "file") return null
        val path = uri.path?.let(::File) ?: return null
        if (!path.isFile || path.length() <= 0L || !path.isShareableLocalFile()) return null
        return FileProvider.getUriForFile(applicationContext, "${applicationContext.packageName}.fileprovider", path)
    }

    private fun File.isShareableLocalFile(): Boolean {
        val canonical = runCatching { canonicalFile }.getOrNull() ?: return false
        return listOf(applicationContext.cacheDir, applicationContext.filesDir).any { root ->
            val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return@any false
            canonical.path == canonicalRoot.path || canonical.path.startsWith("${canonicalRoot.path}${File.separator}")
        }
    }
}

class AndroidLocationService(context: Context) : LocationService {
    private val applicationContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(applicationContext)

    override suspend fun currentLocation(): PlatformResult<GeoLocation> {
        val hasFine = ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return PlatformResult.Failure("location_permission_denied")
        return runCatching {
            val priority = if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val location = client.getCurrentLocation(priority, null).await()
                ?: client.lastLocation.await()
                ?: return PlatformResult.Failure("location_unavailable")
            PlatformResult.Success(GeoLocation(location.latitude, location.longitude, location.accuracy, location.time))
        }.getOrElse { PlatformResult.Failure(it.message) }
    }
}

/** Android MediaRecorder adapter for the shared audio boundary. Playback remains a separate adapter. */
class AndroidAudioRecorderService(context: Context) : AudioRecorderService {
    private val applicationContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMillis: Long = 0L

    override suspend fun start(options: AudioRecordingOptions): PlatformResult<Unit> {
        if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return PlatformResult.Failure("microphone_permission_denied")
        }
        cancel()
        return runCatching {
            val file = File(applicationContext.cacheDir, "quata_audio_${System.currentTimeMillis()}.m4a")
            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(applicationContext)
            else MediaRecorder::class.java.getDeclaredConstructor().newInstance()
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(64_000)
                options.maxDurationMillis?.takeIf { it > 0 }?.let { setMaxDuration(it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = newRecorder
            outputFile = file
            startedAtMillis = System.currentTimeMillis()
            PlatformResult.Success(Unit)
        }.getOrElse {
            runCatching { outputFile?.delete() }
            recorder = null
            outputFile = null
            PlatformResult.Failure(it.message)
        }
    }

    override suspend fun stop(): PlatformResult<AudioRecording> {
        val activeRecorder = recorder ?: return PlatformResult.Failure("recorder_not_started")
        val file = outputFile ?: return PlatformResult.Failure("recording_file_missing")
        return runCatching {
            activeRecorder.stop()
            val duration = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
            if (file.length() == 0L) {
                PlatformResult.Failure("recording_empty")
            } else {
                PlatformResult.Success(AudioRecording(PlatformFile(file.toURI().toString(), file.name, "audio/mp4", file.length()), duration, "audio/mp4"))
            }
        }.getOrElse { PlatformResult.Failure(it.message) }.also { release(deleteFile = it !is PlatformResult.Success) }
    }

    override suspend fun cancel(): PlatformResult<Unit> {
        release(deleteFile = true)
        return PlatformResult.Success(Unit)
    }

    private fun release(deleteFile: Boolean) {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        if (deleteFile) runCatching { outputFile?.delete() }
        recorder = null
        outputFile = null
        startedAtMillis = 0L
    }
}

/** Thin Media3 adapter for the shared player boundary; callers own UI and lifecycle injection. */
class AndroidAudioPlayerService(context: Context) : AudioPlayerService {
    private val applicationContext = context.applicationContext
    private var player: ExoPlayer? = null
    private var sessionId = 0L
    private val eventSink = MutableSharedFlow<AudioPlaybackEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<AudioPlaybackEvent> = eventSink.asSharedFlow()

    override suspend fun load(file: PlatformFile): PlatformResult<AudioPlaybackState> = runCatching {
        if (!isSupportedLocalAudioReference(file.reference)) {
            error("android_audio_reference_remote_unsupported")
        }
        releasePlayer()
        val nextSessionId = ++sessionId
        ExoPlayer.Builder(applicationContext).build().also { newPlayer ->
            newPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (sessionId != nextSessionId) return
                    when (playbackState) {
                        Player.STATE_READY -> eventSink.tryEmit(AudioPlaybackEvent.StateChanged(currentState(AudioPlaybackPhase.Ready)))
                        Player.STATE_ENDED -> eventSink.tryEmit(AudioPlaybackEvent.Ended(currentState(AudioPlaybackPhase.Ended)))
                        Player.STATE_IDLE -> eventSink.tryEmit(AudioPlaybackEvent.StateChanged(currentState(AudioPlaybackPhase.Idle)))
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (sessionId != nextSessionId) return
                    eventSink.tryEmit(
                        AudioPlaybackEvent.StateChanged(
                            if (isPlaying) currentState(AudioPlaybackPhase.Playing) else currentState(),
                        ),
                    )
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (sessionId != nextSessionId) return
                    eventSink.tryEmit(AudioPlaybackEvent.Failed(currentState(AudioPlaybackPhase.Failed), error.message))
                }
            })
            newPlayer.setMediaItem(MediaItem.fromUri(file.reference))
            newPlayer.prepare()
            player = newPlayer
        }
        PlatformResult.Success(currentState(AudioPlaybackPhase.Loading))
    }.getOrElse { PlatformResult.Failure(it.message) }

    override suspend fun play(): PlatformResult<AudioPlaybackState> {
        val active = player ?: return PlatformResult.Failure("player_not_loaded")
        return runCatching {
            active.playWhenReady = true
            active.play()
            active.playerError?.let { error ->
                PlatformResult.Failure(error.message ?: "android_audio_play_failed")
            } ?: if (active !== player) {
                PlatformResult.Failure("android_audio_player_replaced")
            } else {
                PlatformResult.Success(currentState().copy(isPlaying = false, phase = AudioPlaybackPhase.Loading))
            }
        }.getOrElse { PlatformResult.Failure(it.message) }
    }

    override suspend fun pause(): PlatformResult<AudioPlaybackState> = playerOrFailure { active ->
        active.pause()
    }

    override suspend fun seekTo(positionMillis: Long): PlatformResult<AudioPlaybackState> {
        val active = player ?: return PlatformResult.Failure("player_not_loaded")
        return runCatching {
            val durationMillis = active.duration.takeIf { it > 0L } ?: 0L
            val target = if (durationMillis > 0L) {
                positionMillis.coerceIn(0L, durationMillis)
            } else {
                positionMillis.coerceAtLeast(0L)
            }
            active.seekTo(target)
            val state = awaitPlaybackState(
                active = active,
                predicate = {
                    it.playbackState == Player.STATE_ENDED ||
                        abs(it.currentPosition.coerceAtLeast(0L) - target) <= ANDROID_AUDIO_SEEK_CONFIRMATION_TOLERANCE_MS
                },
                timeoutMillis = 2_000L,
            )
            if (active !== player || abs(state.positionMillis - target) > ANDROID_AUDIO_SEEK_CONFIRMATION_TOLERANCE_MS) {
                PlatformResult.Failure("android_audio_seek_not_confirmed")
            } else {
                PlatformResult.Success(state.copy(durationMillis = durationMillis.takeIf { it > 0L } ?: state.durationMillis))
            }
        }.getOrElse { PlatformResult.Failure(it.message) }
    }

    override suspend fun stop(): PlatformResult<Unit> {
        releasePlayer()
        return PlatformResult.Success(Unit)
    }

    override suspend fun state(): AudioPlaybackState = currentState()

    private fun currentState(overridePhase: AudioPlaybackPhase? = null): AudioPlaybackState = player?.let { active ->
        val phase = overridePhase ?: when {
            active.playbackState == Player.STATE_ENDED -> AudioPlaybackPhase.Ended
            active.isPlaying -> AudioPlaybackPhase.Playing
            active.playbackState == Player.STATE_READY -> AudioPlaybackPhase.Ready
            active.playbackState == Player.STATE_BUFFERING -> AudioPlaybackPhase.Loading
            else -> AudioPlaybackPhase.Idle
        }
        AudioPlaybackState(
            isLoaded = active.playbackState != androidx.media3.common.Player.STATE_IDLE,
            isPlaying = active.isPlaying,
            positionMillis = active.currentPosition.coerceAtLeast(0L),
            durationMillis = active.duration.takeIf { it > 0L } ?: 0L,
            phase = phase,
            sessionId = sessionId,
        )
    } ?: AudioPlaybackState()

    private suspend fun playerOrFailure(action: (ExoPlayer) -> Unit): PlatformResult<AudioPlaybackState> {
        val active = player ?: return PlatformResult.Failure("player_not_loaded")
        return runCatching {
            action(active)
            PlatformResult.Success(currentState())
        }.getOrElse { PlatformResult.Failure(it.message) }
    }

    private suspend fun awaitPlaybackState(
        active: ExoPlayer,
        predicate: (ExoPlayer) -> Boolean,
        timeoutMillis: Long = 2_000L,
    ): AudioPlaybackState {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (active === player && !predicate(active) && System.currentTimeMillis() < deadline) {
            delay(50L)
        }
        return currentState()
    }

    private fun releasePlayer() {
        sessionId += 1L
        runCatching { player?.release() }
        player = null
    }

    private fun isSupportedLocalAudioReference(reference: String): Boolean {
        val trimmed = reference.trim()
        if (trimmed.isBlank()) return false
        val scheme = runCatching { Uri.parse(trimmed).scheme?.lowercase(Locale.US) }.getOrNull()
        return scheme == null || scheme == "file" || scheme == "content"
    }
}

private const val ANDROID_AUDIO_SEEK_CONFIRMATION_TOLERANCE_MS = 250L
