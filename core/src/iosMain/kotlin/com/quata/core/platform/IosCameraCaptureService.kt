package com.quata.core.platform

import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlin.random.Random
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSTemporaryDirectory
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

/** Host-owned camera boundary, matching Android's launcher injection without leaking UIKit upward. */
fun interface IosCameraCaptureHost {
    suspend fun capturePhoto(request: CameraCaptureRequest): PlatformResult<PlatformFile>
}

/**
 * iOS [CameraCaptureService] that remains explicitly unsupported until a UIKit host is attached.
 * Requests are serialized because UIKit permits only one image-picker presentation at a time.
 */
class IosCameraCaptureService(
    host: IosCameraCaptureHost? = null,
) : CameraCaptureService {
    private val requests = Mutex()

    @Volatile
    private var host: IosCameraCaptureHost? = host

    fun attachHost(host: IosCameraCaptureHost) {
        this.host = host
    }

    fun detachHost(host: IosCameraCaptureHost) {
        if (this.host === host) this.host = null
    }

    override suspend fun capturePhoto(request: CameraCaptureRequest): PlatformResult<PlatformFile> = requests.withLock {
        val activeHost = host ?: return@withLock PlatformResult.Unsupported
        activeHost.capturePhoto(request)
    }
}

/**
 * Real UIKit still-camera host. It relies on the caller's active view controller and requires
 * `NSCameraUsageDescription` in the app target. The captured JPEG is copied to the app temporary
 * directory so the shared layer never receives a controller-owned bitmap.
 */
@OptIn(ExperimentalForeignApi::class)
class IosImagePickerCameraHost(
    private val presenterProvider: IosViewControllerProvider,
) : IosCameraCaptureHost {
    private var activeDelegate: IosImagePickerCameraDelegate? = null

    override suspend fun capturePhoto(request: CameraCaptureRequest): PlatformResult<PlatformFile> {
        if (!request.supportsIosJpeg()) return PlatformResult.Unsupported
        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            return PlatformResult.Unsupported
        }
        if (activeDelegate != null) return PlatformResult.Failure("camera_capture_in_progress")
        val presenter = presenterProvider.activeViewController() ?: return PlatformResult.Unsupported
        return suspendCancellableCoroutine { continuation ->
            val picker = UIImagePickerController().apply {
                sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            }
            lateinit var delegate: IosImagePickerCameraDelegate
            delegate = IosImagePickerCameraDelegate(request) { result ->
                if (activeDelegate === delegate) activeDelegate = null
                if (continuation.isActive) continuation.resume(result)
            }
            activeDelegate = delegate
            picker.delegate = delegate
            continuation.invokeOnCancellation {
                if (activeDelegate === delegate) {
                    activeDelegate = null
                    picker.dismissViewControllerAnimated(flag = true, completion = null)
                }
            }
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosImagePickerCameraDelegate(
    private val request: CameraCaptureRequest,
    private val complete: (PlatformResult<PlatformFile>) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    private var completed = false

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(flag = true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
            ?: return finish(PlatformResult.Failure("camera_capture_image_missing"))
        finish(image.toTemporaryPlatformFile(request))
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(flag = true, completion = null)
        finish(PlatformResult.Cancelled)
    }

    private fun finish(result: PlatformResult<PlatformFile>) {
        if (completed) return
        completed = true
        complete(result)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toTemporaryPlatformFile(request: CameraCaptureRequest): PlatformResult<PlatformFile> {
    val jpeg: NSData = UIImageJPEGRepresentation(this, 0.92) ?: return PlatformResult.Failure("camera_capture_jpeg_failed")
    val displayName = request.displayName
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.let { name -> if (name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true)) name else "$name.jpg" }
        ?: "quata_camera_${Random.nextLong().toString(16)}.jpg"
    val destination = NSURL.fileURLWithPath(NSTemporaryDirectory() + displayName)
    if (!jpeg.writeToFile(destination.path ?: return PlatformResult.Failure("camera_capture_path_missing"), atomically = true)) {
        return PlatformResult.Failure("camera_capture_write_failed")
    }
    return PlatformResult.Success(
        PlatformFile(
            reference = destination.absoluteString ?: destination.path.orEmpty(),
            displayName = destination.lastPathComponent,
            mimeType = "image/jpeg",
            sizeBytes = jpeg.length.toLong(),
        ),
    )
}

private fun CameraCaptureRequest.supportsIosJpeg(): Boolean = mimeType.trim().lowercase() in setOf("image/jpeg", "image/jpg")
