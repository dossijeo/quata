package com.quata.core.language

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

object IosFastTextLanguageIdentifier : TextLanguageIdentifier {
    private val delegate = FastTextTextLanguageIdentifier(::readIosFastTextModelBytes)

    override suspend fun detect(text: String): QuataLanguageDetection =
        delegate.detect(text)
}

@OptIn(ExperimentalForeignApi::class)
private fun readIosFastTextModelBytes(): ByteArray {
    val path = NSBundle.mainBundle.pathForResource(
        name = FastTextLanguageDetector.ModelAssetName.substringBeforeLast('.'),
        ofType = FastTextLanguageDetector.ModelAssetName.substringAfterLast('.'),
    ) ?: error("Missing iOS FastText language model resource: ${FastTextLanguageDetector.ModelAssetName}")
    val data = NSData.dataWithContentsOfFile(path)
        ?: error("Unreadable iOS FastText language model resource: ${FastTextLanguageDetector.ModelAssetName}")
    val length = data.length.toInt()
    return if (length == 0) ByteArray(0) else data.bytes?.readBytes(length) ?: ByteArray(0)
}
