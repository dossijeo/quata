package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermission
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionRecordPermissionUndetermined
import kotlin.coroutines.resume

/**
 * Native microphone authorization boundary for iOS.
 *
 * This service only reports and requests `NSMicrophoneUsageDescription` authorization through
 * `AVAudioSession`; it deliberately does not configure an audio category, activate a session, or
 * start recording. Those lifecycle responsibilities remain with the injected audio host.
 */
@OptIn(ExperimentalForeignApi::class)
class IosMicrophonePermissionService(
    private val audioSession: AVAudioSession = AVAudioSession.sharedInstance(),
) : PermissionService {
    override suspend fun status(permission: PlatformPermission): PermissionStatus = when (permission) {
        PlatformPermission.Microphone -> audioSession.recordPermission.toPermissionStatus()
        else -> PermissionStatus.Unavailable
    }

    override suspend fun request(permission: PlatformPermission): PermissionStatus {
        if (permission != PlatformPermission.Microphone) return PermissionStatus.Unavailable

        val current = audioSession.recordPermission.toPermissionStatus()
        if (current != PermissionStatus.Denied) return current

        return suspendCancellableCoroutine { continuation ->
            audioSession.requestRecordPermission { granted ->
                if (continuation.isActive) {
                    continuation.resume(
                        if (granted) PermissionStatus.Granted
                        else audioSession.recordPermission.toPermissionStatus(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun AVAudioSessionRecordPermission.toPermissionStatus(): PermissionStatus = when (this) {
    AVAudioSessionRecordPermissionGranted -> PermissionStatus.Granted
    AVAudioSessionRecordPermissionUndetermined -> PermissionStatus.Denied
    AVAudioSessionRecordPermissionDenied -> PermissionStatus.PermanentlyDenied
    else -> PermissionStatus.Unavailable
}
