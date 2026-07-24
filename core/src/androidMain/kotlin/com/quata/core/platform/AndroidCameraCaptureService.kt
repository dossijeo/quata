package com.quata.core.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Activity-owned capture bridge; implementations return a persisted/readable content URI. */
fun interface AndroidCameraCaptureHost {
    suspend fun capturePhoto(request: CameraCaptureRequest): PlatformResult<PlatformFile>
}

/**
 * Real Android camera boundary. Permission prompting and Activity Result launching are injected by
 * the launcher; this service only verifies the grant and serializes capture requests.
 */
class AndroidCameraCaptureService(context: Context) : CameraCaptureService {
    private val applicationContext = context.applicationContext
    private val requests = Mutex()

    @Volatile
    private var host: AndroidCameraCaptureHost? = null

    fun attachHost(host: AndroidCameraCaptureHost) {
        this.host = host
    }

    fun detachHost(host: AndroidCameraCaptureHost) {
        if (this.host === host) this.host = null
    }

    override suspend fun capturePhoto(request: CameraCaptureRequest): PlatformResult<PlatformFile> {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return PlatformResult.Failure("camera_permission_denied")
        }
        return requests.withLock {
            val activeHost = host ?: return@withLock PlatformResult.Unsupported
            activeHost.capturePhoto(request)
        }
    }
}
