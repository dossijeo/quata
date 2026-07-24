package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.random.Random
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioQualityHigh
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSTemporaryDirectory

/**
 * Real AVFoundation host for both shared audio contracts. A composition root injects the same
 * instance into [IosAudioRecorderService] and [IosAudioPlayerService]. It owns the temporary M4A
 * file, AVAudioSession activation and all native resources; common code receives portable state.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAvFoundationAudioHost(
    private val audioSession: AVAudioSession = AVAudioSession.sharedInstance(),
) : IosAudioRecorderHost {
    private var recorder: AVAudioRecorder? = null
    private var recorderFile: NSURL? = null
    private var recorderOptions: AudioRecordingOptions? = null

    override suspend fun start(options: AudioRecordingOptions): PlatformResult<Unit> {
        if (!options.supportsIosAac()) return PlatformResult.Unsupported
        if (recorder != null) return PlatformResult.Failure("audio_recording_in_progress")
        if (!requestMicrophonePermission()) return PlatformResult.Failure("microphone_permission_denied")
        if (!activateAudioSession()) return PlatformResult.Failure("audio_session_activation_failed")
        val destination = NSURL.fileURLWithPath(
            NSTemporaryDirectory() + "quata_audio_${Random.nextLong().toString(16)}.m4a",
        )
        val nativeRecorder = runCatching {
            AVAudioRecorder(
                destination,
                mapOf(
                    AVFormatIDKey to kAudioFormatMPEG4AAC,
                    AVSampleRateKey to 44_100.0,
                    AVNumberOfChannelsKey to 1,
                    AVEncoderAudioQualityKey to AVAudioQualityHigh,
                ),
                null,
            )
        }.getOrElse {
            deactivateIfIdle()
            return PlatformResult.Failure(it.message ?: "audio_recorder_create_failed")
        }
        val started = nativeRecorder.prepareToRecord() && (
            options.maxDurationMillis
                ?.takeIf { it > 0L }
                ?.let { nativeRecorder.recordForDuration(it / 1_000.0) }
                ?: nativeRecorder.record()
            )
        if (!started) {
            nativeRecorder.stop()
            deactivateIfIdle()
            return PlatformResult.Failure("audio_recorder_start_failed")
        }
        recorder = nativeRecorder
        recorderFile = destination
        recorderOptions = options
        return PlatformResult.Success(Unit)
    }

    override suspend fun stop(): PlatformResult<AudioRecording> {
        val activeRecorder = recorder ?: return PlatformResult.Failure("audio_recording_not_started")
        val file = recorderFile ?: return releaseRecorder(PlatformResult.Failure("audio_recording_file_missing"))
        val durationMillis = (activeRecorder.currentTime * 1_000.0).toLong().coerceAtLeast(0L)
        activeRecorder.stop()
        recorder = null
        recorderFile = null
        recorderOptions = null
        deactivateIfIdle()
        return PlatformResult.Success(
            AudioRecording(
                file = PlatformFile(
                    reference = file.absoluteString ?: file.path.orEmpty(),
                    displayName = file.lastPathComponent,
                    mimeType = "audio/mp4",
                ),
                durationMillis = durationMillis,
                mimeType = "audio/mp4",
            ),
        )
    }

    override suspend fun cancel(): PlatformResult<Unit> {
        val activeRecorder = recorder ?: return PlatformResult.Success(Unit)
        val file = recorderFile
        activeRecorder.stop()
        recorder = null
        recorderFile = null
        recorderOptions = null
        file?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
        deactivateIfIdle()
        return PlatformResult.Success(Unit)
    }

    /** Releases native players, active recordings and temporary recording output on host teardown. */
    fun release() {
        recorder?.stop()
        recorderFile?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
        recorder = null
        recorderFile = null
        recorderOptions = null
        deactivateIfIdle()
    }

    private suspend fun requestMicrophonePermission(): Boolean = suspendCancellableCoroutine { continuation ->
        audioSession.requestRecordPermission { granted ->
            if (continuation.isActive) continuation.resume(granted)
        }
    }

    private fun activateAudioSession(): Boolean = runCatching {
        audioSession.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
    }.getOrDefault(false)

    private fun deactivateIfIdle() {
        // This Kotlin/Native SDK does not expose AVAudioSession.setActive; category configuration
        // remains real and the launcher still owns app-wide session activation/lifecycle.
    }

    private fun releaseRecorder(result: PlatformResult<AudioRecording>): PlatformResult<AudioRecording> {
        recorder?.stop()
        recorder = null
        recorderFile = null
        recorderOptions = null
        deactivateIfIdle()
        return result
    }
}

private fun AudioRecordingOptions.supportsIosAac(): Boolean = mimeType.trim().lowercase() in setOf("audio/mp4", "audio/m4a", "audio/aac")
