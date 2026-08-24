package com.quata.feature.postcomposer.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.quata.core.platform.PlatformFile
import com.quata.feature.postcomposer.imageeditor.ImageEditorAvatarOutputSpec
import com.quata.feature.postcomposer.imageeditor.ImageEditorOutputSpec
import com.quata.feature.postcomposer.imageeditor.ImageEditorPostOutputSpec
import com.quata.feature.postcomposer.imageeditor.PostImageEditorDialogContent
import com.quata.feature.postcomposer.imageeditor.PostImageEditorGeometry
import com.quata.feature.postcomposer.imageeditor.PostImageEditorStrings
import com.quata.feature.postcomposer.imageeditor.PostImageEditorTransform
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGContextRotateCTM
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import kotlin.math.PI

@Composable
internal fun IosPostImageEditor(
    source: PlatformFile,
    onDismiss: () -> Unit,
    onEdited: (PlatformFile) -> Unit,
) {
    var transform by remember(source.reference) { mutableStateOf(PostImageEditorTransform.Default) }
    val geometry = remember(source.reference, transform) { iosPostImageEditorGeometry(source, transform, ImageEditorPostOutputSpec) }
    val scope = rememberCoroutineScope()
    var saveInProgress by remember(source.reference) { mutableStateOf(false) }

    PostImageEditorDialogContent(
        transform = transform,
        geometry = geometry,
        outputSpec = ImageEditorPostOutputSpec,
        onTransformChange = { transform = it },
        onDismiss = onDismiss,
        onSave = { cropToOutputAspect ->
            if (saveInProgress) return@PostImageEditorDialogContent
            saveInProgress = true
            scope.launch {
                runCatching { iosPostImageEditorExport(source, transform, cropToOutputAspect) }
                    .onSuccess(onEdited)
                    .onFailure { saveInProgress = false }
            }
        },
        preview = { currentTransform, currentGeometry, cropPanelOpen, cropApplied, modifier ->
            IosComposerLocalImagePreview(
                source,
                modifier.graphicsLayer {
                    val cropToOutputAspect = cropPanelOpen || cropApplied
                    val scale = if (cropToOutputAspect) currentTransform.zoom else 1f
                    scaleX = scale
                    scaleY = scale
                    rotationZ = currentTransform.quarterTurns * 90f
                    translationX = if (cropToOutputAspect) currentTransform.panX * (currentGeometry?.maxPanX ?: 0f) else 0f
                    translationY = if (cropToOutputAspect) currentTransform.panY * (currentGeometry?.maxPanY ?: 0f) else 0f
                },
            )
        },
    )
}

