package com.quata.core.camera

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

class ImageCompressor {
    fun compressJpeg(bitmap: Bitmap, quality: Int = 82): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}
