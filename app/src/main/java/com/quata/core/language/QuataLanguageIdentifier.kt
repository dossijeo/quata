package com.quata.core.language

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QuataLanguageIdentifier {
    @Volatile
    private var detector: FastTextLanguageDetector? = null

    suspend fun detector(context: Context): FastTextLanguageDetector =
        detector ?: withContext(Dispatchers.IO) {
            detector ?: context.applicationContext.assets
                .open(FastTextLanguageDetector.ModelAssetName)
                .use { FastTextLanguageDetector.fromByteArray(it.readBytes()) }
                .also { detector = it }
        }

    suspend fun detect(context: Context, text: String): QuataLanguageDetection =
        detector(context).detect(text)

    suspend fun detectCode(context: Context, text: String): String =
        detector(context).detectCode(text)
}
