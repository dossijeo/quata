package com.quata.core.platform

import com.quata.core.data.toFoundationData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.random.Random
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSTemporaryDirectory

/**
 * Deterministic iOS UI-evidence recorder used only when the launcher process opts in through
 * QUATA_IOS_AUDIO_RECORDER_E2E_FAKE. The product path keeps using AVFoundation; this host only
 * removes the simulator microphone dependency while exercising the shared composer UI.
 */
@OptIn(ExperimentalForeignApi::class)
class IosEvidenceAudioRecorderHost : IosAudioRecorderHost {
    private var recorderFile: NSURL? = null
    private var recorderOptions: AudioRecordingOptions? = null

    override suspend fun start(options: AudioRecordingOptions): PlatformResult<Unit> {
        if (recorderFile != null) return PlatformResult.Failure("audio_recording_in_progress")
        val destination = NSURL.fileURLWithPath(
            NSTemporaryDirectory() + "quata_audio_e2e_${Random.nextLong().toString(16)}.m4a",
        )
        val path = destination.path ?: return PlatformResult.Failure("audio_recording_file_missing")
        val created = NSFileManager.defaultManager.createFileAtPath(
            path,
            QuataIosEvidenceAudioBytes.toFoundationData(),
            null,
        )
        if (!created) return PlatformResult.Failure("audio_recording_file_create_failed")
        recorderFile = destination
        recorderOptions = options
        return PlatformResult.Success(Unit)
    }

    override suspend fun stop(): PlatformResult<AudioRecording> {
        val file = recorderFile ?: return PlatformResult.Failure("audio_recording_not_started")
        val options = recorderOptions ?: AudioRecordingOptions()
        recorderFile = null
        recorderOptions = null
        return PlatformResult.Success(
            AudioRecording(
                file = PlatformFile(
                    reference = file.absoluteString ?: file.path.orEmpty(),
                    displayName = file.lastPathComponent ?: "quata-audio-e2e.m4a",
                    mimeType = options.mimeType,
                ),
                durationMillis = 1_250L,
                mimeType = options.mimeType,
            ),
        )
    }

    override suspend fun cancel(): PlatformResult<Unit> {
        recorderFile?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
        recorderFile = null
        recorderOptions = null
        return PlatformResult.Success(Unit)
    }
}

private val QuataIosEvidenceAudioBytes = byteArrayOf(
    0x71, 0x75, 0x61, 0x74, 0x61, 0x2d, 0x69, 0x6f,
    0x73, 0x2d, 0x61, 0x75, 0x64, 0x69, 0x6f, 0x2d,
    0x65, 0x32, 0x65,
)