@Composable
fun IosAvatarImageEditor(
    source: PlatformFile,
    onDismiss: () -> Unit,
    onEdited: (PlatformFile) -> Unit,
) {
    var transform by remember(source.reference) { mutableStateOf(PostImageEditorTransform.Default) }
    val geometry = remember(source.reference, transform) { iosPostImageEditorGeometry(source, transform, ImageEditorAvatarOutputSpec) }
    val scope = rememberCoroutineScope()
    var saveInProgress by remember(source.reference) { mutableStateOf(false) }

    PostImageEditorDialogContent(
        transform = transform,
        geometry = geometry,
        outputSpec = ImageEditorAvatarOutputSpec,
        onTransformChange = { transform = it },
        onDismiss = onDismiss,
        onSave = {
            if (saveInProgress) return@PostImageEditorDialogContent
            saveInProgress = true
            scope.launch {
                runCatching {
                    iosPostImageEditorExport(
                        source = source,
                        transform = transform,
                        cropToOutputAspect = true,
                        outputSpec = ImageEditorAvatarOutputSpec,
                        outputName = "profile-avatar-editor.jpg",
                    )
                }
                    .onSuccess(onEdited)
                    .onFailure { saveInProgress = false }
            }
        },
        cropLocked = true,
        strings = PostImageEditorStrings(
            title = "Edit profile photo",
            helper = "Drag, zoom or rotate the photo before saving.",
            rotate = "Rotate",
            reset = "Reset",
            cancel = "Cancel",
            save = "Save",
        ),
        preview = { currentTransform, currentGeometry, cropPanelOpen, cropApplied, modifier ->
            IosComposerLocalImagePreview(
                source,
                modifier.graphicsLayer {
                    val cropToOutputAspect = cropPanelOpen || cropApplied
                    val scale = if (cropToOutputAspect) currentTransform.zoom else 1f
                    scaleX = scale
                    scaleY = scale
                    rotationZ = currentTransform.quarterTurns * 90f
                    translationX = if (cropToOutputAspect) currentTransform.panX * (currentGeometry?.maxPanX ?: 0f) else 0f
                    translationY = if (cropToOutputAspect) currentTransform.panY * (currentGeometry?.maxPanY ?: 0f) else 0f
                },
            )
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun iosPostImageEditorGeometry(
    source: PlatformFile,
    transform: PostImageEditorTransform,
    outputSpec: ImageEditorOutputSpec,
): PostImageEditorGeometry? {
    val image = source.iosPostImageEditorImageOrNull() ?: return null
    val width = image.size.useContents { width }.toInt()
    val height = image.size.useContents { height }.toInt()
    if (width <= 0 || height <= 0) return null
    return com.quata.feature.postcomposer.imageeditor.postImageEditorGeometry(
        sourceWidth = width,
        sourceHeight = height,
        transform = transform,
        outputSpec = outputSpec,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun iosPostImageEditorExport(
    source: PlatformFile,
    transform: PostImageEditorTransform,
    cropToOutputAspect: Boolean,
    outputSpec: ImageEditorOutputSpec = ImageEditorPostOutputSpec,
    outputName: String = "post-image-editor.jpg",
): PlatformFile {
    val image = source.iosPostImageEditorImageOrNull() ?: error("ios_post_image_editor_decode_failed")
    val width = image.size.useContents { width }
    val height = image.size.useContents { height }
    require(width > 0.0 && height > 0.0) { "ios_post_image_editor_dimensions_invalid" }
    val turns = ((transform.quarterTurns % 4) + 4) % 4
    val outputWidth = if (cropToOutputAspect) outputSpec.width.toDouble() else if (turns % 2 == 0) width else height
    val outputHeight = if (cropToOutputAspect) outputSpec.height.toDouble() else if (turns % 2 == 0) height else width
    val scale = if (cropToOutputAspect) maxOf(outputWidth / width, outputHeight / height) * transform.zoom else 1.0
    val drawWidth = width * scale
    val drawHeight = height * scale
    val outputDrawnWidth = if (turns % 2 == 0) drawWidth else drawHeight
    val outputDrawnHeight = if (turns % 2 == 0) drawHeight else drawWidth
    val maxPanX = if (cropToOutputAspect) maxOf(0.0, (outputDrawnWidth - outputWidth) / 2.0) else 0.0
    val maxPanY = if (cropToOutputAspect) maxOf(0.0, (outputDrawnHeight - outputHeight) / 2.0) else 0.0

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(outputWidth, outputHeight), true, 1.0)
    val context = UIGraphicsGetCurrentContext() ?: error("ios_post_image_editor_context_unavailable")
    CGContextTranslateCTM(
        context,
        outputWidth / 2.0 + transform.panX.toDouble().coerceIn(-1.0, 1.0) * maxPanX,
        outputHeight / 2.0 + transform.panY.toDouble().coerceIn(-1.0, 1.0) * maxPanY,
    )
    CGContextRotateCTM(context, turns.toDouble() * PI / 2.0)
    image.drawInRect(CGRectMake(-drawWidth / 2.0, -drawHeight / 2.0, drawWidth, drawHeight))
    val output = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    val data = output?.let { UIImageJPEGRepresentation(it, 0.92) }
        ?: error("ios_post_image_editor_encode_failed")
    val path = NSTemporaryDirectory().trimEnd('/') + "/quata-${outputName.removeSuffix(".jpg")}-${NSUUID.UUID().UUIDString}.jpg"
    val url = NSURL.fileURLWithPath(path)
    if (!data.writeToFile(path, atomically = true)) error("ios_post_image_editor_write_failed")
    return PlatformFile(reference = url.absoluteString ?: path, displayName = outputName, mimeType = "image/jpeg")
}

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.iosPostImageEditorImageOrNull(): UIImage? {
    val url = iosPostImageEditorLocalUrl(reference) ?: return null
    val data = NSData.dataWithContentsOfURL(url) ?: return null
    return UIImage.imageWithData(data)
}

private fun iosPostImageEditorLocalUrl(reference: String): NSURL? {
    val value = reference.trim()
    return when {
        value.startsWith("file://") -> NSURL(string = value)
        value.startsWith("/") -> NSURL.fileURLWithPath(value)
        else -> null
    }?.takeIf { it.isFileURL() }
}
