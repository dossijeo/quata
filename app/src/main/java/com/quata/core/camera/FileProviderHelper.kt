package com.quata.core.camera

import android.content.Context
import java.io.File

class FileProviderHelper(private val context: Context) {
    fun createTempImageFile(): File = File.createTempFile("quata_camera_", ".jpg", context.cacheDir)
}
