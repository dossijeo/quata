package com.quata.core.language

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object QuataLanguageIdentifier {
    private val identifierMutex = Mutex()

    @Volatile
    private var detector: FastTextLanguageDetector? = null

    @Volatile
    private var identifier: TextLanguageIdentifier? = null

    suspend fun identifier(context: Context): TextLanguageIdentifier =
        identifier ?: identifierMutex.withLock {
            identifier ?: FastTextTextLanguageIdentifier {
                readModelBytes(context.applicationContext)
            }.also { identifier = it }
        }

    suspend fun detector(context: Context): FastTextLanguageDetector =
        detector ?: withContext(Dispatchers.IO) {
            detector ?: FastTextLanguageDetector.fromByteArray(readModelBytes(context.applicationContext))
                .also { detector = it }
        }

    suspend fun detect(context: Context, text: String): QuataLanguageDetection =
        identifier(context).detect(text)

    suspend fun detectCode(context: Context, text: String): String =
        detect(context, text).code

    private suspend fun readModelBytes(context: Context): ByteArray =
        withContext(Dispatchers.IO) {
            context.assets
                .open(FastTextLanguageDetector.ModelAssetName)
                .use { it.readBytes() }
        }
}
