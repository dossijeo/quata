package com.quata.core.language

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FastTextTextLanguageIdentifier(
    private val modelBytes: suspend () -> ByteArray,
) : TextLanguageIdentifier {
    private val mutex = Mutex()
    private var detector: FastTextLanguageDetector? = null

    override suspend fun detect(text: String): QuataLanguageDetection =
        detector().detect(text)

    private suspend fun detector(): FastTextLanguageDetector =
        detector ?: mutex.withLock {
            detector ?: FastTextLanguageDetector.fromByteArray(modelBytes())
                .also { detector = it }
        }
}
