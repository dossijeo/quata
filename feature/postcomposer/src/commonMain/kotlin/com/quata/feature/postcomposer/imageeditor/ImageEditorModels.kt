package com.quata.feature.postcomposer.imageeditor

enum class QuataImageEditorMode { Post, Avatar }

data class ImageEditorOutputSpec(val width: Int, val height: Int) {
    val aspectRatio: Float = width.toFloat() / height.toFloat()
}

val ImageEditorPostOutputSpec = ImageEditorOutputSpec(width = 1080, height = 1920)
val ImageEditorAvatarOutputSpec = ImageEditorOutputSpec(width = 1080, height = 1080)
