package com.quata.core.captions.transcriber

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.quata.R
import com.quata.core.captions.core.CaptionDocument
import com.quata.core.captions.core.WordTiming
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.Model
import org.vosk.Recognizer

class VoskVideoTranscriber(
    private val context: Context,
    private val locale: Locale = Locale.getDefault()
) {
    suspend fun transcribe(videoUri: Uri): CaptionDocument = withContext(Dispatchers.IO) {
        val modelDir = VoskModelResolver(context, locale).resolve()
            ?: throw CaptionTranscriptionException(context.getString(R.string.caption_transcription_model_missing))
        val words = mutableListOf<WordTiming>()
        val model = Model(modelDir.absolutePath)
        var recognizer: Recognizer? = null
        try {
            decodeAudio(videoUri) { pcmBytes, sampleRate ->
                if (recognizer == null) {
                    recognizer = Recognizer(model, sampleRate.toFloat()).apply {
                        setWords(true)
                    }
                }
                val activeRecognizer = recognizer ?: return@decodeAudio
                if (activeRecognizer.acceptWaveForm(pcmBytes, pcmBytes.size)) {
                    words += parseVoskWords(activeRecognizer.result)
                }
            }
            recognizer?.let { words += parseVoskWords(it.finalResult) }
        } finally {
            recognizer?.close()
            model.close()
        }
        CaptionDocument.fromWords(words.distinctBy { "${it.text}-${it.startMs}-${it.endMs}" })
    }

    private fun decodeAudio(
        videoUri: Uri,
        onPcm: (ByteArray, Int) -> Unit
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, videoUri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw CaptionTranscriptionException(context.getString(R.string.caption_transcription_audio_missing))
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw CaptionTranscriptionException(context.getString(R.string.caption_transcription_audio_missing))
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            extractor.selectTrack(trackIndex)
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }
            drainDecoder(extractor, codec, sampleRate, channelCount, onPcm)
        } finally {
            codec?.runCatching { stop() }
            codec?.release()
            extractor.release()
        }
    }

    private fun drainDecoder(
        extractor: MediaExtractor,
        codec: MediaCodec,
        sampleRate: Int,
        channelCount: Int,
        onPcm: (ByteArray, Int) -> Unit
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(DecoderTimeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    val sampleSize = inputBuffer?.let { buffer ->
                        buffer.clear()
                        extractor.readSampleData(buffer, 0)
                    } ?: -1
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DecoderTimeoutUs)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0 && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val pcm = outputBuffer.toMonoPcm16(channelCount)
                        if (pcm.isNotEmpty()) onPcm(pcm, sampleRate)
                    }
                    outputDone = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
    }

    private fun ByteBuffer.toMonoPcm16(channelCount: Int): ByteArray {
        val bytes = ByteArray(remaining())
        get(bytes)
        if (channelCount <= 1) return bytes
        val bytesPerFrame = channelCount * 2
        val frameCount = bytes.size / bytesPerFrame
        val output = ByteArray(frameCount * 2)
        var sourceOffset = 0
        var targetOffset = 0
        repeat(frameCount) {
            var sum = 0
            repeat(channelCount) {
                val low = bytes[sourceOffset].toInt() and 0xff
                val high = bytes[sourceOffset + 1].toInt()
                sum += (high shl 8) or low
                sourceOffset += 2
            }
            val mixed = (sum / channelCount).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output[targetOffset] = (mixed and 0xff).toByte()
            output[targetOffset + 1] = ((mixed shr 8) and 0xff).toByte()
            targetOffset += 2
        }
        return output
    }

    private fun parseVoskWords(json: String): List<WordTiming> =
        runCatching {
            val root = Json.parseToJsonElement(json).jsonObject
            root["result"]?.jsonArray?.mapNotNull { element ->
                val item = element.jsonObject
                val word = item["word"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val start = item["start"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val end = item["end"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val confidence = item["conf"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 1f
                WordTiming(
                    text = word,
                    startMs = (start * 1000).toLong(),
                    endMs = (end * 1000).toLong(),
                    confidence = confidence
                )
            }.orEmpty()
        }.getOrDefault(emptyList())
}

open class CaptionTranscriptionException(message: String) : Exception(message)

private class VoskModelResolver(
    private val context: Context,
    private val locale: Locale
) {
    fun resolve(): File? {
        val language = VoskModelLanguage.from(locale)
        val languageCode = language.languageCode
        val bundledTarget = File(context.filesDir, "vosk/$languageCode")
        findModelDirectory(bundledTarget)?.let { return it }

        if (!VoskModelDeliveryManager(context).isInstalled(language)) {
            throw VoskModelNotInstalledException(language)
        }

        unpackBundledModelIfPresent(language, bundledTarget)
        return findModelDirectory(bundledTarget)
            ?: candidateDirectories(languageCode).firstNotNullOfOrNull { findModelDirectory(it) }
    }

    private fun candidateDirectories(language: String): List<File> {
        val filesRoot = File(context.filesDir, "vosk")
        val externalRoot = context.getExternalFilesDir(null)?.let { File(it, "vosk") }
        val modelNames = when (language) {
            "fr" -> listOf("fr", "vosk-model-small-fr-0.22")
            "es" -> listOf("es", "vosk-model-small-es-0.42")
            else -> listOf("en", "vosk-model-small-en-us-0.15")
        }
        return buildList {
            modelNames.forEach { add(File(filesRoot, it)) }
            externalRoot?.let { root -> modelNames.forEach { add(File(root, it)) } }
        }
    }

    private fun unpackBundledModelIfPresent(language: VoskModelLanguage, targetRoot: File) {
        if (findModelDirectory(targetRoot) != null) return
        targetRoot.mkdirs()
        val canonicalTarget = targetRoot.canonicalFile
        val resourceId = context.resources.getIdentifier(language.resourceName, "raw", context.packageName)
        if (resourceId == 0) {
            throw VoskModelNotInstalledException(language)
        }
        ZipInputStream(context.resources.openRawResource(resourceId)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val outputFile = File(canonicalTarget, entry.name).canonicalFile
                if (!outputFile.path.startsWith(canonicalTarget.path + File.separator)) {
                    throw CaptionTranscriptionException(context.getString(R.string.caption_transcription_model_missing))
                }
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    outputFile.outputStream().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }
    }

    private fun findModelDirectory(root: File): File? {
        if (!root.exists()) return null
        if (root.isDirectory && File(root, "conf").exists()) return root
        root.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                findModelDirectory(child)?.let { return it }
            }
        }
        return null
    }
}

private const val DecoderTimeoutUs = 10_000L
